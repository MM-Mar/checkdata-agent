package com.checkdata.agent.service.permission;

import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.common.datascope.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * sys_user 表权限规则。
 * <p>
 * SELF：主表别名.id = ?
 * DEPT/DEPT_AND_SUB：EXISTS(SELECT 1 FROM sys_user_dept
 *     WHERE sys_user_dept.user_id = 主表别名.id AND sys_user_dept.dept_id IN (...))
 */
@Component
public class SysUserPermissionRule implements PermissionRule {

    private static final String TABLE = "sys_user";
    private static final String PK_COLUMN = "id";

    private static final String LINK_TABLE = "sys_user_dept";
    private static final String LINK_USER_COLUMN = "user_id";
    private static final String LINK_DEPT_COLUMN = "dept_id";

    @Override
    public Set<String> managedTables() {
        return Set.of(TABLE);
    }

    @Override
    public Expression buildCondition(Table table, DataScopeContext ctx) {
        if (ctx == null || ctx.scope() == DataScope.ALL) {
            return null;
        }
        String mainQualifier = SqlCondSupport.qualifierOf(table);

        if (ctx.scope() == DataScope.SELF) {
            String uid = (ctx.userId() == null) ? "NULL" : String.valueOf(ctx.userId());
            return SqlCondSupport.parseCond(mainQualifier + "." + PK_COLUMN + " = " + uid);
        }

        // DEPT / DEPT_AND_SUB
        String inList = (ctx.deptIdList() == null || ctx.deptIdList().isEmpty())
                ? "NULL"
                : ctx.deptIdList().stream().map(String::valueOf).collect(Collectors.joining(", "));
        String linkQualifier = SqlCondSupport.quoteIdentifier(LINK_TABLE);
        String cond = "EXISTS(SELECT 1 FROM " + LINK_TABLE
                + " WHERE " + linkQualifier + "." + LINK_USER_COLUMN + " = " + mainQualifier + "." + PK_COLUMN
                + " AND " + linkQualifier + "." + LINK_DEPT_COLUMN + " IN (" + inList + "))";
        return SqlCondSupport.parseCond(cond);
    }
}
