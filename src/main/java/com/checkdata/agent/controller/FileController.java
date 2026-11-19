package com.checkdata.agent.controller;

import com.checkdata.agent.common.R;
import com.checkdata.agent.domain.entities.CheckdataFile;
import com.checkdata.agent.domain.vo.UploadResponse;
import com.checkdata.agent.service.FileManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文件相关操作
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final Map<String, MediaType> IMAGE_MIME = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "bmp", MediaType.parseMediaType("image/bmp"),
            "webp", MediaType.parseMediaType("image/webp")
    );

    private final FileManageService fileManageService;

    @Value("${file.max-size-mb:100}")
    private long maxSizeMb;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public R<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.paramError("文件不能为空");
        }
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return R.paramError("文件不能超过 " + maxSizeMb + "MB");
        }

        log.info("[checkdata-agent] 收到上传: name={}, size={}", file.getOriginalFilename(), file.getSize());
        try {
            CheckdataFile saved = fileManageService.uploadFile(file);
            return R.ok(UploadResponse.from(saved));
        } catch (Exception e) {
            log.error("[checkdata-agent] 上传失败", e);
            return R.fail("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 文件预览
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String fileId) {
        try {
            CheckdataFile meta = fileManageService.getFileInfo(fileId);
            byte[] bytes = fileManageService.downloadFileBytes(fileId);
            MediaType mime = IMAGE_MIME.getOrDefault(
                    meta.getFileType() == null ? "" : meta.getFileType().toLowerCase(),
                    MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(mime)
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("[checkdata-agent] 预览失败: fileId={}, err={}", fileId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件删除
     */
    @DeleteMapping("/{fileId}")
    public R<Void> delete(@PathVariable String fileId) {
        try {
            fileManageService.deleteFile(fileId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("[checkdata-agent] 删除失败: fileId={}", fileId, e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }
}
