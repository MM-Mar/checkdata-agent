package com.checkdata.agent.service.permission;

import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.common.datascope.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认权限规则：标准业务表（同时具备 user_id 和 dept_id 列）。
 * <p>
 * SELF：主表别名.user_id = ?
 * DEPT/DEPT_AND_SUB：主表别名.dept_id IN (...)
 */
@Component
public class DefaultPermissionRule implements PermissionRule {

    private static final String DEPT_COLUMN = "dept_id";
    private static final String USER_COLUMN = "user_id";

    private static final Set<String> TABLES = Set.of("payment", "rental");

    @Override
    public Set<String> managedTables() {
        return TABLES;
    }

    @Override
    public Expression buildCondition(Table table, DataScopeContext ctx) {
        if (ctx == null || ctx.scope() == DataScope.ALL) {
            return null;
        }
        if (!TABLES.contains(SqlCondSupport.pureName(table).toLowerCase())) {
            return null;
        }
        String qualifier = SqlCondSupport.qualifierOf(table);

        if (ctx.scope() == DataScope.SELF) {
            String uid = (ctx.userId() == null) ? "NULL" : String.valueOf(ctx.userId());
            return SqlCondSupport.parseCond(qualifier + "." + USER_COLUMN + " = " + uid);
        }

        // DEPT / DEPT_AND_SUB
        String inList = (ctx.deptIdList() == null || ctx.deptIdList().isEmpty())
                ? "NULL"
                : ctx.deptIdList().stream().map(String::valueOf).collect(Collectors.joining(", "));
        return SqlCondSupport.parseCond(qualifier + "." + DEPT_COLUMN + " IN (" + inList + ")");
    }
}
