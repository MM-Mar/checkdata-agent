package com.checkdata.agent.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ExplainPrecheckTest {

    static class SimpleDS implements DataSource {
        private final String url, user, pass;
        SimpleDS(String url, String user, String pass) { this.url = url; this.user = user; this.pass = pass; }
        public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, pass); }
        public Connection getConnection(String u, String p) throws SQLException { return DriverManager.getConnection(url, u, p); }
        public PrintWriter getLogWriter() { return null; }
        public void setLogWriter(PrintWriter out) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() { return 0; }
        public Logger getParentLogger() { return null; }
        public <T> T unwrap(Class<T> iface) { return null; }
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/checkdata_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static DataSource DS;
    private static String BIG_TABLE = "exp2_big_table";
    private static long BIG_TABLE_ROWS = 0;

    @BeforeAll
    static void setup() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        DS = new SimpleDS(URL, "root", "123456");

        try (Connection conn = DS.getConnection(); Statement s = conn.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + BIG_TABLE);
            s.execute("CREATE TABLE " + BIG_TABLE + " (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100), category VARCHAR(50), amount DECIMAL(10,2), created_at DATETIME, INDEX idx_cat (category))");

            s.execute("INSERT INTO " + BIG_TABLE + " (name, category, amount, created_at) " +
                    "SELECT CONCAT('item_', c.customer_id), 'cat_a', 99.99, NOW() FROM customer c LIMIT 500");
            s.execute("INSERT INTO " + BIG_TABLE + " (name, category, amount, created_at) " +
                    "SELECT CONCAT('item_', f.film_id), 'cat_b', 19.99, NOW() FROM film f LIMIT 500");
            s.execute("INSERT INTO " + BIG_TABLE + " (name, category, amount, created_at) " +
                    "SELECT CONCAT('item_', a.actor_id), 'cat_c', 49.99, NOW() FROM actor a LIMIT 500");

            for (int i = 0; i < 12; i++) {
                s.execute("INSERT INTO " + BIG_TABLE + " (name, category, amount, created_at) " +
                        "SELECT CONCAT(name, '_dup', '" + i + "'), category, amount, created_at FROM " + BIG_TABLE);
            }

            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + BIG_TABLE)) {
                rs.next();
                BIG_TABLE_ROWS = rs.getLong(1);
            }
            System.out.println("Big table rows: " + BIG_TABLE_ROWS);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DS.getConnection(); Statement s = conn.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + BIG_TABLE);
        }
    }

    private record Case(String sql, boolean expectBlock, String label) {}

    @Test
    void precheckAccuracyAndLatency() throws Exception {
        ExplainPrecheckService svc = new ExplainPrecheckService(DS);
        java.lang.reflect.Field t1 = ExplainPrecheckService.class.getDeclaredField("timeoutSeconds");
        t1.setAccessible(true); t1.setInt(svc, 5);
        java.lang.reflect.Field t2 = ExplainPrecheckService.class.getDeclaredField("maxEstimatedRows");
        t2.setAccessible(true); t2.setLong(svc, 100_000L);

        List<Case> cases = new ArrayList<>();

        cases.add(new Case("SELECT * FROM " + BIG_TABLE, true, "大表无WHERE全扫"));
        cases.add(new Case("SELECT * FROM " + BIG_TABLE + " WHERE name LIKE '%xyz%'", true, "大表无前缀索引LIKE"));
        cases.add(new Case("SELECT b.*, f.* FROM " + BIG_TABLE + " b, film f", true, "无索引笛卡尔积"));
        cases.add(new Case("SELECT * FROM " + BIG_TABLE + " WHERE amount > 0", true, "大表范围扫描无索引"));
        cases.add(new Case("SELECT * FROM payment WHERE amount > 100000", true, "无索引大查询阈值"));

        cases.add(new Case("SELECT * FROM customer LIMIT 10", false, "LIMIT小查询"));
        cases.add(new Case("SELECT * FROM film WHERE film_id = 1", false, "主键等值查询"));
        cases.add(new Case("SELECT * FROM " + BIG_TABLE + " WHERE category = 'cat_a' LIMIT 10", false, "带索引等值+LIMIT"));
        cases.add(new Case("SELECT COUNT(*) FROM customer GROUP BY store_id", false, "聚合查询"));
        cases.add(new Case("SELECT f.title, c.name FROM film f JOIN film_category fc ON f.film_id = fc.film_id JOIN category c ON fc.category_id = c.category_id LIMIT 5", false, "合理JOIN LIMIT"));

        int correct = 0;
        int total = 0;
        List<Long> latencies = new ArrayList<>();
        List<String> results = new ArrayList<>();

        for (Case c : cases) {
            long t0 = System.nanoTime();
            ExplainPrecheckService.PrecheckResult r = svc.check(c.sql());
            long elapsed = System.nanoTime() - t0;
            latencies.add(elapsed / 1_000_000);

            boolean blocked = !r.allowed();
            boolean hit = blocked == c.expectBlock();
            if (hit) correct++;
            total++;

            results.add(String.format("  [%s] %-28s expect=%s actual=%s reason=%s",
                    hit ? "OK" : "FAIL", c.label(),
                    c.expectBlock() ? "BLOCK" : "ALLOW",
                    blocked ? "BLOCK" : "ALLOW",
                    truncate(r.reason(), 80)));
        }

        double accuracy = total == 0 ? 0 : 100.0 * correct / total;
        double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxMs = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        long minMs = latencies.stream().mapToLong(Long::longValue).min().orElse(0);

        System.out.println("========== EXPERIMENT 2: Explain Precheck ==========");
        System.out.printf("Big table rows: %d%n", BIG_TABLE_ROWS);
        System.out.printf("正常路径拦截准确率: %.1f%% (%d/%d)%n", accuracy, correct, total);
        System.out.printf("EXPLAIN 耗时: avg=%.1fms, min=%dms, max=%dms%n", avgMs, minMs, maxMs);
        System.out.println();
        System.out.println("--- 逐案结果 ---");
        for (String r : results) System.out.println(r);

        assertTrue(correct >= 7, "准确率至少应 >= 70%，fail-open 场景不强制");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}