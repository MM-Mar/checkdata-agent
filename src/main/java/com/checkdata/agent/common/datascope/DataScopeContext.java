package com.checkdata.agent.common.datascope;

import com.checkdata.agent.common.enums.DataScope;

import java.util.List;

/**
 * 数据权限解析结果（DataScopeResolver 产出，DataScopeRewriter 消费）。
 */
public record DataScopeContext(Long userId, DataScope scope, List<Long> deptIdList) {
}
