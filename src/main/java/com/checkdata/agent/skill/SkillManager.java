package com.checkdata.agent.skill;

import com.checkdata.agent.domain.entities.CheckdataSkill;
import com.checkdata.agent.domain.entities.SkillInfo;
import com.checkdata.agent.mapper.CheckdataSkillMapper;
import com.checkdata.agent.utils.FileUtils;
import com.checkdata.agent.utils.FrontmatterUtils;
import com.checkdata.agent.utils.ZipUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.FileSystemUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Skills 管理器 — DB（agentx_skill 表）+ 文件系统双源。
 * <p>
 * 文件系统是 skill 内容的真实来源（SKILL.md + 资源文件），
 * DB 存储 skill 的启用状态和上传元数据。
 * <p>
 * 启动时自动建表 + 同步文件系统到 DB（存量 skill 入库，默认 enabled=1）。
 * 每 3 分钟定时同步，用户手动放目录也能自动发现。
 */
@Slf4j
@Component
public class SkillManager {

    private static final String SKILL_FILE = "SKILL.md";

    private final Path skillsDirectory;
    private final JdbcTemplate jdbcTemplate;
    private final CheckdataSkillMapper skillMapper;

    public SkillManager(@Value("${skills.directory}") String skillsDirectory,
                        DataSource dataSource,
                        CheckdataSkillMapper skillMapper) {
        this.skillsDirectory = Path.of(skillsDirectory);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.skillMapper = skillMapper;
    }

    @PostConstruct
    void init() {
        ensureTableExists();
        syncFilesystemToDb();
    }

    /**
     * 定时同步文件系统 → DB（每 3 分钟），防止用户手动放目录。
     */
    @Scheduled(fixedDelay = 180000, initialDelay = 180000)
    void scheduledSync() {
        syncFilesystemToDb();
    }

    /**
     * 自动建表（模仿 AgentChatMemory 模式：MySQL charset 优先，ANSI 兜底）。
     */
    private void ensureTableExists() {
        String createSql = """
                CREATE TABLE agentx_skill (
                    id           BIGINT       NOT NULL,
                    name         VARCHAR(500) NOT NULL,
                    skill_path   VARCHAR(3000) DEFAULT NULL,
                    description  VARCHAR(3000) DEFAULT '',
                    enabled      TINYINT      NOT NULL DEFAULT 1,
                    file_name    VARCHAR(255) DEFAULT NULL,
                    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id)
                )
                """;
        try {
            jdbcTemplate.execute(createSql + " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        } catch (Exception e) {
            try {
                jdbcTemplate.execute(createSql);
            } catch (Exception e2) {
                log.debug("[SkillManager] 建表跳过（可能已存在）: {}", e2.getMessage());
            }
        }
        try {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_agentx_skill_name ON agentx_skill (name)");
        } catch (Exception e) {
            log.debug("[SkillManager] 唯一索引跳过（可能已存在）: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE agentx_skill ADD COLUMN skill_path VARCHAR(500) DEFAULT NULL");
            log.info("[SkillManager] 存量表已补 skill_path 列");
        } catch (Exception e) {
            log.debug("[SkillManager] skill_path 列跳过（可能已存在）: {}", e.getMessage());
        }
        log.info("[SkillManager] agentx_skill 表就绪");
    }

    /**
     * 扫描 skills 目录，将文件系统同步到 DB（文件系统为权威源）：
     * - FS 有、DB 没有 → 插入（enabled=1）
     * - FS 没有、DB 有 → 删除（目录被手动删了）
     * - 两边都有 → 从 FS 更新 description / skillPath（保留 enabled）
     */
    private void syncFilesystemToDb() {
        if (!Files.isDirectory(skillsDirectory)) {
            log.warn("[SkillManager] skills 目录不存在: {}", skillsDirectory.toAbsolutePath());
            return;
        }

        // 扫描文件系统
        List<String> fsNames = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDirectory, Files::isDirectory)) {
            for (Path dir : stream) {
                if (Files.isRegularFile(dir.resolve(SKILL_FILE))) {
                    fsNames.add(dir.getFileName().toString());
                }
            }
        } catch (IOException e) {
            log.warn("[SkillManager] 扫描 skills 目录失败: {}", e.toString());
        }

