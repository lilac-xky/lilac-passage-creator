package com.lilac.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lilac.domain.entity.PromptTemplate;
import com.lilac.mapper.PromptTemplateMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 提示词模板服务（步骤 D9/D10/D11）
 *
 * <p>从 {@code prompt_template} 表读取模板，带 Caffeine 本地缓存。
 *
 * <p><b>缓存策略</b>
 * <ul>
 *   <li>按 {@code name} 缓存「当前启用的最高版本」</li>
 *   <li>5 分钟过期：改库后最多 5 分钟生效，不用重启</li>
 *   <li>提供 {@link #evict(String)} 主动失效，可挂到管理接口上做「立即生效」</li>
 * </ul>
 *
 * <p><b>降级策略（重要）</b>
 *
 * <p>如果数据库里查不到（比如新环境还没跑 V4 seed 脚本），
 * <b>回退到 {@code PromptConstant} 的硬编码值</b>，保证服务可用。
 *
 * <p>这一点是刻意的：把提示词迁到数据库，不能变成「数据库挂了/没初始化
 * 应用就跑不起来」。硬编码常量保留作为兜底，两边内容在迁移时已逐字核对一致。
 */
@Service
@Slf4j
public class PromptTemplateService {

    @Resource
    private PromptTemplateMapper promptTemplateMapper;

    @Resource
    private PromptRenderer renderer;

    /** name -> 模板（当前启用版本中 version 最大的一个） */
    private Cache<String, Optional<PromptTemplate>> cache;

    /** 缓存过期时间（分钟），可通过配置覆盖 */
    private static final long CACHE_TTL_MINUTES = 5L;
    private static final long CACHE_MAX_SIZE = 500L;

    @PostConstruct
    public void init() {
        cache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
        log.info("PromptTemplateService 初始化完成，缓存 TTL={}分钟，本地兜底=PromptConstant",
                CACHE_TTL_MINUTES);
    }

    /**
     * 获取模板正文。
     *
     * @param name    模板名（对应 PromptConstant 常量名）
     * @param fallback 数据库无此模板时的兜底内容（通常传 PromptConstant.XXX）
     * @return 模板正文，保证非 null
     */
    public String getContent(String name, String fallback) {
        return find(name)
                .map(PromptTemplate::getContent)
                .orElseGet(() -> {
                    log.debug("模板 {} 不在数据库中，使用硬编码兜底", name);
                    return fallback;
                });
    }

    /**
     * 获取模板并渲染。
     *
     * @param name     模板名
     * @param fallback 兜底模板
     * @param vars     变量
     */
    public String render(String name, String fallback, Map<String, ?> vars) {
        return renderer.render(getContent(name, fallback), vars);
    }

    /**
     * 查找模板（带缓存）。
     */
    public Optional<PromptTemplate> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return cache.get(name, this::loadFromDb);
    }

    /**
     * 从数据库加载：同 name 下取 enabled=1 且 version 最大的那条。
     */
    private Optional<PromptTemplate> loadFromDb(String name) {
        try {
            List<PromptTemplate> list = promptTemplateMapper.selectListByQuery(
                    com.mybatisflex.core.query.QueryWrapper.create()
                            .where("name = ?", name)
                            .and("enabled = 1")
                            .orderBy("version DESC")
                            .limit(1));
            if (list == null || list.isEmpty()) {
                return Optional.empty();
            }
            PromptTemplate t = list.get(0);
            log.debug("命中数据库模板: {} v{}", t.getName(), t.getVersion());
            return Optional.of(t);
        } catch (Exception e) {
            // 数据库异常不应导致业务不可用，返回空 -> 走 PromptConstant 兜底
            log.error("加载提示词模板失败, name={}, 将使用硬编码兜底", name, e);
            return Optional.empty();
        }
    }

    /**
     * 使某个模板的缓存失效（改库后调用，让新内容立即生效）。
     */
    public void evict(String name) {
        cache.invalidate(name);
        log.info("提示词模板缓存已失效: {}", name);
    }

    /**
     * 清空全部缓存。
     */
    public void evictAll() {
        cache.invalidateAll();
        log.info("提示词模板缓存已全部失效");
    }

    /**
     * 缓存统计（命中率等），可用于监控。
     */
    public Map<String, Long> stats() {
        var s = cache.stats();
        return Map.of(
                "hitCount", s.hitCount(),
                "missCount", s.missCount(),
                "evictionCount", s.evictionCount(),
                "size", cache.estimatedSize()
        );
    }

    /**
     * 列出所有已加载的模板名（调试用）。
     */
    public Map<String, Optional<PromptTemplate>> snapshot() {
        return Collections.unmodifiableMap(cache.asMap());
    }
}
