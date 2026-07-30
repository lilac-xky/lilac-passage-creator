<template>
    <main class="home-page">
        <section class="hero-section">
            <div class="hero-inner">
                <div class="hero-copy">
                    <div class="eyebrow">
                        <BulbOutlined /> AI 智能文章创作
                    </div>
                    <h1>从一个选题，<br><span>到一篇完整好文章</span></h1>
                    <p class="hero-description">
                        Lilac Passage 串联标题、大纲、正文与配图，让内容创作保持清晰、顺畅、有节奏。
                    </p>
                    <div class="hero-actions">
                        <RouterLink class="primary-action" to="/create">
                            开始创作
                            <ArrowRightOutlined />
                        </RouterLink>
                        <RouterLink class="secondary-action" to="/article/list">
                            <FileTextOutlined /> 查看我的文章
                        </RouterLink>
                    </div>
                    <div class="hero-points">
                        <span>
                            <CheckCircleFilled /> 分阶段可控
                        </span>
                        <span>
                            <CheckCircleFilled /> 智能配图
                        </span>
                        <span>
                            <CheckCircleFilled /> Markdown 导出
                        </span>
                    </div>
                </div>

                <div class="workflow-visual" aria-label="Lilac Passage 创作流程预览">
                    <div class="visual-toolbar">
                        <div class="visual-brand"><span>L</span> 创作工作台</div>
                        <div class="visual-status"><i></i> 正在创作</div>
                    </div>
                    <div class="visual-body">
                        <div class="visual-steps">
                            <div class="visual-step done">
                                <CheckOutlined /><b>选题分析</b>
                            </div>
                            <div class="visual-step done">
                                <CheckOutlined /><b>标题生成</b>
                            </div>
                            <div class="visual-step active"><span>3</span><b>大纲编辑</b></div>
                            <div class="visual-step"><span>4</span><b>正文创作</b></div>
                            <div class="visual-step"><span>5</span><b>智能配图</b></div>
                        </div>
                        <div class="visual-document">
                            <span class="document-label">文章大纲</span>
                            <h2>AI 如何重塑现代职场</h2>
                            <div class="outline-row"><b>01</b><span>工作方式正在发生什么变化</span></div>
                            <div class="outline-row active"><b>02</b><span>人与 AI 协作的新方法</span>
                                <EditOutlined />
                            </div>
                            <div class="outline-row"><b>03</b><span>未来职场需要的核心能力</span></div>
                            <div class="document-progress"><i></i><span>内容结构已完成 68%</span></div>
                        </div>
                    </div>
                </div>
            </div>
        </section>

        <section class="capability-section">
            <div class="section-inner">
                <div class="section-heading">
                    <span>核心能力</span>
                    <h2>把复杂的创作过程，变得简单而清晰</h2>
                    <p>每个关键阶段都可查看、可选择、可调整，让 AI 真正服务于你的表达。</p>
                </div>
                <div class="capability-grid">
                    <article class="capability-item">
                        <div class="capability-icon">
                            <BulbOutlined />
                        </div>
                        <h3>智能选题与标题</h3>
                        <p>围绕你的主题生成多组标题建议，从一开始就找到更准确的表达方向。</p>
                    </article>
                    <article class="capability-item">
                        <div class="capability-icon">
                            <ApartmentOutlined />
                        </div>
                        <h3>结构化大纲</h3>
                        <p>先搭建文章逻辑，再进入正文创作；支持编辑与 AI 辅助调整内容结构。</p>
                    </article>
                    <article class="capability-item">
                        <div class="capability-icon">
                            <EditOutlined />
                        </div>
                        <h3>完整正文生成</h3>
                        <p>根据确认后的标题和大纲持续生成正文，保持内容连贯并适配不同写作风格。</p>
                    </article>
                    <article class="capability-item">
                        <div class="capability-icon">
                            <PictureOutlined />
                        </div>
                        <h3>多方式智能配图</h3>
                        <p>结合文章内容生成或匹配图片、图表与图标，让信息呈现更加完整。</p>
                    </article>
                </div>
            </div>
        </section>

        <section class="process-section">
            <div class="section-inner process-layout">
                <div class="process-intro">
                    <span class="section-kicker">创作流程</span>
                    <h2>四步完成一篇文章</h2>
                    <p>从模糊想法到完整内容，每一步都有明确产出，不再面对空白页面反复犹豫。</p>
                    <RouterLink to="/create">立即体验创作流程
                        <ArrowRightOutlined />
                    </RouterLink>
                </div>
                <div class="process-list">
                    <article><b>01</b>
                        <div>
                            <h3>输入你的选题</h3>
                            <p>用一句话描述想写的主题，并选择文章风格与配图方式。</p>
                        </div>
                    </article>
                    <article><b>02</b>
                        <div>
                            <h3>选择标题方向</h3>
                            <p>从生成的标题方案中选择最适合的一组，确定文章切入点。</p>
                        </div>
                    </article>
                    <article><b>03</b>
                        <div>
                            <h3>确认内容大纲</h3>
                            <p>检查并调整章节结构，让文章逻辑符合你的表达意图。</p>
                        </div>
                    </article>
                    <article><b>04</b>
                        <div>
                            <h3>生成并管理文章</h3>
                            <p>等待正文与配图完成，随时回到历史记录查看或导出内容。</p>
                        </div>
                    </article>
                </div>
            </div>
        </section>

        <section class="scenes-section">
            <div class="section-inner">
                <div class="section-heading compact">
                    <span>适用场景</span>
                    <h2>为每一次清晰表达提供助力</h2>
                </div>
                <div class="scene-grid">
                    <div>
                        <ReadOutlined /><span><b>知识分享</b><small>梳理经验与专业观点</small></span>
                    </div>
                    <div>
                        <NotificationOutlined /><span><b>内容运营</b><small>稳定产出主题内容</small></span>
                    </div>
                    <div>
                        <ExperimentOutlined /><span><b>科技解读</b><small>拆解复杂概念与趋势</small></span>
                    </div>
                    <div>
                        <BookOutlined /><span><b>教育科普</b><small>构建清晰易懂的内容</small></span>
                    </div>
                </div>
            </div>
        </section>

        <section class="cta-section">
            <div class="cta-inner">
                <div><span>准备好开始了吗？</span>
                    <h2>把下一个想法，写成完整文章</h2>
                </div>
                <RouterLink to="/create">开始创作
                    <ArrowRightOutlined />
                </RouterLink>
            </div>
        </section>
    </main>
