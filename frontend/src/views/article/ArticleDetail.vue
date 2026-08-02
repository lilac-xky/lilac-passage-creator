<template>
    <div class="article-detail-page">
        <!-- 顶部通栏渐变背景及悬浮操作栏 -->
        <div class="detail-header-bg">
            <div class="header-actions">
                <a-button class="nav-btn" @click="goBack">
                    <ArrowLeftOutlined /> 返回
                </a-button>
                <a-button type="primary" class="export-btn" @click="exportMarkdown">
                    <DownloadOutlined /> 导出 Markdown
                </a-button>
            </div>
        </div>

        <div class="detail-container">
            <!-- 加载状态 -->
            <div v-if="loading" class="loading-container">
                <a-spin size="large" />
                <p>正在为您加载文章内容...</p>
            </div>

            <!-- 主内容卡片 -->
            <div v-else-if="article" class="article-card">

                <!-- 文章头部 (居中) -->
                <div class="article-header">
                    <h1 class="main-title">{{ article.mainTitle }}</h1>
                    <p class="sub-title">{{ article.subTitle }}</p>
                    <div class="article-meta">
                        <span class="status-tag">已完成</span>
                        <span class="create-time">创建于 {{ dayjs(article.createTime).format('YYYY-MM-DD HH:mm:ss')
                            }}</span>
                    </div>
                </div>

                <!-- 大纲部分 -->
                <div v-if="article.outline && article.outline.length > 0" class="outline-section">
                    <h2 class="section-title">
                        <UnorderedListOutlined class="section-icon" />
                        文章大纲
                    </h2>
                    <div class="outline-list">
                        <div v-for="item in article.outline" :key="item.section" class="outline-block">
                            <div class="outline-title">{{ item.section }}. {{ item.title }}</div>
                            <ul class="outline-points">
                                <li v-for="(point, idx) in item.points" :key="idx">{{ point }}</li>
                            </ul>
                        </div>
                    </div>
                </div>

                <!-- 正文内容部分 -->
                <div class="content-section">
                    <div v-if="article.fullContent" v-html="markdownToHtml(article.fullContent)" class="markdown-body">
                    </div>
                    <div v-else-if="article.content" v-html="markdownToHtml(article.content)" class="markdown-body">
                    </div>
                </div>
            </div>

            <!-- 错误状态 -->
            <div v-else class="error-container">
                <a-empty description="无法找到该文章" />
            </div>
        </div>

        <!-- 执行日志面板 -->
        <div v-if="executionStats && executionStats.logs && executionStats.logs.length > 0"
            class="execution-logs-section">
            <div class="logs-header" @click="showExecutionLogs = !showExecutionLogs">
                <h2 class="section-title">
                    <ClockCircleOutlined class="section-icon" />
                    执行日志
                    <a-tag :color="getStatusColor(executionStats.overallStatus ?? '')" class="status-tag-small">
                        {{ executionStats.overallStatus ?? '' }}
                    </a-tag>
                </h2>
                <ThunderboltOutlined :class="['toggle-icon', { expanded: showExecutionLogs }]" />
            </div>

            <Transition name="expand">
                <div v-show="showExecutionLogs" class="logs-content">
                    <!-- 统计概览 -->
                    <div class="stats-summary">
                        <div class="stat-item">
                            <span class="label">总耗时</span>
                            <span class="value">{{ executionStats.totalDurationMs ?? 0 }}ms</span>
                        </div>
                        <div class="stat-item">
                            <span class="label">智能体数量</span>
                            <span class="value">{{ executionStats.agentCount ?? 0 }}</span>
                        </div>
                        <div class="stat-item">
                            <span class="label">平均耗时</span>
                            <span class="value">
                                {{ executionStats.agentCount && executionStats.totalDurationMs ?
                                    Math.round(executionStats.totalDurationMs / executionStats.agentCount) : 0 }}ms
                            </span>
                        </div>
                    </div>

                    <!-- 智能体时间线 -->
                    <div class="agent-timeline">
                        <div v-for="log in executionStats.logs" :key="log.id"
                            :class="['timeline-item', log.status?.toLowerCase()]">
                            <div class="timeline-indicator">
                                <CheckCircleOutlined v-if="log.status === 'SUCCESS'" class="icon success" />
                                <CloseCircleOutlined v-else-if="log.status === 'FAILED'" class="icon failed" />
                                <LoadingOutlined v-else class="icon running" />
                            </div>
                            <div class="timeline-content">
                                <div class="timeline-header">
                                    <span class="agent-name">{{ getAgentDisplayName(log.agentName ?? '') }}</span>
                                    <span class="duration">{{ log.durationMs ?? 0 }}ms</span>
                                </div>
                                <div class="timeline-time">
                                    {{ log.startTime ? formatDate(log.startTime) : '' }}
                                </div>
                                <div v-if="log.errorMessage" class="error-message">
                                    <CloseCircleOutlined /> {{ log.errorMessage }}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getArticle, getExecutionLogs } from '@/api/articleController'
