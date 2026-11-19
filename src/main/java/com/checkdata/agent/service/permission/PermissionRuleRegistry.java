package com.checkdata.agent.service.permission;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限规则注册中心：表名 -> 规则。
 * 启动时按 managedTables() 建路由表，未命中的表走 DefaultPermissionRule。
 */
@Component
public class PermissionRuleRegistry {

    private final PermissionRule defaultRule;
    private final Map<String, PermissionRule> overrides;

    public PermissionRuleRegistry(DefaultPermissionRule defaultRule,
                                  List<PermissionRule> rules) {
        this.defaultRule = defaultRule;
        Map<String, PermissionRule> m = new HashMap<>();
        for (PermissionRule r : rules) {
            if (r == defaultRule) {
                continue;
            }
            for (String t : r.managedTables()) {
                if (t != null && !t.isBlank()) {
                    m.put(t.trim().toLowerCase(), r);
                }
            }
        }
        this.overrides = Map.copyOf(m);
    }

    /**
     * 按表名（纯名，可任意大小写）解析规则。
     * 未配置专属规则的表返回 DefaultPermissionRule。
     */
    public PermissionRule get(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return defaultRule;
        }
        return overrides.getOrDefault(tableName.toLowerCase(), defaultRule);
    }
}