</template>

<script setup lang="ts">
import {
    ApartmentOutlined,
    ArrowRightOutlined,
    BookOutlined,
    BulbOutlined,
    CheckCircleFilled,
    CheckOutlined,
    EditOutlined,
    ExperimentOutlined,
    FileTextOutlined,
    NotificationOutlined,
    PictureOutlined,
    ReadOutlined,
} from '@ant-design/icons-vue'
</script>

<style scoped>
.home-page {
    overflow: hidden;
    color: var(--color-text);
    background: var(--color-surface);
}

.section-inner {
    width: min(var(--content-width), calc(100% - 48px));
    margin: 0 auto;
}

.hero-section {
    padding: 76px 24px 88px;
    background: var(--color-primary-pale);
    border-bottom: 1px solid var(--color-border-light);
}

.hero-inner {
    display: grid;
    grid-template-columns: minmax(0, .9fr) minmax(520px, 1.1fr);
    gap: 72px;
    align-items: center;
    width: min(var(--content-width), 100%);
    margin: 0 auto;
}

.hero-copy,
.workflow-visual {
    min-width: 0;
}

.eyebrow,
.section-kicker {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--color-primary);
    font-size: 14px;
    font-weight: 700;
}

.hero-copy h1 {
    margin: 18px 0 20px;
    color: var(--color-text);
    font-size: clamp(42px, 4.5vw, 58px);
    line-height: 1.13;
    letter-spacing: 0;
}

.hero-copy h1 span {
    color: var(--color-primary);
}

.hero-description {
    max-width: 570px;
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 18px;
    line-height: 1.85;
}

.hero-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 32px;
}

.primary-action,
.secondary-action {
    display: inline-flex;
    min-height: 48px;
    align-items: center;
    justify-content: center;
    gap: 9px;
    padding: 0 20px;
    border-radius: 6px;
    font-weight: 650;
    transition: .2s ease;
}

.primary-action {
    color: #fff;
    background: var(--color-primary);
}

.primary-action:hover {
    color: #fff;
    background: var(--color-primary-hover);
    transform: translateY(-1px);
}

.secondary-action {
    border: 1px solid var(--color-border);
    color: var(--color-text);
    background: #fff;
}

.secondary-action:hover {
    border-color: #9bcdb2;
    color: var(--color-primary-hover);
}

.hero-points {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    margin-top: 24px;
    color: var(--color-text-secondary);
    font-size: 13px;
}

.hero-points span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.hero-points :deep(.anticon) {
    color: var(--color-primary);
}

.workflow-visual {
    max-width: 100%;
    overflow: hidden;
    border: 1px solid #d9e8df;
    border-radius: 8px;
    background: #fff;
    box-shadow: 0 24px 60px rgba(24, 73, 45, .13);
}