import { marked } from 'marked'
import dayjs from 'dayjs'
import {
    ArrowLeftOutlined,
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    DownloadOutlined,
    LoadingOutlined,
    ThunderboltOutlined,
    UnorderedListOutlined
} from '@ant-design/icons-vue'

const executionStats = ref<API.AgentExecutionStats | null>(null)
const logsLoading = ref(false)
const showExecutionLogs = ref(false)

// 加载执行日志
const loadExecutionLogs = async (taskId: string) => {
    logsLoading.value = true
    try {
        const res = await getExecutionLogs({ taskId })
        executionStats.value = res.data.data || null
    } catch (error) {
        console.error('加载执行日志失败:', error)
    } finally {
        logsLoading.value = false
    }
}

// 获取智能体显示名称
const getAgentDisplayName = (agentName: string) => {
    const nameMap: Record<string, string> = {
        'agent1_generate_titles': '生成标题',
        'agent2_generate_outline': '生成大纲',
        'agent3_generate_content': '生成正文',
        'agent4_analyze_image_requirements': '分析配图需求',
        'agent5_generate_images': '生成配图',
        'agent6_merge_content': '图文合成',
        'ai_modify_outline': 'AI修改大纲'
    }
    return nameMap[agentName] || agentName
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const article = ref<any>(null)

const getStatusColor = (status: string) => {
    const colors: Record<string, string> = {
        SUCCESS: 'success',
        FAILED: 'error',
        RUNNING: 'processing'
    }
    return colors[status.toUpperCase()] || 'default'
}

const formatDate = (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm:ss')

const loadArticle = async () => {
    const taskId = route.params.taskId as string
    if (!taskId) {
        message.error('文章ID不存在')
        return
    }

    loading.value = true
    try {
        const res = await getArticle({ taskId })
        article.value = res.data.data || null
        await loadExecutionLogs(taskId)
    } catch (error: any) {
        message.error(error.message || '加载失败')
    } finally {
        loading.value = false
    }
}

const markdownToHtml = (markdown: string) => {
    return marked(markdown)
}

const exportMarkdown = () => {
    if (!article.value) return

    let markdown = `# ${article.value.mainTitle}\n\n`
    markdown += `> ${article.value.subTitle}\n\n`

    if (article.value.fullContent) {
        markdown += article.value.fullContent
    } else {
        if (article.value.outline && article.value.outline.length > 0) {
            markdown += `## 目录\n\n`
            article.value.outline.forEach((item: any) => {
                markdown += `${item.section}. ${item.title}\n`
            })
            markdown += `\n---\n\n`
        }
        markdown += article.value.content || ''
        if (article.value.images && article.value.images.length > 0) {
            markdown += `\n\n## 配图\n\n`
            article.value.images.forEach((image: any) => {
                markdown += `![${image.description}](${image.url})\n\n`
            })
        }
    }

    const blob = new Blob([markdown], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${article.value.mainTitle || '文章'}.md`
    a.click()
    URL.revokeObjectURL(url)

    message.success('导出成功')
}

const goBack = () => {
    router.push('/article') // 或 router.back()
}

onMounted(() => {
    loadArticle()
})
</script>

<style scoped>
.article-detail-page {
    min-height: calc(100vh - 64px);
    background-color: var(--color-page);
    position: relative;
    padding-bottom: 60px;
}

/* 顶部绿色背景及操作栏 */
.detail-header-bg {
    background: var(--color-primary-soft);
    height: 140px;
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    z-index: 0;
}

.header-actions {
    max-width: 1000px;
    margin: 0 auto;
    padding: 24px;
    display: flex;
    justify-content: space-between;
    position: relative;
    z-index: 1;
}

.nav-btn {
    border-radius: 8px;
    font-weight: 500;
}

.export-btn {
    background-color: var(--color-primary);
    border: none;
    border-radius: 8px;
    font-weight: 500;
}

.export-btn:hover {
    background-color: var(--color-primary-hover);
}

/* 详情主容器 */
.detail-container {
    max-width: 1000px;
    margin: 80px auto 0;
    /* 给头部操作栏留位，形成重叠感 */
    padding: 0 24px;
    position: relative;
    z-index: 2;
}

/* 核心文章卡片 */
.article-card {
    background: #ffffff;
    border: 1px solid var(--color-border);
    border-radius: 8px;
    padding: 48px;
    box-shadow: var(--shadow-md);
}

/* 文章头部居中 */
.article-header {
    text-align: center;
    margin-bottom: 48px;
    padding-bottom: 40px;
    border-bottom: 1px solid #f3f4f6;
}

.main-title {
    font-size: 32px;
    font-weight: 700;
    color: #111827;
    margin-bottom: 16px;
}

.sub-title {
    font-size: 16px;
    color: #4b5563;
    margin-bottom: 24px;
}

.article-meta {
    display: inline-flex;
    align-items: center;
    gap: 12px;
}

.status-tag {
    background: #dcfce7;
    color: #15803d;
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 500;
}

.create-time {
    color: #9ca3af;
    font-size: 14px;
}

/* 大纲区样式（灰底圆角块） */
.outline-section {
    margin-bottom: 48px;
}

.section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 24px;
}

.outline-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.outline-block {
    background-color: #f9fafb;
    border: 1px solid #f3f4f6;
    border-radius: 8px;
    padding: 24px;
}

.outline-title {
    font-size: 16px;
    font-weight: 600;
    color: #111827;
    margin-bottom: 12px;
}

.outline-points {
    margin: 0;
    padding-left: 20px;
    color: #4b5563;
    line-height: 1.8;
}

.outline-points li {
    margin-bottom: 4px;
}

/* 渲染好的正文排版优化 */
.content-section {
    padding-top: 10px;
}

:deep(.markdown-body) {
    font-size: 16px;
    line-height: 1.8;
    color: #374151;
}

:deep(.markdown-body h2) {
    font-size: 20px;
    margin: 32px 0 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f3f4f6;
}

:deep(.markdown-body h3) {
    font-size: 18px;
    margin: 24px 0 12px;
}

:deep(.markdown-body p) {
    margin-bottom: 1em;
}

:deep(.markdown-body img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 16px 0;
}

.loading-container,
.error-container {
    background: #ffffff;
    border: 1px solid var(--color-border);
    border-radius: 8px;
    padding: 100px 0;
    text-align: center;
    color: #6b7280;
    box-shadow: var(--shadow-md);
}

.execution-logs-section {
    width: min(952px, calc(100% - 48px));
    margin: 20px auto 0;
    overflow: hidden;
    border: 1px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-surface);
    box-shadow: var(--shadow-sm);
}

.logs-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 20px 24px;
    cursor: pointer;
    user-select: none;
}

.logs-header:hover {
    background: #f8fbf9;
}

.logs-header .section-title {
    margin: 0;
}

.status-tag-small {
    margin-left: 4px;
    font-weight: 500;
}

.toggle-icon {
    flex: none;
    color: var(--color-text-secondary);
    transition: transform 0.2s ease;
}

.toggle-icon.expanded {
    transform: rotate(90deg);
}

.logs-content {
    padding: 0 24px 24px;
    border-top: 1px solid var(--color-border);
}

.stats-summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1px;
    margin: 20px 0 24px;
    overflow: hidden;
    border: 1px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-border);
}

.stat-item {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 5px;
    padding: 16px 18px;
    background: #f8fbf9;
}

.stat-item .label {
    color: var(--color-text-secondary);
    font-size: 13px;
}

.stat-item .value {
    overflow-wrap: anywhere;
    color: var(--color-text);
    font-size: 20px;
    font-weight: 700;
}

.agent-timeline {
    display: grid;
    gap: 0;
}

.timeline-item {
    position: relative;
    display: grid;
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 12px;
    padding-bottom: 20px;
}

.timeline-item:last-child {
    padding-bottom: 0;
}

.timeline-item:not(:last-child)::before {
    position: absolute;
    top: 22px;
    bottom: 0;
    left: 13px;
    width: 1px;
    background: var(--color-border);
    content: '';
}

.timeline-indicator {
    position: relative;
    z-index: 1;
    display: grid;
    width: 28px;
    height: 28px;
    place-items: center;
    border-radius: 50%;
    background: var(--color-surface);
}

.timeline-indicator .icon {
    font-size: 18px;
}

.timeline-indicator .success {
    color: #16a34a;
}

.timeline-indicator .failed {
    color: #dc2626;
}

.timeline-indicator .running {
    color: #2563eb;
}

.timeline-content {
    min-width: 0;
    padding: 3px 0 2px;
}

.timeline-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.agent-name {
    overflow-wrap: anywhere;
    color: var(--color-text);
    font-weight: 600;
}

.duration,
.timeline-time {
    color: var(--color-text-secondary);
    font-size: 13px;
}

.duration {
    flex: none;
    font-variant-numeric: tabular-nums;
}

.timeline-time {
    margin-top: 4px;
}

.error-message {
    margin-top: 10px;
    padding: 10px 12px;
    border: 1px solid #fecaca;
    border-radius: 6px;
    background: #fef2f2;
    color: #b91c1c;
    overflow-wrap: anywhere;
}

.expand-enter-active,
.expand-leave-active {
    transition: opacity 0.18s ease;
}

.expand-enter-from,
.expand-leave-to {
    opacity: 0;
}

@media (max-width: 700px) {
    .detail-header-bg {
        height: 112px;
    }

    .header-actions {
        padding: 18px 16px;
    }

    .detail-container {
        margin-top: 56px;
        padding: 0 16px;
    }

    .article-card {
        padding: 28px 20px;
    }

    .article-header {
        margin-bottom: 34px;
        padding-bottom: 28px;
    }

    .main-title {
        font-size: 26px;
        line-height: 1.35;
    }

    .outline-section {
        margin-bottom: 34px;
    }

    .outline-block {
        padding: 18px;
    }

    .execution-logs-section {
        width: calc(100% - 32px);
        margin-top: 16px;
    }

    .logs-header,
    .logs-content {
        padding-inline: 18px;
    }

    .stats-summary {
        grid-template-columns: 1fr;
    }
}
</style>
