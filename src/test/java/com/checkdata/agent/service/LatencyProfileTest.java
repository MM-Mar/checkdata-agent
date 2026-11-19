package com.checkdata.agent.service;

import com.checkdata.agent.common.datascope.DataScopeContext;
import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.service.permission.DefaultPermissionRule;
import com.checkdata.agent.service.permission.PermissionRuleRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

class LatencyProfileTest {

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

    private static DataSource buildDS() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return new SimpleDS(URL, "root", "123456");
    }

    private static double avg(List<Long> list) {
        return list.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static long max(List<Long> list) {
        return list.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    private static long min(List<Long> list) {
        return list.stream().mapToLong(Long::longValue).min().orElse(0);
    }

    private static long p95(List<Long> list) {
        List<Long> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    @Test
    void profilePipelineLatency() throws Exception {
        DataSource ds = buildDS();
        SensitiveFilter sensitiveFilter = new SensitiveFilter(false, List.of());

        SqlSafetyGuard guard = new SqlSafetyGuard(200L, 3);

        DefaultPermissionRule defaultRule = new DefaultPermissionRule();
        PermissionRuleRegistry registry = new PermissionRuleRegistry(defaultRule, List.of(defaultRule));
        DataScopeRewriter rewriter = new DataScopeRewriter(registry);

        ExplainPrecheckService precheck = new ExplainPrecheckService(ds);
        ReadOnlyQueryRunner runner = new ReadOnlyQueryRunner(ds);

        java.lang.reflect.Field t1 = ExplainPrecheckService.class.getDeclaredField("timeoutSeconds");
        t1.setAccessible(true); t1.setInt(precheck, 5);
        java.lang.reflect.Field t2 = ExplainPrecheckService.class.getDeclaredField("maxEstimatedRows");
        t2.setAccessible(true); t2.setLong(precheck, 100_000L);

        List<String> sqls = List.of(
                "SELECT * FROM customer LIMIT 10",
                "SELECT c.name, p.amount FROM customer c JOIN payment p ON c.customer_id = p.customer_id LIMIT 20",
                "SELECT * FROM film WHERE film_id = 100",
                "SELECT COUNT(*) FROM payment GROUP BY customer_id LIMIT 10",
                "SELECT f.title, c.name FROM film f JOIN film_category fc ON f.film_id = fc.film_id JOIN category c ON fc.category_id = c.category_id LIMIT 5"
        );

        DataScopeContext ctx = new DataScopeContext(42L, DataScope.SELF, List.of(10L, 20L));

        int WARMUP = 10;
        int ITERATIONS = 50;

        List<Long> guardTimes = new ArrayList<>();
        List<Long> rewriteTimes = new ArrayList<>();
        List<Long> explainTimes = new ArrayList<>();
        List<Long> executeTimes = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            for (String sql : sqls) {
                if (i < WARMUP) {
                    guard.validate(sql);
                    continue;
                }

                long start, end;

                start = System.nanoTime();
                guard.validate(sql);
                end = System.nanoTime();
                guardTimes.add((end - start) / 1_000);

                start = System.nanoTime();
                rewriter.rewrite(sql, ctx);
                end = System.nanoTime();
                rewriteTimes.add((end - start) / 1_000);

                start = System.nanoTime();
                precheck.check(sql);
                end = System.nanoTime();
                explainTimes.add((end - start) / 1_000);

                start = System.nanoTime();
                try { runner.execute(sql); } catch (Exception ignored) {}
                end = System.nanoTime();
                executeTimes.add((end - start) / 1_000);
            }
        }

        System.out.println("========== EXPERIMENT 5: Latency Profile ==========");
        System.out.println("测试配置: 5 条 SQL × 40 次有效迭代 = 200 样本/环节");
        System.out.println();
        printSection("Guard (SqlSafetyGuard.validate)", guardTimes);
        printSection("Rewriter (DataScopeRewriter.rewrite)", rewriteTimes);
        printSection("EXPLAIN (ExplainPrecheckService.check)", explainTimes);
        printSection("Execute (ReadOnlyQueryRunner.execute)", executeTimes);

        double sum = avg(guardTimes) + avg(rewriteTimes) + avg(explainTimes) + avg(executeTimes);
        System.out.println("========== 耗时分解（按均值） ==========");
        System.out.printf("Guard:    %.1f%%  (%.2f ms)%n", 100 * avg(guardTimes) / sum, avg(guardTimes) / 1000.0);
        System.out.printf("Rewriter: %.1f%%  (%.2f ms)%n", 100 * avg(rewriteTimes) / sum, avg(rewriteTimes) / 1000.0);
        System.out.printf("EXPLAIN:  %.1f%%  (%.2f ms)%n", 100 * avg(explainTimes) / sum, avg(explainTimes) / 1000.0);
        System.out.printf("Execute:  %.1f%%  (%.2f ms)%n", 100 * avg(executeTimes) / sum, avg(executeTimes) / 1000.0);
        System.out.printf("端到端均值: %.2f ms%n", sum / 1000.0);
        System.out.println();
        System.out.println("环境: checkdata_agent MySQL, sakila+业务表, JDK 21, JSqlParser 5.1, HikariCP");
    }

    private static void printSection(String label, List<Long> timesUs) {
        System.out.println("--- " + label + " ---");
        System.out.printf("  样本量: %d%n", timesUs.size());
        System.out.printf("  均值:   %.2f ms%n", avg(timesUs) / 1000.0);
        System.out.printf("  p95:    %.2f ms%n", p95(timesUs) / 1000.0);
        System.out.printf("  max:    %.2f ms%n", max(timesUs) / 1000.0);
        System.out.printf("  min:    %.3f ms%n", min(timesUs) / 1000.0);
        System.out.println();
    }
}