.visual-toolbar {
    display: flex;
    height: 54px;
    align-items: center;
    justify-content: space-between;
    padding: 0 18px;
    border-bottom: 1px solid var(--color-border-light);
}

.visual-brand {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 13px;
    font-weight: 700;
}

.visual-brand>span {
    display: grid;
    width: 26px;
    height: 26px;
    place-items: center;
    border-radius: 6px;
    color: #fff;
    background: var(--color-primary);
}

.visual-status {
    display: flex;
    align-items: center;
    gap: 7px;
    color: var(--color-text-secondary);
    font-size: 12px;
}

.visual-status i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #22c55e;
    box-shadow: 0 0 0 4px #e7f8ed;
}

.visual-body {
    display: grid;
    grid-template-columns: 152px 1fr;
    min-height: 390px;
}

.visual-steps {
    padding: 24px 14px;
    border-right: 1px solid var(--color-border-light);
    background: #fafcfb;
}

.visual-step {
    display: flex;
    min-height: 54px;
    align-items: center;
    gap: 10px;
    color: var(--color-text-muted);
    font-size: 12px;
}

.visual-step>span,
.visual-step> :deep(.anticon) {
    display: grid;
    flex: 0 0 24px;
    width: 24px;
    height: 24px;
    place-items: center;
    border: 1px solid var(--color-border);
    border-radius: 50%;
    font-weight: 600;
}

.visual-step>b {
    min-width: 0;
    white-space: nowrap;
}

.visual-step.done {
    color: var(--color-text-secondary);
}

.visual-step.done> :deep(.anticon) {
    border-color: #acd9c1;
    color: var(--color-primary);
    background: var(--color-primary-soft);
}

.visual-step.active {
    color: var(--color-primary-hover);
}

.visual-step.active>span {
    border-color: var(--color-primary);
    color: #fff;
    background: var(--color-primary);
}

.visual-document {
    padding: 35px 32px;
}

.document-label {
    color: var(--color-primary);
    font-size: 12px;
    font-weight: 700;
}

.visual-document h2 {
    margin: 8px 0 26px;
    font-size: 22px;
}

.outline-row {
    display: flex;
    min-height: 52px;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    padding: 0 14px;
    border: 1px solid var(--color-border-light);
    border-radius: 6px;
    color: var(--color-text-secondary);
    font-size: 13px;
}

.outline-row b {
    color: #a1aea7;
    font-size: 11px;
}

.outline-row span {
    flex: 1;
}

.outline-row.active {
    border-color: #a6d8bd;
    color: var(--color-text);
    background: var(--color-primary-pale);
}

.outline-row.active :deep(.anticon) {
    color: var(--color-primary);
}

.document-progress {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 24px;
    color: var(--color-text-muted);
    font-size: 11px;
}

.document-progress i {
    position: relative;
    width: 96px;
    height: 4px;
    border-radius: 2px;
    background: var(--color-border-light);
}

.document-progress i::after {
    position: absolute;
    width: 68%;
    height: 100%;
    border-radius: inherit;
    background: var(--color-primary);
    content: '';
}

.capability-section,
.scenes-section {
    padding: 94px 0;
}

.section-heading {
    max-width: 720px;
    margin: 0 auto 46px;
    text-align: center;
}

.section-heading>span {
    color: var(--color-primary);
    font-size: 14px;
    font-weight: 700;
}

.section-heading h2,
.process-intro h2 {
    margin: 12px 0 14px;
    font-size: 34px;
    line-height: 1.3;
}

.section-heading p,
.process-intro p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 16px;
    line-height: 1.8;
}

.capability-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    border: 1px solid var(--color-border);
    border-radius: 8px;
}

.capability-item {
    min-width: 0;
    padding: 30px 25px;
    background: #fff;
}

.capability-item+.capability-item {
    border-left: 1px solid var(--color-border);
}

.capability-icon {
    display: grid;
    width: 42px;
    height: 42px;
    place-items: center;
    border-radius: 7px;
    color: var(--color-primary);
    background: var(--color-primary-soft);
    font-size: 20px;
}

.capability-item h3 {
    margin: 20px 0 10px;
    font-size: 17px;
}

.capability-item p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 14px;
    line-height: 1.75;
}

.process-section {
    padding: 92px 0;
    background: var(--color-page);
    border-block: 1px solid var(--color-border-light);
}

.process-layout {
    display: grid;
    grid-template-columns: .8fr 1.2fr;
    gap: 90px;
    align-items: start;
}

.process-intro {
    position: sticky;
    top: 100px;
}

.process-intro p {
    margin-bottom: 24px;
}

.process-intro a {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-weight: 650;
}

