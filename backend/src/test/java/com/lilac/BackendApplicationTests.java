package com.lilac;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载测试。
 *
 * <p>⚠️ 暂时禁用，原因：
 * <ul>
 *   <li>{@code application-dev.yml} 被 .gitignore 排除，CI 检出后不存在，
 *       datasource / redis / 第三方 API Key 全部缺失，上下文必然启动失败；</li>
 *   <li>即使本地存在该文件，也会真实连接 MySQL、Redis 并初始化 Stripe / COS 客户端，
 *       不适合作为 CI 的自动化测试。</li>
 * </ul>
 *
 * <p>要在 CI 里跑通它，需要补一套 test profile + 嵌入式依赖（H2 / embedded-redis），
 * 属于独立的改造步骤，不在此处一并处理。
 *
 * <p>当前 CI 覆盖的是不依赖 Spring 容器的纯逻辑单测，见 {@code com.lilac.unit} 包。
 */
@Disabled("需要真实 MySQL/Redis 与第三方密钥，且 application-dev.yml 不入库；待补 test profile 后启用")
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