        // DB 已有记录
        List<CheckdataSkill> dbSkills = skillMapper.selectList(null);
        List<String> dbNames = dbSkills.stream().map(CheckdataSkill::getName).toList();

        // FS 有、DB 没有 → 插入
        for (String name : fsNames) {
            String absPath = skillsDirectory.resolve(name).toAbsolutePath().toString();
            String description = FrontmatterUtils.extractFromFile(
                    skillsDirectory.resolve(name).resolve(SKILL_FILE), "description");
            if (description == null) description = "";
            if (!dbNames.contains(name)) {
                CheckdataSkill entity = new CheckdataSkill();
                entity.setName(name);
                entity.setSkillPath(absPath);
                entity.setDescription(description);
                entity.setEnabled(1);
                entity.setFileName(null);
                skillMapper.insert(entity);
                log.info("[SkillManager] 存量 skill 入库: {} (enabled=1)", name);
            } else {
                // 从 FS 更新 description / skillPath（保留 enabled）
                CheckdataSkill existing = dbSkills.stream()
                        .filter(s -> s.getName().equals(name)).findFirst().orElse(null);
                if (existing != null) {
                    boolean descChanged = !description.equals(existing.getDescription());
                    boolean pathChanged = !absPath.equals(existing.getSkillPath());
                    if (descChanged || pathChanged) {
                        LambdaUpdateWrapper<CheckdataSkill> uw = new LambdaUpdateWrapper<CheckdataSkill>()
                                .eq(CheckdataSkill::getName, name);
                        if (descChanged) uw.set(CheckdataSkill::getDescription, description);
                        if (pathChanged) uw.set(CheckdataSkill::getSkillPath, absPath);
                        skillMapper.update(null, uw);
                    }
                }
            }
        }

