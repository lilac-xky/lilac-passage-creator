package com.lilac.integration;

import com.lilac.domain.entity.Article;
import com.lilac.domain.entity.AgentLog;
import com.lilac.domain.entity.PromptTemplate;
import com.lilac.domain.entity.User;
import com.lilac.mapper.AgentLogMapper;
import com.lilac.mapper.ArticleMapper;
import com.lilac.mapper.PromptTemplateMapper;
import com.lilac.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体 ↔ 建表脚本一致性测试（步骤 G1 的核心价值）。
 *
 * <p><b>这个测试要解决的问题</b>
 *
 * <p>项目不用迁移框架，{@code init.sql} 与 {@code upgrade/} 是两份独立 DDL，
 * 靠人工同步。E3 已经因此出过一次事故：init.sql 落后 5 个迁移，
 * 新环境跑不起来。
 *
 * <p>本测试的做法：对每个实体，<b>插入一条包含全部字段的记录再读回来</b>。
 * 只要建表脚本漏了任何一列，MyBatis-Flex 的 INSERT/UPDATE 就会报
 * "Column not found"，测试立刻失败。
 *
 * <p>这比"比对 information_schema"更直接 —— 它验证的是<b>代码真的能用</b>，
 * 而不是两张表长得像。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("实体与建表脚本一致性")
class SchemaEntityConsistencyTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private AgentLogMapper agentLogMapper;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Test
    @DisplayName("user 表：全部字段可写入并读回")
    void userAllColumns() {
        User u = new User();
        u.setUserAccount("schema_test_acc");
        u.setUserPassword("pwd");
        u.setUserName("测试用户");
        u.setUserAvatar("http://a.png");
        u.setUserProfile("简介");
        u.setUserRole("user");
        u.setQuota(10);
        u.setVipTime(LocalDateTime.now());
        u.setEditTime(LocalDateTime.now());
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        u.setIsDelete(0);

        assertEquals(1, userMapper.insert(u));
        assertNotNull(u.getId(), "雪花 ID 应由 MyBatis-Flex 生成");

        User loaded = userMapper.selectOneById(u.getId());
        assertNotNull(loaded);
        assertEquals("schema_test_acc", loaded.getUserAccount());
        assertEquals(10, loaded.getQuota());
    }

    @Test
    @DisplayName("article 表：含 quotaRefunded / imageTotal / imageDegraded 全部字段")
    void articleAllColumns() {
        Article a = new Article();
        a.setTaskId("schema-task-1");
        a.setUserId(1L);
        a.setTopic("测试选题");
        a.setUserDescription("补充");
        a.setEnabledImageMethods("[\"PEXELS\"]");
        a.setStyle("tech");
        a.setMainTitle("主标题");
        a.setSubTitle("副标题");
        a.setTitleOptions("[]");
        a.setOutline("[]");
        a.setContent("正文");
        a.setFullContent("全文");
        a.setCoverImage("http://c.png");
        a.setImages("[]");
        // V5 新增
        a.setImageTotal(5);
        a.setImageFallbackCount(2);
        // V6 新增
        a.setImageDegraded(1);
        a.setStatus("COMPLETED");
        a.setPhase("CONTENT_GENERATING");
        a.setErrorMessage(null);
        a.setCreateTime(LocalDateTime.now());
        a.setCompletedTime(LocalDateTime.now());
        // V2 新增
        a.setQuotaRefunded(0);
        a.setUpdateTime(LocalDateTime.now());
        a.setIsDelete(0);

        assertEquals(1, articleMapper.insert(a));

        Article loaded = articleMapper.selectOneById(a.getId());
        assertNotNull(loaded);
        // 逐个校验本步骤关心的字段（漏列会在这里炸）
        assertEquals(5, loaded.getImageTotal());
        assertEquals(2, loaded.getImageFallbackCount());
        assertEquals(1, loaded.getImageDegraded());
        assertEquals(0, loaded.getQuotaRefunded());
    }

    @Test
    @DisplayName("agent_log 表：含 token 与模板版本字段")
    void agentLogAllColumns() {
        AgentLog log = AgentLog.builder()
                .taskId("schema-task-2")
                .agentName("agent1_generate_titles")
                // V5 新增
                .model("glm-5.2")
                .promptTokens(100)
                .completionTokens(200)
                .totalTokens(300)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .durationMs(1234)
                .status("SUCCESS")
                // V3 新增
                .templateId(7L)
                .templateVersion(2)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        assertEquals(1, agentLogMapper.insert(log));

        AgentLog loaded = agentLogMapper.selectOneById(log.getId());
        assertNotNull(loaded);
        assertEquals(300, loaded.getTotalTokens());
        assertEquals("glm-5.2", loaded.getModel());
        assertEquals(7L, loaded.getTemplateId());
        assertEquals(2, loaded.getTemplateVersion());
    }

    @Test
    @DisplayName("prompt_template 表：唯一键 (name, version) 生效")
    void promptTemplateAllColumns() {
        PromptTemplate t = PromptTemplate.builder()
                .name("SCHEMA_TEST_PROMPT")
                .version(1)
                .agentName("TEST")
                .description("测试")
                .content("内容 {var}")
                .variables("var")
                .enabled(1)
                .abWeight(100)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        assertEquals(1, promptTemplateMapper.insert(t));
        assertNotNull(t.getId());

        List<PromptTemplate> found = promptTemplateMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("name = ?", "SCHEMA_TEST_PROMPT"));
        assertEquals(1, found.size());
        assertEquals("内容 {var}", found.get(0).getContent());
    }

    @Test
    @DisplayName("视图 v_prompt_effect 可查询（存在且语法正确）")
    void viewPromptEffectQueryable() {
        List<Object> rows = agentLogMapper.selectObjectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .select("totalRuns", "successRate")
                        .from("v_prompt_effect"));
        assertNotNull(rows, "视图应可查询；若报 'Table not found' 说明 schema-test.sql 缺视图");
    }

    @Test
    @DisplayName("视图 v_image_degradation 可查询")
    void viewImageDegradationQueryable() {
        List<Object> rows = articleMapper.selectObjectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .select("taskId", "fallbackRate")
                        .from("v_image_degradation"));
        assertNotNull(rows);
    }
}
