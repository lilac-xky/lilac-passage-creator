package com.lilac;

import com.lilac.mapper.ArticleMapper;
import com.lilac.mapper.PromptTemplateMapper;
import com.lilac.mapper.UserMapper;
import com.lilac.service.PromptTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring 上下文加载测试（步骤 G1 启用）。
 *
 * <p><b>历史</b>：本测试在改造初期被 {@code @Disabled}，原因是
 * {@code application-dev.yml} 被 gitignore 且从未入库，CI 检出后不存在，
 * datasource / redis / 第三方 key 全缺，上下文必然启动失败。
 *
 * <p><b>现在</b>：新增了 {@code application-test.yml} + H2 内存库 +
 * {@code schema-test.sql}，不再依赖任何外部服务，因此可以真正启用。
 *
 * <p>这个测试的价值不只是"上下文能起来"，它还间接验证了：
 * <ul>
 *   <li><b>建表脚本与实体一致</b> —— schema 加载失败会直接启动失败</li>
 *   <li>所有 Mapper 的 SQL 映射正确</li>
 *   <li>所有 {@code @PostConstruct} 能在无外部依赖下完成</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Test
    @DisplayName("Spring 上下文能加载（含 H2 建表）")
    void contextLoads() {
        assertNotNull(articleMapper, "ArticleMapper 应已装配");
        assertNotNull(userMapper, "UserMapper 应已装配");
        assertNotNull(promptTemplateMapper, "PromptTemplateMapper 应已装配");
        assertNotNull(promptTemplateService, "PromptTemplateService 应已装配");
    }
}
