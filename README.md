# lilac-passage-creator

> AI 爆款文章生成平台 —— 输入一个选题，自动完成 **标题 → 大纲 → 正文 → 配图** 的全流程生成。

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-blue">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen">
  <img alt="Spring AI Alibaba" src="https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.0.0--RC2-orange">
  <img alt="Vue" src="https://img.shields.io/badge/Vue-3.5-42b883">
  <img alt="Tests" src="https://img.shields.io/badge/tests-123%20passed-success">
</p>

---

## 这是什么

面向自媒体创作者的 AI 写作助手。与传统「一键生成全文」的工具不同，
本项目采用 **多阶段人机协同**：每一步生成后由用户确认或修改，再进入下一步。

```
选题 + 风格
    ↓
[阶段1] 生成 3~5 组标题  →  用户挑一个
    ↓
[阶段2] 生成大纲          →  用户可编辑 / 让 AI 改
    ↓
[阶段3] 生成正文 + 配图   →  图文自动合成
    ↓
导出 Markdown
```

**技术定位**：LLM 应用工程（非模型训练）。核心难点不在调用模型，
而在 **编排、流式、降级、以及"不可信输出"的防御**。

---

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 / 框架 | Java 17 · Spring Boot 3.5.14 · Spring MVC |
| AI 编排 | Spring AI Alibaba 1.1.0.0-RC2（StateGraph）· 模型 `glm-5.2` |
| 持久层 | MyBatis-Flex 1.11.1 · MySQL 8.0 |
| 会话 | Spring Session + Redis |
| 实时推送 | SSE（`SseEmitter`） |
| 对象存储 | 腾讯云 COS |
| 支付 | Stripe Checkout + Webhook |
| 生图 | Google GenAI（Nano Banana）· Pexels · Mermaid · Iconify · SVG |
| 前端 | Vue 3.5 · TypeScript · Vite · Ant Design Vue |
| 安全 | DOMPurify（前端 XSS 白名单） |
| CI | GitHub Actions |

---

## 核心设计

### 1. 三阶段状态机 + 可恢复

用户每一步都可能关掉页面。文章状态落库为 `status` + `phase` 双字段：

```
status: PENDING → PROCESSING → COMPLETED / FAILED
phase : PENDING → TITLE_GENERATING → TITLE_SELECTING
      → OUTLINE_GENERATING → OUTLINE_EDITING → CONTENT_GENERATING
```

用户重新打开页面时，按 `phase` 恢复到对应步骤，不丢进度。

> 📌 状态机图见 [`docs/diagrams/state-machine.md`](docs/diagrams/state-machine.md)

### 2. StateGraph 编排

阶段 3 的图（`ArticleAgentOrchestrator:289-303`）：

```
START → content_generator → image_analyzer
      → parallel_image_generator → content_merger → END
```

每个节点是一个 `NodeAction`，状态通过 `OverAllState` 传递，
14 个状态键全部用 `ReplaceStrategy`。

> ⚠️ **诚实说明**：当前三条图都是**直线**，没有条件边和循环。
> 也就是说它目前**等价于顺序调用**，用 StateGraph 的价值在于
> 状态集中管理 + 节点可观测（AOP 记录耗时）+ 后续可扩展分支。
> 本项目的定位是 **多阶段编排式工作流（Orchestration）**，
> 不是自主 Agent（Autonomous Agent）—— 这两者有本质区别。

### 3. 流式输出：SSE 全链路

```
LLM stream → Flux<ChatResponse>
  → 逐 chunk 累加 & 推给 StreamHandler
  → SseEmitterManager.send(taskId, "AGENT3_STREAMING:片段")
  → 前端 EventSource 收到后追加渲染
```

- `StreamHandlerContext` 用 ThreadLocal + taskId 兜底传递 handler
  （因为 `Consumer` 无法进 StateGraph 的状态）
- 13 种消息类型（`SseMessageTypeEnum`），带前缀区分「流式片段」和「事件」
- SSE 超时 30 分钟，超时/完成/错误三种回调都会清理 emitter

### 4. 七种图源策略 + 三级降级

`ImageServiceStrategy` 通过 `@Resource List<ImageSearchService>`
自动收集所有实现，`@PostConstruct` 装进 `EnumMap`。
**新增图源只需实现接口，注册中心零改动。**