        // FS 没有、DB 有 → 删除
        for (CheckdataSkill dbSkill : dbSkills) {
            if (!fsNames.contains(dbSkill.getName())) {
                skillMapper.delete(new LambdaQueryWrapper<CheckdataSkill>()
                        .eq(CheckdataSkill::getName, dbSkill.getName()));
                log.warn("[SkillManager] 孤儿 DB 记录已清理（目录不存在）: {}", dbSkill.getName());
            }
        }
    }

    /**
     * 列出所有 skill（含 enabled 状态 + 路径）。
     */
    public List<SkillInfo> list() {
        List<CheckdataSkill> skills = skillMapper.selectList(null);
        List<SkillInfo> result = new ArrayList<>();
        for (CheckdataSkill s : skills) {
            result.add(new SkillInfo(s.getName(),
                    s.getSkillPath() == null ? "" : s.getSkillPath(),
                    s.getDescription() == null ? "" : s.getDescription(),
                    s.getEnabled() != null && s.getEnabled() == 1));
        }
        return result;
    }

    /**
     * 返回所有 enabled=1 的 skill 目录绝对路径（供 CheckdataAgent 构建 SkillsTool）。
     */
    public List<String> getEnabledSkillDirs() {
        List<CheckdataSkill> enabled = skillMapper.selectList(
                new LambdaQueryWrapper<CheckdataSkill>().eq(CheckdataSkill::getEnabled, 1));
        List<String> dirs = new ArrayList<>();
        for (CheckdataSkill s : enabled) {
            String path = s.getSkillPath();
            if (path != null && Files.isDirectory(Path.of(path))) {
                dirs.add(path);
            } else {
                log.warn("[SkillManager] enabled skill 目录不存在: {} → {}", s.getName(), path);
            }
        }
        return dirs;
    }

    /**
     * 上传 zip 压缩包，自动解压到 skills 目录。
     * 新 skill 默认启用，覆盖上传保留原 enabled 状态。
     */
    public SkillInfo uploadZip(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("仅支持 .zip 文件");
        }

        Files.createDirectories(skillsDirectory);
        Path tempDir = Files.createTempDirectory(skillsDirectory, "skill-upload-");
        try {
            ZipUtils.extract(file.getInputStream(), tempDir);

            Path skillMd = FileUtils.findFile(tempDir, SKILL_FILE, 3);
            if (skillMd == null) {
                throw new IllegalArgumentException("压缩包中未找到 SKILL.md 文件");
            }

            Path skillSourceDir = skillMd.getParent();
            String content = Files.readString(skillMd);
            String name = FrontmatterUtils.extract(content, "name");
            if (name == null || name.isBlank()) {
                name = skillSourceDir.getFileName().toString();
            }
            name = name.trim();
            validateSkillName(name);

            String description = FrontmatterUtils.extract(content, "description");
            if (description == null) description = "";

            Path targetDir = skillsDirectory.resolve(name);
            if (Files.isDirectory(targetDir)) {
                FileUtils.deleteRecursively(targetDir);
            }

            FileSystemUtils.copyRecursively(skillSourceDir.toFile(), targetDir.toFile());

            String absPath = targetDir.toAbsolutePath().toString();
            CheckdataSkill existing = skillMapper.selectOne(
                    new LambdaQueryWrapper<CheckdataSkill>().eq(CheckdataSkill::getName, name));
            boolean enabled;
            if (existing == null) {
                CheckdataSkill entity = new CheckdataSkill();
                entity.setName(name);
                entity.setSkillPath(absPath);
                entity.setDescription(description.trim());
                entity.setEnabled(1);
                entity.setFileName(originalName);
                skillMapper.insert(entity);
                enabled = true;
                log.info("[SkillManager] 新 skill 上传: {} (enabled=1)", name);
            } else {
                skillMapper.update(null, new LambdaUpdateWrapper<CheckdataSkill>()
                        .eq(CheckdataSkill::getName, name)
                        .set(CheckdataSkill::getSkillPath, absPath)
                        .set(CheckdataSkill::getDescription, description.trim())
                        .set(CheckdataSkill::getFileName, originalName));
                enabled = existing.getEnabled() != null && existing.getEnabled() == 1;
                log.info("[SkillManager] skill 覆盖上传: {} (enabled={})", name, enabled);
            }

            return new SkillInfo(name, absPath, description.trim(), enabled);
        } finally {
            FileUtils.deleteRecursively(tempDir);
        }
    }

    /**
     * 切换 skill 启用状态。
     */
    public boolean toggleEnabled(String name, boolean enabled) {
        CheckdataSkill existing = skillMapper.selectOne(
                new LambdaQueryWrapper<CheckdataSkill>().eq(CheckdataSkill::getName, name));
        if (existing == null) {
            throw new IllegalArgumentException("skill 不存在: " + name);
        }
        int newValue = enabled ? 1 : 0;
        if (existing.getEnabled() != null && existing.getEnabled() == newValue) {
            return enabled;
        }
        skillMapper.update(null, new LambdaUpdateWrapper<CheckdataSkill>()
                .eq(CheckdataSkill::getName, name)
                .set(CheckdataSkill::getEnabled, newValue));
        log.info("[SkillManager] skill 切换: {} → enabled={}", name, enabled);
        return enabled;
    }

    /**
     * 删除 skill（目录 + DB 记录）。
     */
    public boolean delete(String name) throws IOException {
        validateSkillName(name);
        Path skillDir = skillsDirectory.resolve(name);
        boolean dirDeleted = false;
        if (Files.isDirectory(skillDir)) {
            FileUtils.deleteRecursively(skillDir);
            dirDeleted = true;
        }
        int deleted = skillMapper.delete(
                new LambdaQueryWrapper<CheckdataSkill>().eq(CheckdataSkill::getName, name));
        log.info("[SkillManager] skill 删除: {} (dir={}, db={})", name, dirDeleted, deleted > 0);
        return dirDeleted || deleted > 0;
    }

    /**
     * 校验 skill 名称（防路径穿越）。
     */
    private void validateSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill 名称不能为空");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            throw new IllegalArgumentException("非法 skill 名称: " + name);
        }
        Path resolved = skillsDirectory.resolve(name);
        if (!resolved.startsWith(skillsDirectory)) {
            throw new IllegalArgumentException("非法 skill 名称: " + name);
        }
    }
}