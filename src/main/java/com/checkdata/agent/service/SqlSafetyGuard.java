package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器。由 validateSql tool 和 executeSql tool 共享，形成双保险。
 * 覆盖：白名单（仅 SELECT/WITH）、防多语句、防危险函数、强制 LIMIT、防 FOR UPDATE / SELECT INTO。
 */
@Slf4j
@Service
public class SqlSafetyGuard {

    /**
     * 危险函数/模式正则（文本预扫，作为 AST 检查的补充）。
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?i)\\bLOAD_FILE\\s*\\("                       // 读文件
                    + "|\\bINTO\\s+(OUTFILE|DUMPFILE)\\b"   // SELECT 写文件
                    + "|\\bSYSTEM_USER\\s*\\("              // 信息泄漏
                    + "|\\bFILE_READ|\\bFILE_WRITE"
    );

    /**
     * AST 遍历要拦截的函数名（小写）。
     */
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
            "sleep", "benchmark", "get_lock", "release_lock",
            "is_free_lock", "is_used_lock", "master_pos_wait", "source_pos_wait",
            "load_file", "sys_exec", "sys_eval", "sys_get"
    );

    private final long maxRows;
    private final int maxJoins;

    public SqlSafetyGuard(@Value("${data-agent.safety.max-rows:200}") long maxRows,
                          @Value("${data-agent.safety.max-joins:3}") int maxJoins) {
        this.maxRows = maxRows;
        this.maxJoins = maxJoins;
    }

    /**
     * 校验 SQL。
     *
     * @param sql 待校验 SQL
     * @return valid=true 时 safeSql 为可执行的安全 SQL（已注入 LIMIT）；valid=false 时 reason 为修改建议
     */
    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return new ValidationResult(false, "SQL 为空", null);
        }
        String trimmed = sql.trim();
        // 去掉末尾分号，避免 parseStatements 误判
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        try {
            return doValidate(trimmed);
        } catch (JSQLParserException pe) {
            // 解析失败一律拒绝，不冒险放行
            String msg = pe.getMessage() == null ? "" : pe.getMessage().replace("\n", " ");
            return new ValidationResult(false, "SQL 语法解析失败：" + truncate(msg, 200), null);
        } catch (Exception e) {
            log.warn("SqlSafetyGuard 意外异常，保守拒绝 sql={} err={}", truncate(trimmed, 120), e.toString());
            return new ValidationResult(false, "SQL 校验异常：" + e.getClass().getSimpleName(), null);
        }
    }

    private ValidationResult doValidate(String sql) throws JSQLParserException {
        Matcher m = DANGEROUS_PATTERN.matcher(sql);
        if (m.find()) {
            return reject("检测到危险模式：" + m.group().trim());
        }

        Statements stmts = CCJSqlParserUtil.parseStatements(sql);
        if (stmts.size() != 1) {
            return reject("仅允许单条语句，检测到 " + stmts.size() + " 条（禁止用 ; 串联）");
        }
        Statement stmt = stmts.get(0);

        if (!(stmt instanceof Select)) {
            return reject("仅允许 SELECT/WITH 查询，禁止 " + stmt.getClass().getSimpleName()
                    + "（INSERT/UPDATE/DELETE/DDL/CALL 等均被拦截）");
        }
        Select select = (Select) stmt;

        String bad = new DangerousFunctionFinder().scan(select);
        if (bad != null) {
            return reject("检测到危险函数：" + bad + "()");
        }

        ValidationResult shapeResult = validateSelectShape(select);
        if (!shapeResult.valid()) {
            return shapeResult;
        }

        capOuterLimit(select, maxRows);

        if (select.getLimit() == null) {
            Limit limit = new Limit();
            limit.setRowCount(new LongValue(maxRows));
            select.setLimit(limit);
        }

        String safeSql = select.toString();
        log.debug("SqlSafetyGuard 通过：{}", truncate(safeSql, 200));
        return new ValidationResult(true, "OK", safeSql);
    }

    /**
     * 递归校验查询形态，拦截不稳定分页和高风险 JOIN。
     */
    private ValidationResult validateSelectShape(Select select) {
        if (select.getWithItemsList() != null) {
            for (WithItem<?> item : select.getWithItemsList()) {
                if (item.getSelect() != null) {
                    ValidationResult result = validateSelectShape(item.getSelect());
                    if (!result.valid()) {
                        return result;
                    }
                }
            }
        }

        if (select instanceof PlainSelect ps) {
            return validatePlainSelectShape(ps);
        }
        if (select instanceof SetOperationList sol) {
            for (Select branch : sol.getSelects()) {
                ValidationResult result = validateSelectShape(branch);
                if (!result.valid()) {
                    return result;
                }
            }
        } else if (select instanceof ParenthesedSelect ps && ps.getSelect() != null) {
            return validateSelectShape(ps.getSelect());
        }
        return new ValidationResult(true, "OK", null);
    }

    private ValidationResult validatePlainSelectShape(PlainSelect ps) {
        if (ps.getForMode() != null) {
            return reject("禁止 FOR UPDATE / FOR SHARE（只读连接）");
        }
        if (ps.getIntoTables() != null && !ps.getIntoTables().isEmpty()) {
            return reject("禁止 SELECT ... INTO（写文件/写变量/写表）");
        }
        if (ps.getOffset() != null && (ps.getOrderByElements() == null || ps.getOrderByElements().isEmpty())) {
            return reject("分页查询使用 OFFSET 时必须带稳定 ORDER BY，建议按主表主键或时间字段 + 主键排序");
        }

        ValidationResult fromResult = validateFromItemShape(ps.getFromItem());
        if (!fromResult.valid()) {
            return fromResult;
        }
        if (ps.getWhere() != null) {
            ValidationResult whereResult = validateSubSelectsInExpression(ps.getWhere());
            if (!whereResult.valid()) {
                return whereResult;
            }
        }
        if (ps.getJoins() == null || ps.getJoins().isEmpty()) {
            return new ValidationResult(true, "OK", null);
        }
        if (ps.getJoins().size() > maxJoins) {
            return reject("当前 SQL 关联表过多，最多允许 " + maxJoins
                    + " 个 JOIN。请拆成多步查询：先查主表分页，再按主键查询详情；如果是统计问题，请改为 GROUP BY 聚合查询");
        }

        for (Join join : ps.getJoins()) {
            if (join.isCross()) {
                return reject("禁止 CROSS JOIN。请补充明确的 JOIN ON 条件，避免产生笛卡尔积");
            }
            if (join.isSimple()) {
                return reject("禁止无条件 JOIN。请使用明确的 JOIN ON/USING 条件，避免产生笛卡尔积");
            }
            ValidationResult rightResult = validateFromItemShape(join.getRightItem());
            if (!rightResult.valid()) {
                return rightResult;
            }
            if (join.getOnExpression() != null) {
                ValidationResult onResult = validateSubSelectsInExpression(join.getOnExpression());
                if (!onResult.valid()) {
                    return onResult;
                }
            }
        }
        return new ValidationResult(true, "OK", null);
    }

    private ValidationResult validateFromItemShape(FromItem item) {
        if (item instanceof ParenthesedSelect ps && ps.getSelect() != null) {
            return validateSelectShape(ps.getSelect());
        }
        if (item instanceof ParenthesedFromItem pf) {
            ValidationResult fromResult = validateFromItemShape(pf.getFromItem());
            if (!fromResult.valid()) {
                return fromResult;
            }
            if (pf.getJoins() != null) {
                if (pf.getJoins().size() > maxJoins) {
                    return reject("当前 SQL 括号 JOIN 关联表过多，最多允许 " + maxJoins
                            + " 个 JOIN。请拆成多步查询或改为聚合查询");
                }
                for (Join join : pf.getJoins()) {
                    if (join.isCross()) {
                        return reject("禁止 CROSS JOIN。请补充明确的 JOIN ON 条件，避免产生笛卡尔积");
                    }
                    if (join.isSimple()) {
                        return reject("禁止无条件 JOIN。请使用明确的 JOIN ON/USING 条件，避免产生笛卡尔积");
                    }
                    ValidationResult rightResult = validateFromItemShape(join.getRightItem());
                    if (!rightResult.valid()) {
                        return rightResult;
                    }
                }
            }
        }
        return new ValidationResult(true, "OK", null);
    }

    private ValidationResult validateSubSelectsInExpression(Expression expression) {
        SubSelectShapeValidator validator = new SubSelectShapeValidator();
        expression.accept(validator);
        return validator.result;
    }

    /**
     * 用户 LIMIT 超阈值则下调到 maxRows（仅处理字面量 ROWCOUNT）。
     */
    private void capOuterLimit(Select select, long maxRows) {
        Limit limit = select.getLimit();
        if (limit != null && limit.getRowCount() instanceof LongValue) {
            long userVal = ((LongValue) limit.getRowCount()).getValue();
            if (userVal > maxRows) {
                ((LongValue) limit.getRowCount()).setValue(maxRows);
                log.debug("用户 LIMIT {} > 阈值 {}，已下调", userVal, maxRows);
            }
        }
        // SetOperationList 分支内的 LIMIT 不动
    }

    private static ValidationResult reject(String reason) {
        return new ValidationResult(false, reason, null);
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "...");
    }

    private class SubSelectShapeValidator extends ExpressionVisitorAdapter<Object> {
        private ValidationResult result = new ValidationResult(true, "OK", null);

        @Override
        public <S> Object visit(ParenthesedSelect select, S context) {
            if (result.valid() && select.getSelect() != null) {
                result = validateSelectShape(select.getSelect());
            }
            return super.visit(select, context);
        }
    }

    /**
     * 危险函数扫描器：递归遍历 AST 所有节点（含 selectItems/WHERE/JOIN ON/子查询），
     * 重写 visit(Function) 拦截危险函数。
     */
    private static class DangerousFunctionFinder extends ExpressionVisitorAdapter<Object> {
        private String found;

        String scan(Statement stmt) {
            found = null;
            if (stmt instanceof Select s) {
                scanSelect(s);
            }
            return found;
        }

        private void scanSelect(Select select) {
            if (found != null) return;
            if (select.getWithItemsList() != null) {
                for (WithItem<?> item : select.getWithItemsList()) {
                    if (item.getSelect() != null) scanSelect(item.getSelect());
                }
            }
            if (select instanceof PlainSelect ps) {
                scanPlainSelect(ps);
            } else if (select instanceof SetOperationList sol) {
                for (Select branch : sol.getSelects()) scanSelect(branch);
            } else if (select instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                scanSelect(ps.getSelect());
            }
        }

        private void scanPlainSelect(PlainSelect ps) {
            if (found != null) return;

            if (ps.getSelectItems() != null) {
                for (SelectItem item : ps.getSelectItems()) {
                    try {
                        java.lang.reflect.Method m = item.getClass().getMethod("getExpression");
                        Object expr = m.invoke(item);
                        if (expr instanceof Expression e) {
                            e.accept(this);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (ps.getWhere() != null) ps.getWhere().accept(this);

            if (ps.getFromItem() instanceof net.sf.jsqlparser.statement.select.ParenthesedSelect ps2 && ps2.getSelect() != null) {
                scanSelect(ps2.getSelect());
            }

            if (ps.getJoins() != null) {
                for (Join j : ps.getJoins()) {
                    if (j.getOnExpression() != null) j.getOnExpression().accept(this);
                    if (j.getRightItem() instanceof net.sf.jsqlparser.statement.select.ParenthesedSelect ps2 && ps2.getSelect() != null) {
                        scanSelect(ps2.getSelect());
                    }
                }
            }
        }

        @Override
        public <S> Object visit(Function function, S context) {
            if (found == null && function.getName() != null) {
                String lower = function.getName().toLowerCase();
                if (DANGEROUS_FUNCTIONS.contains(lower)) {
                    found = lower;
                }
            }
            return super.visit(function, context);
        }
    }
}