| 图源 | 类型 | 用途 |
|---|---|---|
| Pexels | 检索 | 真实场景照片 |
| Nano Banana | AI 生图 | 创意插画 |
| Mermaid | 代码渲染 | 流程图 / 架构图 |
| Iconify | 检索 | 图标 |
| Emoji Pack | 检索 | 表情包 |
| SVG Diagram | AI 生成 | 概念示意图 |
| **Picsum** | **降级兜底** | 唯一 `isFallback=true` |

降级链：`服务不可用 → 数据无效 → COS 上传失败 → Picsum`
且**降级图也会转存 COS**，保证前端统一拿自家域名。

### 5. 不可信输出防御（三层）

这是本项目最值得讲的部分。LLM 的输出是**不可信输入**，三层收口：

| # | 防御点 | 防什么 | 实现 |
|---|---|---|---|
| ① | 配图方式二次白名单 | 模型越权使用用户未授权的图源 | `enforceEnabledImageMethods` |
| ② | Markdown DOMPurify 白名单 | 模型输出恶意 HTML（存储型 XSS） | `utils/markdown.ts` |
| ③ | JSON 结构容错 | 模型输出格式漂移导致任务失败 | `LlmJsonExtractor` |

#### ③ 为什么需要 JSON 容错

Prompt 里写「请直接返回 JSON，不要有其他内容」——**这是请求，不是保证**。
模型常见的破坏性输出：代码围栏、前置解释、全角标点、尾随逗号。
任何一种都会让 `Gson` 抛异常 → 整个阶段 FAILED → **用户白扣一次配额**。

`LlmJsonExtractor` 按由轻到重四级修复：

```
直接解析 → 剥离代码围栏 → 括号配对截取 → 规范化标点 → 明确失败
```

**实测数据**：

```
12 种真实输出形态，解析成功率：
  改造前  2/12  (17%)
  改造后 10/12  (83%)
```

> 剩余 2 个失败是刻意的：完全无 JSON、括号未闭合属于不可恢复，
> 应当明确失败走降级，而不是猜测性补全（避免错误数据静默入库）。

#### 踩坑记录：Gson 的宽松模式会静默补 null

```java
JsonParser.parseString("[1,2,3,]")   // → [1, 2, 3, null]
```

不报错，但多一个 `null` 元素。**静默数据污染比抛异常更危险。**
因此即使原文"能解析"，也要先检测尾随逗号并规范化。

### 6. 安全加固

| 项 | 问题 | 现状 |
|---|---|---|
| 存储型 XSS | `marked()` 输出直喂 `v-html` | ✅ 已修，DOMPurify 白名单 |
| CORS | `allowCredentials(true)` + `origins("*")` | ✅ 已修，显式白名单 |
| 密码存储 | MD5 + 固定盐 | ⬜ 待修（计划迁移 BCrypt） |
| 密钥管理 | 明文写在 `application-dev.yml` | ⚠️ 该文件已 gitignore，建议迁环境变量 |

---

## 快速开始

### 前置依赖

| 依赖 | 版本 / 地址 | 必需 |
|---|---|---|
| JDK | 17 | ✅ |
| Maven | 3.9+ | ✅ |
| MySQL | 8.0，`localhost:3306` | ✅ |
| Redis | `localhost:6379` | ✅（存 Session） |
| Node.js | `^22.18 \|\| >=24.12` | ✅（前端） |
| mermaid CLI | `npm i -g @mermaid-js/mermaid-cli` | ⬜（不装则该图源静默降级） |

### 1. 初始化数据库

```bash
# 第一步：建库 + 建表（5 张表 + 2 个视图）
mysql -uroot -p < backend/src/main/resources/db/init.sql

# 第二步：灌入 11 个提示词模板（可选，不跑也能启动）
mysql -uroot -p lilac-passage-creator \
  -e "source backend/src/main/resources/db/upgrade/004-prompt-seed.sql"
```

脚本幂等（`IF NOT EXISTS` / `CREATE OR REPLACE VIEW`），可重复执行。

**5 张表**：`user` / `article` / `payment_record` / `agent_log` / `prompt_template`
**2 个视图**：`v_prompt_effect`（模板效果聚合）/ `v_image_degradation`（配图降级排序）

