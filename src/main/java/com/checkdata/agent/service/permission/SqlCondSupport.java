package com.checkdata.agent.service.permission;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

/**
 * 权限条件构造工具：表名/标识符处理、表达式解析。
 */
public final class SqlCondSupport {

    private SqlCondSupport() {
    }

    /**
     * 取表真实名（去 schema 前缀和反引号）。
     */
    public static String pureName(Table t) {
        String s = t.getName();
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        if (s.startsWith("`") && s.endsWith("`") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 取表在 SQL 中的限定名（别名优先），非标识符字符做反引号转义。
     */
    public static String qualifierOf(Table t) {
        String raw = (t.getAlias() != null && t.getAlias().getName() != null)
                ? t.getAlias().getName() : pureName(t);
        return quoteIdentifier(raw);
    }

    /**
     * 非标识符字符做反引号转义。
     */
    public static String quoteIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            return name;
        }
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * 解析字符串为 JSQLParser Expression，失败抛 IllegalStateException。
     */
    public static Expression parseCond(String cond) {
        try {
            return CCJSqlParserUtil.parseCondExpression(cond);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("构造数据权限条件失败：" + cond, e);
        }
    }
}
