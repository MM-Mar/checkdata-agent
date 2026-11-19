package com.checkdata.agent.domain.entities;

import java.util.List;

/**
 * 批量表详情查询结果
 *
 * @param found   找到的表/视图（按入参顺序）
 * @param missing 未找到的表名（按入参顺序，供 LLM 看哪些表名拼错了）
 */
public record LookupResult(List<TableEntry> found,
                           List<String> missing) {
}
