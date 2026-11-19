package com.checkdata.agent.domain.entities;

/**
 * /agent/stop 响应体。
 */
public record StopResponse(String conversationId, boolean stopped, boolean hasInterruptState) {
}
