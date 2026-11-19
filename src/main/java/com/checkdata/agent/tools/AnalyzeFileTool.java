package com.checkdata.agent.tools;

import com.checkdata.agent.domain.entities.CheckdataFile;
import com.checkdata.agent.service.EmbeddingService;
import com.checkdata.agent.service.FileManageService;
import com.checkdata.agent.service.MultimodalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReactAgent 调用的文件内容加载工具。
 * <p>
 * 路由（按 DB 中的 fileType + embed 字段三路分发，LLM 不需要知道文件类型）：
 * - status != SUCCESS：返回状态提示
 * - 图片：MinIO 下载 → MultimodalService 识别；首次识别后懒写回 extracted_text 作缓存
 * - embed=1：走 RAG 检索（按 question 召回 topK 片段）
 * - 其他：直接返回 extracted_text
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeFileTool {

    private final FileManageService fileManageService;
    private final EmbeddingService embeddingService;
    private final MultimodalService multimodalService;

    @Tool(description = "针对用户上传的临时文件，根据文件 ID 加载已上传文件的内容，同时支持文本解析和图片识别，返回解析后的文本内容。" +
            "触发场景：用户问到'文件/文档/PDF/图片/报表/表格'等文件相关词，" +
            "或提到'这个/这些/那个/刚才的/之前的/第N章/第N页'等指代词且可能指代文件，" +
            "或 system prompt 里'当前会话文件'段列出了文件。" +
            "重要：system prompt 里【本次新上传】是一个整体，用户说'这个/这些/它们'时必须挨个 fileId 全部加载，不能只加载第一个。" +
            "大文件传 question 做语义检索。普通对话不要调本工具。")
    public String analyzeFile(
            @ToolParam(description = "文件 ID（UUID）") String fileId,
            @ToolParam(description = "用户问题，用于 RAG 语义检索") String question) {

        log.info("[checkdata-agent] EXECUTE analyzeFile: fileId={}, question={}", fileId, question);

        if (fileId == null || fileId.trim().isEmpty()) {
            return "文件 ID 不能为空";
        }

        try {
            CheckdataFile file = fileManageService.getFileInfo(fileId);
            if (!"SUCCESS".equals(file.getStatus())) {
                return String.format("文件处理中或处理失败，状态: %s, fileId: %s", file.getStatus(), fileId);
            }

            // 图片：按需调多模态（首次识别后懒写回 extracted_text 缓存）
            if (fileManageService.isImageFile(file.getFileType())) {
                return handleImage(file);
            }

            // 文本：embed=1 走 RAG，否则直接返回 extracted_text
            if (file.getEmbed() != null && file.getEmbed() == 1) {
                List<String> segments = embeddingService.ragRetrieve(fileId, question == null ? "" : question);
                if (segments == null || segments.isEmpty()) {
                    return buildResponse(file, "未检索到与问题相关的内容，可换一种问法或直接传空 question 获取全文摘要。", null, false);
                }
                return buildResponse(file, "RAG 检索", segments, false);
            }

            return buildResponse(file, file.getExtractedText(), null, false);

        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[checkdata-agent] analyzeFile 失败: fileId={}", fileId, e);
            return "加载文件内容失败: " + e.getMessage();
        }
    }

    /**
     * 图片按需识别 + 懒缓存：
     * - extracted_text 已有 → 直接返回（缓存命中）
     * - 否则 → MinIO 下载字节 → MultimodalService 识别 → 写回 DB → 返回
     */
    private String handleImage(CheckdataFile file) {
        if (StringUtils.isNotBlank(file.getExtractedText())) {
            log.info("[checkdata-agent] 图片识别缓存命中: fileId={}", file.getFileId());
            return buildResponse(file, file.getExtractedText(), null, true);
        }

        log.info("[checkdata-agent] 首次读取图片，调用多模态识别: fileId={}", file.getFileId());
        byte[] bytes = fileManageService.downloadFileBytes(file.getFileId());
        String description = multimodalService.imageToText(bytes, file.getFileName());

        // 懒缓存：写回 extracted_text，下次直接命中
        try {
            fileManageService.saveExtractedText(file.getFileId(), description);
        } catch (Exception cacheErr) {
            log.warn("[checkdata-agent] 图片识别结果写回缓存失败（不影响本次返回）: fileId={}, err={}",
                    file.getFileId(), cacheErr.getMessage());
        }
        return buildResponse(file, description, null, true);
    }

    private String buildResponse(CheckdataFile file, String content, List<String> segments, boolean isImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 文件信息 ===\n");
        sb.append("文件名: ").append(file.getFileName()).append("\n");
        sb.append("文件类型: ").append(file.getFileType()).append("\n");
        if (isImage) {
            sb.append("内容来源: 多模态识别\n");
        } else if (file.getEmbed() != null && file.getEmbed() == 1) {
            sb.append("已向量化: 是（按问题检索相关片段）\n");
        }
        sb.append("\n=== 文件内容 ===\n");

        if (segments != null && !segments.isEmpty()) {
            for (int i = 0; i < segments.size(); i++) {
                sb.append("[片段 ").append(i + 1).append("]\n")
                        .append(segments.get(i)).append("\n\n");
            }
        } else if (content != null) {
            sb.append(content);
        } else {
            sb.append("无内容可显示");
        }
        return sb.toString();
    }
}
