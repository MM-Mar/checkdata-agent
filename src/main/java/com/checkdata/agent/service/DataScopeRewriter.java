package com.checkdata.agent.service;

import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.service.permission.PermissionRule;
import com.checkdata.agent.service.permission.PermissionRuleRegistry;
import com.checkdata.agent.service.permission.SqlCondSupport;
import com.checkdata.agent.common.datascope.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Component;


/**
 * 数据权限 SQL 改写器。
 * <p>
 * 遍历 AST 找真实 Table，按表名查规则注入权限条件。
 * FROM 表和普通 JOIN 右表注入 WHERE；LEFT JOIN 右表注入 JOIN ON，避免破坏 LEFT JOIN 语义。
 * 支持 CTE/UNION/JOIN/子查询嵌套。改写失败抛 RewriteException。
 */
@Component
public class DataScopeRewriter {

    private final PermissionRuleRegistry ruleRegistry;

    public DataScopeRewriter(PermissionRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * 改写 SQL：注入数据权限条件。ALL 或 ctx 为 null 时原样返回。
     */
    public String rewrite(String sql, DataScopeContext ctx) {
        if (ctx == null || ctx.scope() == DataScope.ALL) {
            return sql;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (!(stmt instanceof Select)) {
                return sql;
            }
            Select select = (Select) stmt;
            if (select.getWithItemsList() != null) {
                for (WithItem<?> item : select.getWithItemsList()) {
                    if (item.getSelect() != null) {
                        walk(item.getSelect(), ctx);
                    }
                }
            }
            walk(select, ctx);
            return stmt.toString();
        } catch (RewriteException e) {
            throw e;
        } catch (Exception e) {
            throw new RewriteException("数据权限改写失败：" + rootMessage(e), e);
        }
    }

    /**
     * 递归 Select 节点：PlainSelect 注入条件，UNION/Paren 节点向下递归。
     */
    private void walk(Select select, DataScopeContext ctx) {
        if (select instanceof PlainSelect ps) {
            injectConditions(ps, ctx);
            if (ps.getFromItem() != null) {
                walkFromItem(ps.getFromItem(), ctx);
            }
            if (ps.getJoins() != null) {
                for (Join j : ps.getJoins()) {
                    walkFromItem(j.getRightItem(), ctx);
                }
            }
            if (ps.getWhere() != null) {
                rewriteSubSelectsInWhere(ps.getWhere(), ctx);
            }
        } else if (select instanceof SetOperationList sol) {
            for (Select branch : sol.getSelects()) {
                walk(branch, ctx);
            }
        } else if (select instanceof ParenthesedSelect ps && ps.getSelect() != null) {
            walk(ps.getSelect(), ctx);
        }
    }

    /**
     * 递归 FromItem：子查询和括号 Join 向下走，普通 Table 由 injectConditions 处理。
     */
    private void walkFromItem(FromItem item, DataScopeContext ctx) {
        if (item instanceof ParenthesedSelect ps && ps.getSelect() != null) {
            walk(ps.getSelect(), ctx);
        } else if (item instanceof ParenthesedFromItem pf) {
            if (pf.getFromItem() != null) {
                walkFromItem(pf.getFromItem(), ctx);
            }
            if (pf.getJoins() != null) {
                for (Join j : pf.getJoins()) {
                    walkFromItem(j.getRightItem(), ctx);
                }
            }
        }
    }

    /**
     * 对 PlainSelect 的 FROM/JOIN 真实表按规则注入条件。
     * <p>
     * FROM 表和普通 JOIN 右表的权限条件注入 WHERE；LEFT JOIN 右表的权限条件注入 ON，
     * 避免把 LEFT JOIN 变成类似 INNER JOIN 的过滤效果。
     */
    private void injectConditions(PlainSelect ps, DataScopeContext ctx) {
        Expression newWhere = ps.getWhere();
        if (ps.getFromItem() instanceof Table t) {
            newWhere = appendCondition(newWhere, buildCondition(t, ctx));
        }

        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                if (!(j.getRightItem() instanceof Table t)) {
                    continue;
                }
                Expression cond = buildCondition(t, ctx);
                if (cond == null) {
                    continue;
                }
                if (j.isLeft()) {
                    if (j.getOnExpression() == null) {
                        throw new RewriteException("LEFT JOIN 受权限控制表缺少 ON 条件。请补充明确 JOIN ON，或先过滤右表子查询后再 LEFT JOIN", null);
                    }
                    j.setOnExpression(new AndExpression(j.getOnExpression(), cond));
                } else {
                    newWhere = appendCondition(newWhere, cond);
                }
            }
        }
        ps.setWhere(newWhere);
    }

    private Expression buildCondition(Table table, DataScopeContext ctx) {
        PermissionRule rule = ruleRegistry.get(SqlCondSupport.pureName(table));
        return rule.buildCondition(table, ctx);
    }

    private Expression appendCondition(Expression base, Expression cond) {
        if (cond == null) {
            return base;
        }
        return base == null ? cond : new AndExpression(base, cond);
    }

    /**
     * 递归 WHERE 表达式中嵌套的子查询（IN (SELECT...) / EXISTS 等）。
     */
    private void rewriteSubSelectsInWhere(Expression where, final DataScopeContext ctx) {
        where.accept(new ExpressionVisitorAdapter<Object>() {
            @Override
            public <S> Object visit(ParenthesedSelect select, S context) {
                if (select.getSelect() != null) {
                    walk(select.getSelect(), ctx);
                }
                return super.visit(select, context);
            }

            @Override
            public <S> Object visit(ExistsExpression expr, S context) {
                Expression right = expr.getRightExpression();
                if (right instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                    walk(ps.getSelect(), ctx);
                }
                return super.visit(expr, context);
            }
        });
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        Throwable deepest = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            deepest = cur.getCause();
            cur = cur.getCause();
        }
        String msg = deepest.getMessage();
        return msg == null ? deepest.getClass().getSimpleName() : msg;
    }

    /**
     * 数据权限改写异常。
     */
    public static class RewriteException extends RuntimeException {
        public RewriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}