> 💡 **不跑第二步也能启动**：`PromptTemplateService` 查不到模板时会自动回退到
> `PromptConstant` 里的硬编码兜底。这是刻意的降级设计。

**已有旧库**：按序号执行 `db/upgrade/` 下的增量脚本（V2 → V6）。

> ⚠️ **本项目不使用 Flyway**，没有自动迁移。所有 SQL 手动执行。
> 因此 `init.sql` 与 `upgrade/` 会双份维护，改结构时两处都要同步。

> ⚠️ 所有实体标注 `@Table(camelToUnderline = false)`，
> 配合 `map-underscore-to-camel-case: false`，
> **列名就是驼峰原样**（`taskId` 而非 `task_id`）。
> `user` 是 MySQL 保留字，引用时需加反引号。

### 2. 配置

复制并填写 `backend/src/main/resources/application-dev.yml`（该文件已被 gitignore）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lilac-passage-creator
    username: root
    password: <your-password>
  data:
    redis:
      host: localhost
      port: 6379
      password: <your-password>
  ai:
    dashscope:
      api-key: <your-dashscope-key>

cos:
  client:
    host: <your-cos-host>
    secretId: <...>
    secretKey: <...>

pexels:
  api-key: <...>

nano-banana:
  api-key: ${GEMINI_API_KEY:}

stripe:
  api-key: <...>
  webhook-secret: <...>
```

### 3. 启动后端

```bash
cd backend
mvn clean spring-boot:run
```

后端端口 **9090**，context-path **`/api`**。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认 **5173**，直连 `http://localhost:9090`
（可用 `VITE_API_BASE_URL` 覆盖，项目未配 vite proxy）。

> ⚠️ 若改了前端端口，记得同步 `cors.allowed-origins`
> 或设环境变量 `CORS_ALLOWED_ORIGINS`。

---

## 测试

```bash
cd backend
mvn test
```

```
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
```

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `EnumTest` | 8 | 图源/风格枚举查找与分类 |
| `ThrowUtilsTest` | 4 | 参数校验统一入口 |
| `GsonUtilsTest` | 10 | JSON 序列化 + 严格/容错双模式 |
| `LlmJsonExtractorTest` | 27 | LLM 输出 6 大类形态修复 |
| `CorsConfigTest` | 8 | CORS 白名单（含 3 条通配符回归断言） |
| `ImageExecutorTest` | 6 | 配图线程池（含"不是 commonPool"回归） |
| `StripeWebhookControllerTest` | 7 | Webhook 状态码分类（4 条回归断言） |
| `PromptRendererTest` | 13 | 占位符渲染与缺失变量告警 |
| `PromptMigrationRegressionTest` | 11 | 迁库前后 prompt 逐字节一致 |
| `TokenUsageContextTest` | 7 | token 采集（含流式取较大值） |
| `ArticleVOImageDegradedTest` | 7 | 配图降级可见化 |
| `BackendApplicationTests` | 1 | Spring 上下文加载（H2，无需外部服务） |
| `SchemaEntityConsistencyTest` | 6 | 实体 ↔ 建表脚本一致性 |
| `SchemaDriftTest` | 5 | `init.sql` ↔ `schema-test.sql` 防漂移 |

> **集成测试不需要 MySQL / Redis / 任何 API Key** ——
> 走 `application-test.yml` 的 H2 内存库 + `schema-test.sql`，
> 上下文约 7.5 秒启动。`mvn test` 在任何机器上都能跑。

> **注意**：Mockito 在 JDK 17 上需要 `-Djdk.attach.allowAttachSelf=true`，
> 已配置在 `pom.xml` 的 surefire 中，本地与 CI 行为一致。

---

## 目录结构

