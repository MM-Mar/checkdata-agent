package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.Mschema;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MSchema 缓存服务。
 * 启动时自省数据库并缓存到 Redis，定时刷新，Redis 不可用时降级到内存。
 */
@Slf4j
@Service
@EnableScheduling
public class MschemaCacheService {

    private static final String REDIS_KEY = "data-agent:mschema:schema";

    private final StringRedisTemplate redis;
    private final MschemaIntrospector introspector;
    private final ObjectMapper objectMapper;

    @Value("${data-agent.schema.mschema.exclude-tables:}")
    private String excludeTablesConfig;

    private volatile Mschema fallback;

    public MschemaCacheService(@Nullable StringRedisTemplate redis,
                               MschemaIntrospector introspector,
                               ObjectMapper objectMapper) {
        this.redis = redis;
        this.introspector = introspector;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 定时刷新
     */
    @Scheduled(cron = "${data-agent.schema.mschema.refresh-cron:0 0 3 * * *}")
    public void refresh() {
        try {
            List<String> excludeTables = parseExcludeTables();
            Mschema schema = introspector.readSchema(null, null, excludeTables);
            this.fallback = schema;
            if (redis != null) {
                try {
                    String json = objectMapper.writeValueAsString(schema);
                    redis.opsForValue().set(REDIS_KEY, json, 7, TimeUnit.DAYS);
                    log.info("[checkdata-agent] MSchema 刷新成功：{} 表 (排除: {}), 已缓存至 Redis",
                            schema.tables().size(),
                            excludeTables.isEmpty() ? "无" : String.join(",", excludeTables));
                } catch (Exception re) {
                    log.warn("[checkdata-agent] MSchema 写入 Redis 失败，仅使用内存缓存", re);
                }
            } else {
                log.info("[checkdata-agent] MSchema 刷新成功：{} 表 (排除: {}), Redis 未启用，仅使用内存缓存",
                        schema.tables().size(),
                        excludeTables.isEmpty() ? "无" : String.join(",", excludeTables));
            }
        } catch (Exception e) {
            log.error("[checkdata-agent] MSchema 刷新失败，将继续使用缓存", e);
        }
    }

    /**
     * 获取当前 schema（优先 Redis，不可用时降级内存）。
     */
    public Mschema get() {
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(REDIS_KEY);
                if (json != null && !json.isEmpty()) {
                    return objectMapper.readValue(json, Mschema.class);
                }
            } catch (Exception e) {
                log.debug("[checkdata-agent] Redis 读取 MSchema 失败，降级到内存缓存");
            }
        }
        if (fallback != null) {
            return fallback;
        }
        refresh();
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("MSchema 不可用：Redis 和内存缓存均为空，请检查数据库连接");
    }

    /**
     * 解析 exclude-tables 配置为 List。
     */
    private List<String> parseExcludeTables() {
        if (excludeTablesConfig == null || excludeTablesConfig.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(excludeTablesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}