.process-list article {
    display: grid;
    grid-template-columns: 48px 1fr;
    gap: 20px;
    padding: 4px 0 28px;
    border-bottom: 1px solid var(--color-border);
}

.process-list article+article {
    padding-top: 28px;
}

.process-list article>b {
    display: grid;
    width: 42px;
    height: 42px;
    place-items: center;
    border: 1px solid #b9dac8;
    border-radius: 50%;
    color: var(--color-primary);
    font-size: 12px;
}

.process-list h3 {
    margin: 1px 0 7px;
    font-size: 18px;
}

.process-list p {
    margin: 0;
    color: var(--color-text-secondary);
    line-height: 1.7;
}

.section-heading.compact {
    margin-bottom: 38px;
}

.scene-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 14px;
}

.scene-grid>div {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 15px;
    padding: 22px;
    border: 1px solid var(--color-border);
    border-radius: 7px;
}

.scene-grid :deep(.anticon) {
    color: var(--color-primary);
    font-size: 24px;
}

.scene-grid span {
    display: grid;
    gap: 4px;
}

.scene-grid b {
    font-size: 15px;
}

.scene-grid small {
    color: var(--color-text-secondary);
    white-space: nowrap;
}

.cta-section {
    padding: 0 24px 88px;
    background: #fff;
}

.cta-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 30px;
    width: min(var(--content-width), 100%);
    margin: 0 auto;
    padding: 42px 48px;
    border-radius: 8px;
    color: #fff;
    background: #163c2a;
}

.cta-inner span {
    color: #a9d8c0;
    font-size: 13px;
    font-weight: 700;
}

.cta-inner h2 {
    margin: 8px 0 0;
    color: #fff;
    font-size: 28px;
}

.cta-inner a {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 9px;
    min-height: 46px;
    padding: 0 19px;
    border-radius: 6px;
    color: #123322;
    background: #fff;
    font-weight: 700;
}

@media (max-width: 1020px) {
    .hero-inner {
        grid-template-columns: 1fr;
        gap: 48px;
    }

    .hero-copy {
        max-width: 720px;
    }

    .workflow-visual {
        width: min(700px, 100%);
        min-width: 0;
    }

    .capability-grid,
    .scene-grid {
        grid-template-columns: repeat(2, 1fr);
    }

    .capability-item:nth-child(3) {
        border-top: 1px solid var(--color-border);
        border-left: 0;
    }

    .capability-item:nth-child(4) {
        border-top: 1px solid var(--color-border);
    }
}

@media (max-width: 720px) {
    .section-inner {
        width: min(100% - 32px, var(--content-width));
    }

    .hero-section {
        padding: 54px 16px 60px;
    }

    .hero-copy h1 {
        font-size: 42px;
    }

    .hero-description {
        font-size: 16px;
    }

    .visual-body {
        grid-template-columns: 1fr;
        min-height: 0;
    }

    .visual-steps {
        display: flex;
        overflow-x: auto;
        padding: 13px;
        border-right: 0;
        border-bottom: 1px solid var(--color-border-light);
    }

    .visual-step {
        flex: 0 0 auto;
        min-height: 32px;
        margin-right: 16px;
    }

    .visual-step b,
    .visual-step>span+b {
        display: none;
    }

    .visual-document {
        padding: 26px 20px;
    }

    .capability-section,
    .scenes-section,
    .process-section {
        padding: 68px 0;
    }

    .section-heading h2,
    .process-intro h2 {
        font-size: 28px;
    }

    .process-layout {
        grid-template-columns: 1fr;
        gap: 44px;
    }

    .process-intro {
        position: static;
    }

    .cta-inner {
        align-items: flex-start;
        flex-direction: column;
        padding: 32px 25px;
    }
}

@media (max-width: 520px) {
    .hero-copy h1 {
        font-size: 36px;
    }

    .hero-copy,
    .hero-description {
        min-width: 0;
        overflow-wrap: anywhere;
    }

    .hero-actions,
    .primary-action,
    .secondary-action {
        width: 100%;
    }

    .hero-points {
        gap: 10px 16px;
    }

    .visual-toolbar {
        padding: 0 12px;
    }

    .visual-status {
        display: none;
    }

    .capability-grid,
    .scene-grid {
        grid-template-columns: 1fr;
    }

    .capability-item+.capability-item,
    .capability-item:nth-child(4) {
        border-top: 1px solid var(--color-border);
        border-left: 0;
    }

    .scene-grid small {
        white-space: normal;
    }

    .cta-section {
        padding: 0 16px 64px;
    }

    .cta-inner h2 {
        font-size: 24px;
    }
}
</style>
