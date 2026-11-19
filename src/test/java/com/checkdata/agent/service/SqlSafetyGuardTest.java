package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlSafetyGuardTest {

    private SqlSafetyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SqlSafetyGuard(200, 3);
    }

    @Nested
    @DisplayName("Should block - write ops & multi-stmt")
    class ShouldBlockBasic {
        private void assertBlocked(String sql) {
            ValidationResult r = guard.validate(sql);
            assertFalse(r.valid(), "Should block: " + sql + " | " + r.reason());
        }
        @Test void t01() { assertBlocked("INSERT INTO payment (amount) VALUES (100)"); }
        @Test void t02() { assertBlocked("UPDATE payment SET amount = 100 WHERE id = 1"); }
        @Test void t03() { assertBlocked("DELETE FROM payment WHERE id = 1"); }
        @Test void t04() { assertBlocked("DROP TABLE payment"); }
        @Test void t05() { assertBlocked("ALTER TABLE payment ADD COLUMN x INT"); }
        @Test void t06() { assertBlocked("TRUNCATE TABLE payment"); }
        @Test void t07() { assertBlocked("CALL my_proc()"); }
        @Test void t08() { assertBlocked("SELECT 1; DELETE FROM payment"); }
        @Test void t09() { assertBlocked("SELECT * FROM payment; DROP TABLE payment"); }
    }

    @Nested
    @DisplayName("Should block - dangerous functions")
    class ShouldBlockDangerous {
        private void assertBlocked(String sql) {
            ValidationResult r = guard.validate(sql);
            assertFalse(r.valid(), "Should block: " + sql + " | " + r.reason());
        }
        @Test void t10() { assertBlocked("SELECT sleep(5) FROM payment"); }
        @Test void t11() { assertBlocked("SELECT benchmark(1000000,md5('x'))"); }
        @Test void t12() { assertBlocked("SELECT load_file('/etc/passwd')"); }
        @Test void t13() { assertBlocked("SELECT get_lock('test', 10)"); }
        @Test void t14() { assertBlocked("SELECT sys_exec('whoami')"); }
        @Test void t15() { assertBlocked("SELECT sleep(5)"); }
        @Test void t16() { assertBlocked("SELECT * FROM payment WHERE amount > sleep(1)"); }
    }

    @Nested
    @DisplayName("Should block -形态检查")
    class ShouldBlockShape {
        private void assertBlocked(String sql) {
            ValidationResult r = guard.validate(sql);
            assertFalse(r.valid(), "Should block: " + sql + " | " + r.reason());
        }
        @Test void t17() { assertBlocked("SELECT * FROM payment INTO OUTFILE '/tmp/x'"); }
        @Test void t18() { assertBlocked("SELECT * FROM payment FOR UPDATE"); }
        @Test void t19() { assertBlocked("SELECT * FROM payment FOR SHARE"); }
        @Test void t20() { assertBlocked("SELECT * FROM payment CROSS JOIN customer"); }
        @Test void t21() { assertBlocked("SELECT * FROM payment JOIN customer"); }
        @Test void t22() { assertBlocked("SELECT * FROM a JOIN b ON a.id=b.id JOIN c ON b.id=c.id JOIN d ON c.id=d.id JOIN e ON d.id=e.id"); }
        @Test void t23() { assertBlocked("SELECT * FROM payment LIMIT 10 OFFSET 20"); }
        @Test void t24() { assertBlocked("SELECT * FROM (SELECT * FROM payment FOR UPDATE) t"); }
    }

    @Nested
    @DisplayName("Should block - 边界")
    class ShouldBlockEdge {
        private void assertBlocked(String sql) {
            ValidationResult r = guard.validate(sql);
            assertFalse(r.valid(), "Should block: " + sql + " | " + r.reason());
        }
        @Test void t25() { assertBlocked(""); }
        @Test void t26() { assertBlocked(";;;"); }
        @Test void t27() { assertBlocked(null); }
    }

    @Nested
    @DisplayName("Should pass - 聚合")
    class ShouldPassAgg {
        private void assertPassed(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid(), "Should pass: " + sql + " | " + r.reason());
        }
        @Test void p01() { assertPassed("SELECT customer_id, COUNT(*) FROM payment GROUP BY customer_id"); }
        @Test void p02() { assertPassed("SELECT customer_id, SUM(amount) FROM payment GROUP BY customer_id HAVING SUM(amount) > 100"); }
        @Test void p03() { assertPassed("SELECT SUM(amount) FROM payment"); }
        @Test void p04() { assertPassed("SELECT COUNT(*) FROM payment"); }
    }

    @Nested
    @DisplayName("Should pass - 分页")
    class ShouldPassPaging {
        private void assertPassed(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid(), "Should pass: " + sql + " | " + r.reason());
        }
        private void assertCapped(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid());
            assertTrue(r.safeSql().contains("LIMIT 200"), "Should cap to 200: " + r.safeSql());
        }
        @Test void p05() { assertPassed("SELECT * FROM payment ORDER BY id LIMIT 50"); }
        @Test void p06() { assertCapped("SELECT * FROM payment ORDER BY id LIMIT 999999"); }
        @Test void p07() { assertPassed("SELECT * FROM payment ORDER BY id LIMIT 50 OFFSET 100"); }
    }

    @Nested
    @DisplayName("Should pass - JOIN")
    class ShouldPassJoin {
        private void assertPassed(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid(), "Should pass: " + sql + " | " + r.reason());
        }
        @Test void p08() { assertPassed("SELECT p.*, c.name FROM payment p JOIN customer c ON p.customer_id = c.id"); }
        @Test void p09() { assertPassed("SELECT p.*, c.name, s.name FROM payment p JOIN customer c ON p.customer_id = c.id JOIN staff s ON p.staff_id = s.id"); }
        @Test void p10() { assertPassed("SELECT c.*, p.amount FROM customer c LEFT JOIN payment p ON c.id = p.customer_id"); }
    }

    @Nested
    @DisplayName("Should pass - CTE/UNION/子查询")
    class ShouldPassCteUnionSub {
        private void assertPassed(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid(), "Should pass: " + sql + " | " + r.reason());
        }
        @Test void p11() { assertPassed("WITH top AS (SELECT customer_id, SUM(amount) total FROM payment GROUP BY customer_id) SELECT * FROM top"); }
        @Test void p12() { assertPassed("WITH RECURSIVE cte AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM cte WHERE n < 10) SELECT * FROM cte"); }
        @Test void p13() { assertPassed("SELECT id FROM payment WHERE id < 100 UNION SELECT id FROM payment WHERE id > 500 ORDER BY id LIMIT 50"); }
        @Test void p14() { assertPassed("SELECT customer_id FROM payment UNION ALL SELECT customer_id FROM rental"); }
        @Test void p15() { assertPassed("SELECT * FROM payment WHERE customer_id IN (SELECT id FROM customer WHERE active = 1)"); }
        @Test void p16() { assertPassed("SELECT * FROM customer WHERE EXISTS (SELECT 1 FROM payment WHERE payment.customer_id = customer.id)"); }
        @Test void p17() { assertPassed("SELECT * FROM (SELECT customer_id, SUM(amount) s FROM payment GROUP BY customer_id) t WHERE t.s > 100"); }
    }

    @Nested
    @DisplayName("Should pass - 其他")
    class ShouldPassMisc {
        private void assertPassed(String sql) {
            ValidationResult r = guard.validate(sql);
            assertTrue(r.valid(), "Should pass: " + sql + " | " + r.reason());
        }
        @Test void p18() { assertPassed("SELECT * FROM payment WHERE amount > 5.0"); }
        @Test void p19() { assertPassed("SELECT * FROM payment WHERE amount > 5.0 ORDER BY id LIMIT 10"); }
        @Test void p20() { assertPassed("SELECT * FROM payment WHERE amount > 5.0 LIMIT 500"); }
        @Test void p21() { assertPassed("SELECT ABS(-1), SQRT(16), NOW() FROM payment"); }
        @Test void p22() { assertPassed("SELECT c.name, p.amount FROM customer c LEFT JOIN payment p ON c.id = p.customer_id AND p.amount > 10"); }
        @Test void p23() { assertPassed("SELECT 1 UNION SELECT 2 UNION SELECT 3 ORDER BY 1 LIMIT 10"); }
        @Test void p24() { assertPassed("SELECT customer_id, staff_id, COUNT(*) FROM payment GROUP BY customer_id, staff_id"); }
        @Test void p25() { assertPassed("SELECT c.*, (SELECT COUNT(*) FROM payment p WHERE p.customer_id = c.id) FROM customer c"); }
        @Test void p26() { assertPassed("WITH active AS (SELECT * FROM customer WHERE active = 1) SELECT a.*, p.amount FROM active a JOIN payment p ON a.id = p.customer_id"); }
        @Test void p27() { assertPassed("SELECT * FROM payment WHERE customer_id IN (SELECT id FROM customer WHERE store_id = 1) AND amount > 5"); }
        @Test void p28() { assertPassed("SELECT MIN(amount), MAX(amount), AVG(amount) FROM payment"); }
        @Test void p29() { assertPassed("SELECT * FROM `payment` WHERE `amount` > 1"); }
        @Test void p30() { assertPassed("SELECT * FROM payment ORDER BY id LIMIT 10;"); }
    }

    @Test
    @DisplayName("实验一指标汇总")
    void runMetrics() {
        String[] shouldBlock = {
                "INSERT INTO payment (amount) VALUES (100)",
                "UPDATE payment SET amount = 100 WHERE id = 1",
                "DELETE FROM payment WHERE id = 1",
                "DROP TABLE payment",
                "ALTER TABLE payment ADD COLUMN x INT",
                "TRUNCATE TABLE payment",
                "CALL my_proc()",
                "SELECT 1; DELETE FROM payment",
                "SELECT * FROM payment; DROP TABLE payment",
                "SELECT sleep(5) FROM payment",
                "SELECT benchmark(1000000,md5('x'))",
                "SELECT load_file('/etc/passwd')",
                "SELECT get_lock('test', 10)",
                "SELECT sys_exec('whoami')",
                "SELECT sleep(5)",
                "SELECT * FROM payment WHERE amount > sleep(1)",
                "SELECT * FROM payment INTO OUTFILE '/tmp/x'",
                "SELECT * FROM payment FOR UPDATE",
                "SELECT * FROM payment FOR SHARE",
                "SELECT * FROM payment CROSS JOIN customer",
                "SELECT * FROM payment JOIN customer",
                "SELECT * FROM a JOIN b ON a.id=b.id JOIN c ON b.id=c.id JOIN d ON c.id=d.id JOIN e ON d.id=e.id",
                "SELECT * FROM payment LIMIT 10 OFFSET 20",
                "SELECT * FROM (SELECT * FROM payment FOR UPDATE) t",
                "",
                ";;;",
                null
        };

        String[] shouldPass = {
                "SELECT customer_id, COUNT(*) FROM payment GROUP BY customer_id",
                "SELECT customer_id, SUM(amount) FROM payment GROUP BY customer_id HAVING SUM(amount) > 100",
                "SELECT SUM(amount) FROM payment",
                "SELECT COUNT(*) FROM payment",
                "SELECT * FROM payment ORDER BY id LIMIT 50",
                "SELECT * FROM payment ORDER BY id LIMIT 999999",
                "SELECT * FROM payment ORDER BY id LIMIT 50 OFFSET 100",
                "SELECT p.*, c.name FROM payment p JOIN customer c ON p.customer_id = c.id",
                "SELECT p.*, c.name, s.name FROM payment p JOIN customer c ON p.customer_id = c.id JOIN staff s ON p.staff_id = s.id",
                "SELECT c.*, p.amount FROM customer c LEFT JOIN payment p ON c.id = p.customer_id",
                "WITH top AS (SELECT customer_id, SUM(amount) total FROM payment GROUP BY customer_id) SELECT * FROM top",
                "WITH RECURSIVE cte AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM cte WHERE n < 10) SELECT * FROM cte",
                "SELECT id FROM payment WHERE id < 100 UNION SELECT id FROM payment WHERE id > 500 ORDER BY id LIMIT 50",
                "SELECT customer_id FROM payment UNION ALL SELECT customer_id FROM rental",
                "SELECT * FROM payment WHERE customer_id IN (SELECT id FROM customer WHERE active = 1)",
                "SELECT * FROM customer WHERE EXISTS (SELECT 1 FROM payment WHERE payment.customer_id = customer.id)",
                "SELECT * FROM (SELECT customer_id, SUM(amount) s FROM payment GROUP BY customer_id) t WHERE t.s > 100",
                "SELECT * FROM payment WHERE amount > 5.0",
                "SELECT * FROM payment WHERE amount > 5.0 ORDER BY id LIMIT 10",
                "SELECT * FROM payment WHERE amount > 5.0 LIMIT 500",
                "SELECT ABS(-1), SQRT(16), NOW() FROM payment",
                "SELECT c.name, p.amount FROM customer c LEFT JOIN payment p ON c.id = p.customer_id AND p.amount > 10",
                "SELECT 1 UNION SELECT 2 UNION SELECT 3 ORDER BY 1 LIMIT 10",
                "SELECT customer_id, staff_id, COUNT(*) FROM payment GROUP BY customer_id, staff_id",
                "SELECT c.*, (SELECT COUNT(*) FROM payment p WHERE p.customer_id = c.id) FROM customer c",
                "WITH active AS (SELECT * FROM customer WHERE active = 1) SELECT a.*, p.amount FROM active a JOIN payment p ON a.id = p.customer_id",
                "SELECT * FROM payment WHERE customer_id IN (SELECT id FROM customer WHERE store_id = 1) AND amount > 5",
                "SELECT MIN(amount), MAX(amount), AVG(amount) FROM payment",
                "SELECT * FROM `payment` WHERE `amount` > 1",
                "SELECT * FROM payment ORDER BY id LIMIT 10;"
        };

        int blockedPassed = 0, falsePass = 0, falseBlock = 0, injectedCount = 0, cappedCount = 0;

        System.out.println("========== EXPERIMENT 1: SqlSafetyGuard ==========");
        System.out.println("Guard params: maxRows=200, maxJoins=3");
        System.out.println();

        System.out.println("--- Should block (" + shouldBlock.length + ") ---");
        for (int i = 0; i < shouldBlock.length; i++) {
            ValidationResult r = guard.validate(shouldBlock[i]);
            if (!r.valid()) blockedPassed++; else falsePass++;
            System.out.printf("  [%2d] %s -> %s | %s%n", i + 1, r.valid() ? "FAIL" : "OK", truncate(shouldBlock[i], 55), r.reason());
        }

        System.out.println();
        System.out.println("--- Should pass (" + shouldPass.length + ") ---");
        for (int i = 0; i < shouldPass.length; i++) {
            ValidationResult r = guard.validate(shouldPass[i]);
            if (r.valid()) {
                if (r.safeSql().toUpperCase().contains("LIMIT")) injectedCount++;
                if (r.safeSql().contains("LIMIT 200")) cappedCount++;
            } else {
                falseBlock++;
            }
            System.out.printf("  [%2d] %s -> %s | %s%n", i + 1, r.valid() ? "OK" : "FAIL", truncate(shouldPass[i], 55), r.reason());
        }

        System.out.println();
        System.out.println("================== METRICS ==================");
        System.out.printf("Should block: %d -> blocked %d (%.1f%%), leaked %d (%.1f%%)%n",
                shouldBlock.length, blockedPassed, 100.0*blockedPassed/shouldBlock.length,
                falsePass, 100.0*falsePass/shouldBlock.length);
        System.out.printf("Should pass:  %d -> passed  %d (%.1f%%), blocked %d (%.1f%%)%n",
                shouldPass.length, shouldPass.length-falseBlock, 100.0*(shouldPass.length-falseBlock)/shouldPass.length,
                falseBlock, 100.0*falseBlock/shouldPass.length);
        System.out.printf("LIMIT injected %d/%d (%.1f%%), capped to 200: %d%n",
                injectedCount, shouldPass.length, 100.0*injectedCount/shouldPass.length, cappedCount);

        assertEquals(shouldBlock.length, blockedPassed, "Block rate should be 100%");
        assertEquals(0, falseBlock, "False block rate should be 0%");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        String o = s.replace("\n", " ");
        return o.length() <= max ? o : o.substring(0, max) + "...";
    }
}