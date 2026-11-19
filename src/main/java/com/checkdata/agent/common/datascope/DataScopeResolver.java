package com.checkdata.agent.common.datascope;

import com.checkdata.agent.auth.dto.DeptSimpleVO;
import com.checkdata.agent.auth.dto.LoginUserVO;
import com.checkdata.agent.auth.service.AuthService;
import com.checkdata.agent.common.enums.DataScope;
import com.checkdata.agent.sys.service.DeptTreeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限解析器。
 * <p>
 * 根据 userId 查角色 + 部门挂载，算出能看到的部门 ID 列表。
 * 多部门挂载按并集：每个挂的部门独立按 scope 展开，最后 union。
 * <p>
 * fail-closed：scope 是 DEPT/DEPT_AND_SUB 且部门树未加载时抛 IllegalStateException。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeResolver {

    private final AuthService authService;
    private final DeptTreeCache deptTreeCache;

    /**
     * 解析指定用户的数据权限上下文。
     */
    public DataScopeContext resolve(Long userId) {
        LoginUserVO user = authService.getUserById(userId);
        DataScope scope = DataScope.fromString(user.getDataScope());

        // ALL / SELF 不需要部门列表
        if (scope == DataScope.ALL || scope == DataScope.SELF) {
            return new DataScopeContext(user.getId(), scope, List.of());
        }

        // 部门树没加载好就拒绝执行
        if (!deptTreeCache.isLoaded()) {
            throw new IllegalStateException(
                    "部门数据未加载，无法解析数据权限。请联系管理员检查 sys_dept 表或重启服务。");
        }

        // 用户的全部部门（sys_user_dept 多对多，可能多个）
        List<Long> userDeptIds = user.getDepts() == null ? List.of() :
                user.getDepts().stream()
                .map(DeptSimpleVO::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // 按 scope 展开每个部门
        Set<Long> union = new LinkedHashSet<>();
        for (Long deptId : userDeptIds) {
            if (scope == DataScope.DEPT_AND_SUB) {
                union.addAll(deptTreeCache.collectWithSubtree(deptId));
            } else {
                union.addAll(deptTreeCache.collectSelfOnly(deptId));
            }
        }

        return new DataScopeContext(user.getId(), scope, new ArrayList<>(union));
    }
}