```
lilac-passage-creator/
├── backend/
│   └── src/
│       ├── main/
│       │   ├── java/com/lilac/
│       │   │   ├── agent/          # StateGraph 编排
│       │   │   │   ├── agents/     # 5 个 NodeAction
│       │   │   │   ├── parallel/   # 并行生图（专用线程池）
│       │   │   │   ├── context/    # ThreadLocal：流式 handler / token 采集
│       │   │   │   └── tools/      # @Tool 封装
│       │   │   ├── strategy/       # 7 种图源策略
│       │   │   ├── service/        # 业务逻辑 + PromptTemplateService
│       │   │   ├── controller/     # 22 个接口
│       │   │   ├── config/         # CORS / Async / Stripe / COS...
│       │   │   └── utils/          # GsonUtils / LlmJsonExtractor
│       │   └── resources/
│       │       └── db/
│       │           ├── init.sql    # 全量建表
│       │           └── upgrade/    # 增量脚本 002~006
│       └── test/java/com/lilac/
│           └── unit/               # 单测（112 个）
│           └── integration/        # 集成测试（11 个，H2）
├── frontend/
│   └── src/
│       ├── views/                  # 页面
│       ├── utils/                  # markdown(DOMPurify) / sse
│       └── api/                    # 由 Apifox openapi 生成
├── docs/
│   ├── ONBOARDING.md               # 项目速查手册
│   ├── CHANGELOG.md                # 改造变更索引
│   ├── INTERNSHIP-PLAN.md          # 学习计划
│   └── changes/                    # 逐步改造记录
└── .github/workflows/ci.yml        # CI
```

---

## 接口一览（22 个，前缀 `/api`）

| 模块 | 接口 |
|---|---|
| 文章 | `create` · `confirm-title` · `confirm-outline` · `ai-modify-outline` · `progress/{taskId}`(SSE) · `{taskId}` · `list` · `delete` · `execution-logs/{taskId}` |
| 用户 | `register` · `login` · `get/login` · `logout` · `add` · `delete` · `list/page` · `update` |
| 支付 | `create-vip-session` · `refund` · `records` · `webhook/stripe` |
| 统计 | `overview`(admin) |

---

## 已知问题 / TODO

| # | 问题 | 优先级 |
|---|---|---|
| 1 | 密码用 MD5 + 固定盐 `"lilac"`，应迁 BCrypt | P1 |
| 2 | 密钥明文在 `application-dev.yml`，应迁环境变量 | P1 |
| 3 | `abWeight` 字段未实现真正的 A/B 分流（仅按版本号取最高） | P1 |
| 4 | `templateId/templateVersion` 已建列但无代码写入，效果回收未闭环 | P1 |
| 5 | 配图降级只做提示，未提供「单独重新生成配图」能力 | P2 |
| 6 | SSE `emitterMap` 为单机内存，多实例推不到 | P2 |
| 7 | 集成测试未覆盖 Service 业务逻辑（配额退还幂等性等） | P2 |
| 8 | LLM 解析失败无自动重试（仅有提取层容错） | P2 |
| 9 | 后端未对存储内容做净化（仅前端渲染时过滤） | P2 |
| 10 | 旧实现 `ArticleAgentService` 与 orchestrator 并存，应择一保留 | P2 |

> ✅ 已解决：~~并行生图占 `ForkJoinPool.commonPool`~~（改用专用池）、
> ~~Stripe Webhook 异常一律 500~~（按性质分类）、~~任务失败不退配额~~（幂等退还）

> 详细改造记录见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)

---

## 项目时间线说明

- **2026.06 ~ 08**：完成主体功能开发（标题/大纲/正文/配图全链路、支付、管理后台）
- **2026.10 ~ 11**：系统性工程化重构
  - 补充建表脚本与 CI（此前无任何自动化测试，测试数 19 → 123）
  - 补 test profile：H2 集成测试，CI 无需 MySQL/Redis 即可验证建表脚本
  - 修复存储型 XSS、CORS 全放开两项安全问题
  - 新增 LLM 输出 JSON 容错层，解析成功率 17% → 83%
  - 配图并行生成改用专用线程池（脱离 `ForkJoinPool.commonPool`）
  - Stripe Webhook 按异常性质分类状态码，避免确定性失败被无限重试
  - 任务失败幂等退还配额（CAS 标志位）
  - 提示词迁移至数据库并支持版本管理，11 个模板全部接入
  - 补全 token 与配图降级埋点，发现并修复「14.7% 配图静默降级」问题
  - 收敛重复代码（3 处 `marked()` 调用 → 1 处、4 处 `getStylePrompt` → 1 处）

> 半年后回头重构自己的项目、系统性地发现并修复问题，
> 本身就是一次有价值的实践。

