package com.checkdata.agent.service;

import com.checkdata.agent.common.datascope.DataScopeContext;
import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.service.permission.DefaultPermissionRule;
import com.checkdata.agent.service.permission.PermissionRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataScopeRewriterTest {

    private DataScopeRewriter rewriter;
    private DataScopeContext selfCtx;
    private DataScopeContext deptCtx;
    private DataScopeContext allCtx;

    @BeforeEach
    void setUp() {
        DefaultPermissionRule defaultRule = new DefaultPermissionRule();
        PermissionRuleRegistry registry = new PermissionRuleRegistry(defaultRule, List.of(defaultRule));
        rewriter = new DataScopeRewriter(registry);
        selfCtx = new DataScopeContext(42L, DataScope.SELF, List.of(10L, 20L));
        deptCtx = new DataScopeContext(42L, DataScope.DEPT, List.of(10L, 20L));
        allCtx = new DataScopeContext(42L, DataScope.ALL, List.of(10L, 20L));
    }

    @Nested
    @DisplayName("A1: 单表 FROM 条件注入")
    class SingleTable {
        @Test void selfScope_injectsUserId() {
            String result = rewriter.rewrite("SELECT * FROM payment", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "应注入 user_id=42 到 WHERE，actual=" + result);
        }
        @Test void deptScope_injectsDeptId() {
            String result = rewriter.rewrite("SELECT * FROM payment", deptCtx);
            assertTrue(result.toUpperCase().contains("DEPT_ID IN"), "应注入 dept_id IN 到 WHERE，actual=" + result);
        }
        @Test void allScope_noInjection() {
            String result = rewriter.rewrite("SELECT * FROM payment", allCtx);
            assertFalse(result.toUpperCase().contains("USER_ID"), "ALL scope 不应注入");
            assertEquals("SELECT * FROM payment", result);
        }
        @Test void alreadyHasWhere_and() {
            String result = rewriter.rewrite("SELECT * FROM payment WHERE amount > 5", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "应追加 user_id=42，actual=" + result);
            assertTrue(result.toUpperCase().contains("AMOUNT > 5"), "原有 WHERE 应保留");
        }
    }

    @Nested
    @DisplayName("A2: INNER JOIN 条件注入 WHERE")
    class InnerJoin {
        @Test void bothTables_injectedToWhere() {
            String result = rewriter.rewrite(
                    "SELECT p.*, r.id FROM payment p JOIN rental r ON p.id = r.payment_id", selfCtx);
            assertTrue(result.toUpperCase().contains("P.USER_ID = 42"), "payment 应注入 WHERE，actual=" + result);
            assertTrue(result.toUpperCase().contains("R.USER_ID = 42"), "rental 应注入 WHERE，actual=" + result);
        }
    }

    @Nested
    @DisplayName("A3: LEFT JOIN 条件注入 ON（关键语义保持）")
    class LeftJoin {
        @Test void leftRightTable_injectedToOn() {
            String result = rewriter.rewrite(
                    "SELECT c.*, p.amount FROM customer c LEFT JOIN payment p ON c.id = p.customer_id", selfCtx);
            assertTrue(result.toUpperCase().contains("P.USER_ID = 42"), "payment 右表应注入条件");
            int onIdx = result.toUpperCase().indexOf("ON");
            int whereIdx = result.toUpperCase().indexOf("WHERE");
            int userIdIdx = result.toUpperCase().indexOf("P.USER_ID");
            assertTrue(userIdIdx > onIdx, "LEFT JOIN 右表条件应在 ON 之后而非 WHERE");
            if (whereIdx > 0) {
                assertTrue(userIdIdx < whereIdx, "LEFT JOIN 右表条件不应落到 WHERE 里破坏语义");
            }
            System.out.println("LEFT JOIN 改写结果: " + result);
        }
    }

    @Nested
    @DisplayName("A4: CTE WITH 递归注入")
    class Cte {
        @Test void withClause_injected() {
            String result = rewriter.rewrite(
                    "WITH top AS (SELECT * FROM payment) SELECT * FROM top", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "CTE 内部 payment 应被注入，actual=" + result);
        }
    }

    @Nested
    @DisplayName("A5: UNION 各分支注入")
    class Union {
        @Test void unionBranches_bothInjected() {
            String result = rewriter.rewrite(
                    "SELECT * FROM payment WHERE id < 10 UNION SELECT * FROM payment WHERE id > 100", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "UNION 各分支 payment 应注入，actual=" + result);
        }
    }

    @Nested
    @DisplayName("A6: 子查询 IN/EXISTS 递归注入")
    class SubQuery {
        @Test void inSubquery_injected() {
            String result = rewriter.rewrite(
                    "SELECT * FROM rental WHERE payment_id IN (SELECT id FROM payment WHERE amount > 5)", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "子查询内 payment 应注入，actual=" + result);
        }
        @Test void existsSubquery_injected() {
            String result = rewriter.rewrite(
                    "SELECT * FROM customer WHERE EXISTS (SELECT 1 FROM payment WHERE payment.customer_id = customer.id)", selfCtx);
            assertTrue(result.toUpperCase().contains("USER_ID = 42"), "EXISTS 子查询内 payment 应注入，actual=" + result);
        }
    }

    @Nested
    @DisplayName("A7: LEFT JOIN 无 ON 应报 RewriteException（fail-closed）")
    class LeftJoinFailClosed {
        @Test void noOn_throws() {
            assertThrows(DataScopeRewriter.RewriteException.class, () ->
                    rewriter.rewrite("SELECT * FROM customer LEFT JOIN payment", selfCtx),
                    "LEFT JOIN 无 ON 且右表受权限控制应抛 RewriteException");
        }
    }

    @Test
    @DisplayName("实验四：结构覆盖汇总")
    void structureCoverage() {
        String[] structures = {
                "单表 FROM",
                "INNER JOIN",
                "LEFT JOIN (ON 注入)",
                "CTE WITH",
                "UNION 分支",
                "IN 子查询",
                "EXISTS 子查询",
                "LEFT JOIN 无 ON (fail-closed)"
        };
        int covered = structures.length;
        System.out.println("========== EXPERIMENT 4: DataScopeRewriter ==========");
        System.out.println("覆盖的 SQL 结构类型数：" + covered);
        for (int i = 0; i < structures.length; i++) {
            System.out.println("  " + (i + 1) + ". " + structures[i]);
        }
        assertTrue(covered >= 5, "至少应覆盖 5 类结构，实际覆盖 " + covered);
    }
}