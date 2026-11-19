package com.checkdata.agent.controller;

import com.checkdata.agent.common.R;
import com.checkdata.agent.domain.entities.SkillInfo;
import com.checkdata.agent.skill.SkillManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skills 管理 API。
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillController {

    private final SkillManager skillManager;

    /**
     * 列出全部 skills（含 enabled 状态）。
     */
    @GetMapping
    public R<List<SkillInfo>> list() {
        return R.ok(skillManager.list());
    }

    /**
     * 上传 zip 压缩包，自动解压。新 skill 默认禁用，覆盖上传保留原状态。
     */
    @PostMapping("/upload")
    public R<SkillInfo> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.paramError("文件不能为空");
        }
        try {
            SkillInfo info = skillManager.uploadZip(file);
            return R.ok(info);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("[skills] 上传失败: {}", e.getMessage(), e);
            return R.fail("上传失败: " + e.getMessage());
        }
    }

    /**
     * 切换 skill 启用/禁用状态。
     */
    @PutMapping("/{name}/toggle")
    public R<Void> toggle(@PathVariable String name, @RequestParam boolean enabled) {
        try {
            skillManager.toggleEnabled(name, enabled);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("[skills] 切换状态失败: {}", e.getMessage(), e);
            return R.fail("操作失败: " + e.getMessage());
        }
    }

    /**
     * 删除 skill（目录 + DB 记录）。
     */
    @DeleteMapping("/{name}")
    public R<Void> delete(@PathVariable String name) {
        try {
            boolean ok = skillManager.delete(name);
            return ok ? R.ok() : R.notFound("skill 不存在: " + name);
        } catch (Exception e) {
            log.error("[skills] 删除失败: {}", e.getMessage(), e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }
}
