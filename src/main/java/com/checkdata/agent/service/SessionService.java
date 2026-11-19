package com.checkdata.agent.service;

import com.checkdata.agent.auth.service.AuthService;
import com.checkdata.agent.domain.entities.CheckdataConversation;
import com.checkdata.agent.domain.entities.CheckdataFile;
import com.checkdata.agent.domain.entities.CheckdataSession;
import com.checkdata.agent.domain.vo.ConversationDetailVO;
import com.checkdata.agent.domain.vo.ConversationPage;
import com.checkdata.agent.domain.vo.ConversationVO;
import com.checkdata.agent.domain.vo.SessionMessageVO;
import com.checkdata.agent.mapper.CheckdataConversationMapper;
import com.checkdata.agent.mapper.CheckdataSessionMapper;
import com.agentx.ai.core.utils.MessageJsonSerializer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int TITLE_MAX_LENGTH = 40;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CheckdataConversationMapper conversationMapper;
    private final CheckdataSessionMapper sessionMapper;
    private final FileManageService fileManageService;
    private final AuthService authService;

    /**
     * 列出会话（按最近活跃倒序，按 conversationId 聚合，分页返回）。
     */
    public ConversationPage listConversations(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : size;

        String currentUserId = currentUserIdOrNull();
        if (currentUserId == null) {
            return ConversationPage.empty(p, s);
        }

        List<CheckdataConversation> all = conversationMapper.selectList(
                new LambdaQueryWrapper<CheckdataConversation>()
                        .eq(CheckdataConversation::getUserId, currentUserId)
                        .orderByDesc(CheckdataConversation::getCreatedAt)
        );
        if (all.isEmpty()) {
            return ConversationPage.empty(p, s);
        }

        // groupingBy 保持插入顺序 = 最近活跃的会话排前面
        Map<String, List<CheckdataConversation>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        CheckdataConversation::getConversationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ConversationVO> allConvs = grouped.entrySet().stream().map(e -> {
            List<CheckdataConversation> rounds = e.getValue();
            CheckdataConversation latest = rounds.get(0);
            CheckdataConversation first = rounds.get(rounds.size() - 1);
            return new ConversationVO(
                    e.getKey(),
                    truncate(first.getQuestion()),
                    rounds.size(),
                    first.getCreatedAt(),
                    latest.getCreatedAt()
            );
        }).toList();

        int total = allConvs.size();
        int from = Math.min(p * s, total);
        int to = Math.min(from + s, total);
        boolean hasMore = to < total;
        return new ConversationPage(allConvs.subList(from, to), total, p, s, hasMore);
    }

    /**
     * 查询会话详情（包含所有问答轮次，按时间正序）。
     */
    public ConversationDetailVO getConversation(String conversationId) {
        String currentUserId = currentUserIdOrNull();
        if (currentUserId == null) {
            return null;
        }

        List<CheckdataConversation> sessions = conversationMapper.selectList(
                new LambdaQueryWrapper<CheckdataConversation>()
                        .eq(CheckdataConversation::getConversationId, conversationId)
                        .eq(CheckdataConversation::getUserId, currentUserId)
                        .orderByAsc(CheckdataConversation::getCreatedAt)
        );
        if (sessions.isEmpty()) {
            return null;
        }

        List<SessionMessageVO> messages = sessions.stream()
                .map(this::buildSessionMessage)
                .toList();

        return new ConversationDetailVO(conversationId, messages);
    }

    private SessionMessageVO buildSessionMessage(CheckdataConversation conv) {
        Long sessionId = conv.getSessionId() != null ? Long.parseLong(conv.getSessionId()) : null;
        List<SessionMessageVO.AttachmentInfo> attachments = loadAttachments(sessionId);
        List<Message> originalMessages = loadOriginalMessages(conv.getConversationId(), conv.getSessionId());
        String answer = extractAnswer(originalMessages);
        String timeline = buildTimeline(originalMessages);
        return new SessionMessageVO(sessionId, conv.getQuestion(), answer, timeline,
                conv.getCreatedAt(), attachments);
    }

    /**
     * 加载指定会话的 original_messages。
     */
    private List<Message> loadOriginalMessages(String conversationId, String sessionId) {
        List<CheckdataSession> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<CheckdataSession>()
                        .eq(CheckdataSession::getConversationId, conversationId)
                        .eq(CheckdataSession::getSessionId, sessionId)
                        .eq(CheckdataSession::getStateKey, "original_messages")
                        .orderByAsc(CheckdataSession::getItemIndex)
        );
        List<Message> all = new ArrayList<>();
        for (CheckdataSession row : rows) {
            if (row.getStateData() != null && !row.getStateData().isBlank()) {
                all.addAll(MessageJsonSerializer.fromJson(row.getStateData()));
            }
        }
        return all;
    }

    /**
     * 从消息链中提取最终回答（最后一个非空 AssistantMessage 文本）。
     */
    private String extractAnswer(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage am) {
                String text = am.getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * 从消息链重构前端时间线 JSON。
     * AssistantMessage 拆为三阶段：思考(reasoning_content) → 正文(content) → 工具调用。
     */
    private String buildTimeline(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> events = new ArrayList<>();
        Set<String> seenToolCallIds = new LinkedHashSet<>();

        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am) {
                String reasoning = extractReasoning(am);
                if (reasoning != null && !reasoning.isBlank()) {
                    events.add(Map.of("type", "thinking", "content", reasoning));
                }
                String text = am.getText();
                if (text != null && !text.isBlank()) {
                    events.add(Map.of("type", "text", "content", text));
                }
                List<AssistantMessage.ToolCall> toolCalls = am.getToolCalls();
                if (toolCalls != null) {
                    for (AssistantMessage.ToolCall tc : toolCalls) {
                        if ("TodoWrite".equalsIgnoreCase(tc.name())) {
                            List<Map<String, Object>> items = parseTodoItems(tc.arguments());
                            if (!items.isEmpty()) {
                                events.add(Map.of("type", "todo", "items", (Object) items));
                            }
                            continue;
                        }
                        seenToolCallIds.add(tc.id());
                        Map<String, Object> toolEvent = new LinkedHashMap<>();
                        toolEvent.put("type", "tool");
                        toolEvent.put("toolName", tc.name());
                        toolEvent.put("toolCallId", tc.id());
                        toolEvent.put("arguments", tc.arguments());
                        toolEvent.put("status", "pending");
                        events.add(toolEvent);
                    }
                }
            } else if (msg instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    if (seenToolCallIds.contains(tr.id())) {
                        for (int i = events.size() - 1; i >= 0; i--) {
                            Map<String, Object> event = events.get(i);
                            if ("tool".equals(event.get("type"))
                                    && tr.id().equals(event.get("toolCallId"))
                                    && "pending".equals(event.get("status"))) {
                                event.put("result", tr.responseData());
                                event.put("status", "completed");
                                break;
                            }
                        }
                    }
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(events);
        } catch (Exception e) {
            log.warn("Failed to serialize timeline: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将 TodoWrite 参数中的 todos 转为前端 todo 面板所需的 items 格式。
     * 参数格式：{"todos": [{"content": "...", "status": "pending", "activeForm": "..."}]}
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseTodoItems(String arguments) {
        try {
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            List<Map<String, Object>> todos = (List<Map<String, Object>>) args.get("todos");
            if (todos == null) return List.of();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> todo : todos) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("text", todo.getOrDefault("content", ""));
                item.put("status", todo.getOrDefault("status", "pending"));
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("Failed to parse TodoWrite arguments: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 AssistantMessage metadata 提取 reasoning_content（兼容两种 key）。
     */
    private String extractReasoning(AssistantMessage am) {
        Map<String, Object> metadata = am.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object rc = metadata.get("reasoningContent");
        if (rc == null) {
            rc = metadata.get("reasoning_content");
        }
        return rc instanceof String s ? s : null;
    }

    /**
     * 删除会话（仅当前用户自己的）。
     */
    public boolean deleteConversation(String conversationId) {
        String currentUserId = currentUserIdOrNull();
        if (currentUserId == null) {
            return false;
        }
        int deleted = conversationMapper.delete(
                new LambdaQueryWrapper<CheckdataConversation>()
                        .eq(CheckdataConversation::getConversationId, conversationId)
                        .eq(CheckdataConversation::getUserId, currentUserId)
        );
        sessionMapper.delete(
                new LambdaQueryWrapper<CheckdataSession>()
                        .eq(CheckdataSession::getConversationId, conversationId)
        );
        log.info("[session] 删除会话: userId={}, conversationId={}, 调用数={}",
                currentUserId, conversationId, deleted);
        return deleted > 0;
    }

    /**
     * 按 agentx_conversation 主键 id 反查该轮上传的文件元数据。
     */
    private List<SessionMessageVO.AttachmentInfo> loadAttachments(Long convId) {
        if (convId == null) {
            return List.of();
        }
        List<CheckdataFile> files = fileManageService.listBySessionId(convId);
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(f -> new SessionMessageVO.AttachmentInfo(
                        f.getFileId(), f.getFileName(), f.getFileType(), f.getFileSize()))
                .toList();
    }

    private String currentUserIdOrNull() {
        Long uid = authService.getCurrentUserIdOrNull();
        return uid == null ? null : uid.toString();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > TITLE_MAX_LENGTH
                ? text.substring(0, TITLE_MAX_LENGTH) + "..."
                : text;
    }
}