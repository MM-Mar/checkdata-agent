package com.checkdata.agent.domain.dto;

import java.util.List;

/**
 * /agent/stream 请求体。
 *
 * @param query          用户问题
 * @param conversationId 会话 id
 * @param online         是否开启联网搜索
 * @param fileIds        关联文档 id 列表
 */
public record ChatRequest(
        String query,
        String conversationId,
        Boolean online,
        List<String> fileIds
) {
}
