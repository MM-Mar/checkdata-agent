package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.Mschema;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaStatsTest {

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

    @Test
    void introspectAndCount() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/checkdata_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
        Class.forName("com.mysql.cj.jdbc.Driver");
        DataSource ds = new SimpleDS(url, "root", "123456");

        long t0 = System.currentTimeMillis();
        MschemaIntrospector introspector = new MschemaIntrospector(ds, new SensitiveFilter(false, List.of()));
        Mschema schema = introspector.readSchema("checkdata_agent", null, List.of());
        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Mschema.TableDef> tables = schema.tables();
        List<Mschema.FkDef> fks = schema.foreignKeys();

        int totalCols = 0, stringCols = 0, stringColsWithExamples = 0;
        for (var td : tables.values()) {
            for (var fe : td.fields().values()) {
                totalCols++;
                String type = fe.type();
                boolean isString = type != null && type.toUpperCase().matches(".*(CHAR|TEXT|ENUM|SET).*");
                if (isString) {
                    stringCols++;
                    if (fe.examples() != null && !fe.examples().isEmpty()) stringColsWithExamples++;
                }
            }
        }
        double exCov = stringCols == 0 ? 0 : 100.0 * stringColsWithExamples / stringCols;

        int viewCount = 0;
        try (Connection conn = ds.getConnection();
             ResultSet rs = conn.getMetaData().getTables("checkdata_agent", null, "%", new String[]{"VIEW"})) {
            while (rs.next()) viewCount++;
        }

        System.out.println("========== EXPERIMENT 3: Schema Introspector ==========");
        System.out.printf("Database: checkdata_agent%n");
        System.out.printf("Tables: %d%n", tables.size());
        System.out.printf("Views: %d%n", viewCount);
        System.out.printf("FKs: %d%n", fks.size());
        System.out.printf("Total columns: %d%n", totalCols);
        System.out.printf("String columns: %d%n", stringCols);
        System.out.printf("String cols with examples: %d%n", stringColsWithExamples);
        System.out.printf("Example coverage: %.1f%%%n", exCov);
        System.out.printf("Elapsed: %d ms%n", elapsed);
        System.out.println();

        System.out.println("--- Tables ---");
        for (String tname : tables.keySet()) {
            int fc = tables.get(tname).fields().size();
            System.out.printf("  %-30s %d cols%n", tname, fc);
        }
        System.out.println();

        System.out.println("--- FKs ---");
        for (Mschema.FkDef fk : fks) {
            System.out.printf("  %s.%s -> %s.%s%n", fk.table(), fk.column(), fk.refTable(), fk.refColumn());
        }

        assertTrue(tables.size() > 0, "Should find tables");
    }
}