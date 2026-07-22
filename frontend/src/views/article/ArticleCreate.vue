<template>
    <div class="article-create-page">
        <!-- 三栏布局容器 -->
        <div class="create-layout">
            <!-- 左侧：智能体流程可视化 -->
            <aside class="sidebar-left">
                <div class="sidebar-header">
                    <h3 class="sidebar-title">创作流程</h3>
                    <p class="sidebar-subtitle">智能体协作可视化</p>
                </div>

                <div class="flow-timeline">
                    <div v-for="(step, index) in agentSteps" :key="index" :class="['flow-item', {
                        'active': currentStep === index,
                        'completed': currentStep > index,
                        'pending': currentStep < index
                    }]">
                        <div class="flow-indicator">
                            <LoadingOutlined v-if="currentStep === index && isCreating" class="spin-icon" />
                            <CheckCircleOutlined v-else-if="currentStep > index" />
                            <span v-else class="step-number">{{ index + 1 }}</span>
                        </div>

                        <div class="flow-content">
                            <div class="flow-title">{{ step.title }}</div>
                            <div class="flow-desc">{{ step.description }}</div>
                            <div v-if="currentStep === index && isCreating" class="flow-status">
                                <span class="status-dot"></span>
                                执行中...
                            </div>
                        </div>
                    </div>
                </div>
            </aside>

            <!-- 中间：主内容区 -->
            <main ref="mainContentRef" class="main-content">
                <!-- 输入状态 -->
                <div v-if="!isCreating && !isCompleted" class="input-state">
                    <div class="input-card">
                        <div class="input-header">
                            <h1 class="input-title">创作新文章</h1>
                            <p class="input-subtitle">输入选题，AI 帮你生成爆款文章</p>
                        </div>

                        <div class="input-area">
                            <a-alert v-if="errorVisible" :message="errorMessage" type="error" show-icon closable
                                @close="errorVisible = false">
                                <template #action>
                                    <a-button v-if="taskId" size="small" danger @click="reconnectSSE">重新连接</a-button>
                                </template>
                            </a-alert>
                            <a-textarea v-model:value="topic" placeholder="请输入您想创作的文章选题，例如：2026年AI如何改变职场" :rows="6"
                                :maxlength="500" show-count class="topic-textarea" />
                            <a-button size="large" :loading="isCreating" :disabled="!topic.trim() || !hasQuota"
                                @click="startCreate" class="create-btn">
                                <template #icon>
                                    <RocketOutlined />
                                </template>
                                开始创作
                            </a-button>
                            <div v-if="!hasQuota" class="quota-warning">
                                <WarningOutlined />
                                <span>配额已用完，无法创建文章</span>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 创作进行中 -->
                <div v-if="isCreating && !isCompleted" class="creating-state">
                    <!-- 标题预览 -->
                    <div v-if="article.mainTitle" class="preview-header">
                        <h1 class="article-title">{{ article.mainTitle }}</h1>
                        <p class="article-subtitle">{{ article.subTitle }}</p>
                    </div>

                    <!-- 大纲预览（流式解析展示） -->
                    <div v-if="outlineRaw" class="outline-preview">
                        <div class="section-label">
                            <BulbOutlined />
                            <span>文章大纲</span>
                            <span v-if="isOutlineStreaming" class="typing-cursor">|</span>
                        </div>

                        <div class="outline-list">
                            <div v-for="item in parsedOutline" :key="item.section" class="outline-item">
                                <div class="outline-title">{{ item.section }}. {{ item.title }}</div>
                                <ul class="outline-points">
                                    <li v-for="(point, idx) in item.points" :key="idx">{{ point }}</li>
                                </ul>
                            </div>
                        </div>
                    </div>

                    <!-- 正文预览（流式输出） -->
                    <div v-if="article.content" class="content-preview">
                        <div v-html="markdownToHtml(article.content)" class="markdown-body"></div>
                        <span v-if="isStreaming" class="typing-cursor">|</span>
                    </div>

                    <!-- 配图进度 -->
                    <div v-if="currentStep === 4 && imageProgress > 0" class="image-progress-box">
                        <div class="progress-header">
                            <PictureOutlined />
                            <span>正在生成配图</span>
                        </div>
                        <a-progress :percent="imageProgress" status="active"
                            :stroke-color="{ from: 'var(--primary-color)', to: 'var(--primary-color-hover)' }" />
                        <p class="progress-hint">{{ imageCount }}/{{ totalImages }} 张图片已完成</p>
                    </div>

                    <!-- 加载占位 -->
                    <div v-if="currentStep === 0 && !article.mainTitle" class="loading-placeholder">
                        <a-spin size="large" />
                        <p>AI 正在构思标题...</p>
                    </div>
                </div>

                <!-- 创作完成 -->
                <div v-if="isCompleted" class="completed-state">
                    <div class="success-header">
                        <CheckCircleFilled class="success-icon" />
                        <span>文章创作完成！</span>
                    </div>

                    <div class="preview-header">
                        <h1 class="article-title">{{ article.mainTitle }}</h1>
                        <p class="article-subtitle">{{ article.subTitle }}</p>
                    </div>

                    <div class="content-preview">
                        <div v-html="markdownToHtml(article.fullContent || article.content)" class="markdown-body">
                        </div>
                    </div>
                </div>
            </main>

            <!-- 右侧：辅助面板 -->
            <aside class="sidebar-right">
                <!-- 热门选题 -->
                <div v-if="!isCreating && !isCompleted" class="panel-section">
                    <h4 class="panel-title">
                        <BulbOutlined />
                        热门选题
                    </h4>

                    <div class="hot-tags">
                        <span v-for="example in exampleTopics" :key="example" class="hot-tag" @click="topic = example">
                            {{ example }}
                        </span>
                    </div>
                </div>

                <!-- 创作技巧 -->
                <div v-if="!isCreating && !isCompleted" class="panel-section">
                    <h4 class="panel-title">
                        <StarOutlined />
                        爆款技巧
                    </h4>
                    <div class="tips-list">
                        <div class="tip-item">
                            <div class="tip-icon">1</div>
                            <div class="tip-content">
                                <div class="tip-title">抓住痛点</div>
                                <div class="tip-desc">直击用户最关心的问题</div>
                            </div>
                        </div>

                        <div class="tip-item">
                            <div class="tip-icon">2</div>
                            <div class="tip-content">
                                <div class="tip-title">制造悬念</div>
                                <div class="tip-desc">让读者产生好奇心</div>
                            </div>
                        </div>

                        <div class="tip-item">
                            <div class="tip-icon">3</div>
                            <div class="tip-content">
                                <div class="tip-title">数字吸引</div>
                                <div class="tip-desc">使用具体数据增加说服力</div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 创作进行中的提示 -->
                <div v-if="isCreating && !isCompleted" class="panel-section">
                    <h4 class="panel-title">
                        <ClockCircleOutlined />
                        创作进度
                    </h4>

                    <div class="progress-info">
                        <div class="progress-step">
                            <span class="step-label">当前步骤</span>
                            <span class="step-value">{{ agentSteps[currentStep]?.title }}</span>
                        </div>
                        <div class="progress-step">
                            <span class="step-label">已完成</span>
                            <span class="step-value">{{ currentStep }}/{{ agentSteps.length }}</span>
                        </div>
                    </div>

                    <div class="progress-tip">
                        <InfoCircleOutlined />
                        <span>AI 正在努力创作中，请耐心等待...</span>
                    </div>
                </div>

                <!-- 操作按钮 -->
                <div v-if="isCompleted" class="panel-section">
                    <h4 class="panel-title">
                        <ThunderboltOutlined />
                        快捷操作
                    </h4>

                    <div class="action-list">
                        <a-button block @click="copyContent" class="action-btn">
                            <CopyOutlined />
                            复制全文
                        </a-button>

                        <a-button block @click="viewArticle" class="action-btn">
                            <EyeOutlined />
                            查看详情
                        </a-button>

                        <a-button block type="primary" @click="resetCreate" class="action-btn primary">
                            <RedoOutlined />
                            再创作一篇
                        </a-button>
                    </div>
                </div>

                <!-- 完成后的统计 -->
                <div v-if="isCompleted" class="panel-section stats-section">
                    <h4 class="panel-title">
                        <BarChartOutlined />
                        文章统计
                    </h4>

                    <div class="stats-grid">
                        <div class="stat-item">
                            <div class="stat-value">{{ (article.fullContent || article.content || '').length }}</div>
                            <div class="stat-label">字数</div>
                        </div>

                        <div class="stat-item">
                            <div class="stat-value">{{ article.images?.length || 0 }}</div>
                            <div class="stat-label">配图</div>
                        </div>
                    </div>
                </div>
            </aside>
        </div>
    </div>
</template>

<script setup lang="ts">
import {
    LoadingOutlined,
    CheckCircleOutlined,
    RocketOutlined,
    WarningOutlined,
    BulbOutlined,
    PictureOutlined,
    CheckCircleFilled,
    ClockCircleOutlined,
    InfoCircleOutlined,
    ThunderboltOutlined,
    CopyOutlined,
    EyeOutlined,
    RedoOutlined,
    BarChartOutlined,
    StarOutlined,
} from '@ant-design/icons-vue'
import { createArticle } from '@/api/articleController'
import { closeSSE, connectSSE, type SSEMessage } from '@/utils/sse'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

// 智能体步骤（对应后端 6 个步骤）
const agentSteps = [
    { title: '生成标题', description: 'AI 分析选题，生成吸睛标题' },
    { title: '规划大纲', description: '构建文章结构，理清脉络' },
    { title: '撰写正文', description: '流式生成高质量文章内容' },
    { title: '分析配图', description: '智能分析配图需求和位置' },
    { title: '生成配图', description: '自动匹配高清无版权图片' },
    { title: '图文合成', description: '将配图插入正文，完美呈现' },
]

// 示例选题
const exampleTopics = [
    '2026年AI如何改变职场',
    '程序员如何提升竞争力',
    '远程办公的利与弊',
    '如何培养深度思考',
    '新能源汽车趋势',
    '健康饮食指南',
]

// 页面状态
const topic = ref('')
const isCreating = ref(false)
const isCompleted = ref(false)
const isStreaming = ref(false)
const isOutlineStreaming = ref(false)
const currentStep = ref(0)
const taskId = ref('')
const errorVisible = ref(false)
const errorMessage = ref('')
const hasQuota = ref(true)

// 大纲数据（流式）
const outlineRaw = ref('')

// 配图进度
const imageCount = ref(0)
const totalImages = ref(5)
const imageProgress = ref(0)

// 文章数据
const article = ref<any>({
    mainTitle: '',
    subTitle: '',
    content: '',
    fullContent: '',
    images: [],
})

// SSE 连接实例
let eventSource: EventSource | null = null

// 内容区域引用（用于自动滚动）
const mainContentRef = ref<HTMLElement | null>(null)

interface OutlineItem {
    title: string
    points: string[]
    section: number
}

const parsedOutline = computed<OutlineItem[]>(() => {
    if (!outlineRaw.value) return []
    const str = outlineRaw.value.trim()
    try {
        const parsed = JSON.parse(str)
        if (parsed && Array.isArray(parsed.sections)) return parsed.sections
        return []
    } catch {
        try {
            const sectionsMatch = str.match(/"sections"\s*:\s*\[/)
            if (!sectionsMatch) return []
            const sectionsStart = str.indexOf('[', sectionsMatch.index)
            if (sectionsStart === -1) return []
            const afterStart = str.substring(sectionsStart)
            const lastBrace = afterStart.lastIndexOf('}')
            if (lastBrace > 0) {
                const partialArray = afterStart.substring(0, lastBrace + 1) + ']'
                const parsed = JSON.parse(partialArray)
                if (Array.isArray(parsed)) return parsed
            }
            return []
        } catch {
            return []
        }
    }
})

const markdownToHtml = (markdown: string) => {
    return marked(markdown)
}

const scrollToBottom = () => {
    nextTick(() => {
        if (mainContentRef.value) {
            mainContentRef.value.scrollTop = mainContentRef.value.scrollHeight
        }
    })
}

const startCreate = async () => {
    if (!topic.value.trim()) {
        message.warning('请输入选题')
        return
    }

    isCreating.value = true
    currentStep.value = 0
    errorVisible.value = false
    errorMessage.value = ''

    try {
        const res = await createArticle({ topic: topic.value })
        taskId.value = res.data.data ?? ''
        eventSource = connectSSE(taskId.value, {
            onMessage: handleSSEMessage,
            onError: handleSSEError,
            onComplete: handleSSEComplete,
        })
    } catch (error: any) {
        message.error(error.message || '创建任务失败')
        isCreating.value = false
    }
}

const handleSSEMessage = (msg: SSEMessage) => {
    switch (msg.type) {
        case 'AGENT1_COMPLETE':
            currentStep.value = 1
            article.value.mainTitle = msg.title?.mainTitle
            article.value.subTitle = msg.title?.subTitle
            break
        case 'AGENT2_STREAMING':
            isOutlineStreaming.value = true
            outlineRaw.value += msg.content || ''
            scrollToBottom()
            break
        case 'AGENT2_COMPLETE':
            isOutlineStreaming.value = false
            currentStep.value = 2
            break
        case 'AGENT3_STREAMING':
            isStreaming.value = true
            article.value.content += msg.content || ''
            scrollToBottom()
            break
        case 'AGENT3_COMPLETE':
            isStreaming.value = false
            currentStep.value = 3
            break
        case 'AGENT4_COMPLETE':
            currentStep.value = 4
            totalImages.value = msg.imageRequirements?.length || 5
            break
        case 'IMAGE_COMPLETE':
            imageCount.value++
            imageProgress.value = Math.round((imageCount.value / totalImages.value) * 100)
            break
        case 'AGENT5_COMPLETE':
            currentStep.value = 5
            article.value.images = msg.images
            break
        case 'MERGE_COMPLETE':
            article.value.fullContent = msg.fullContent
            scrollToBottom()
            break
        case 'ALL_COMPLETE':
            currentStep.value = 6
            isCompleted.value = true
            message.success('文章创作完成!')
            break
        case 'ERROR':
            errorMessage.value = msg.message || '创作失败'
            errorVisible.value = true
            isCreating.value = false
            break
    }
}

const handleSSEError = (error: Event) => {
    console.error('文章进度连接失败:', error)
    errorMessage.value = '进度连接失败，任务仍可能在后台生成，请重新连接'
    errorVisible.value = true
    isCreating.value = false
}

const handleSSEComplete = () => { }

const reconnectSSE = () => {
    if (!taskId.value) return
    closeSSE(eventSource)
    errorVisible.value = false
    isCreating.value = true
    eventSource = connectSSE(taskId.value, {
        onMessage: handleSSEMessage,
        onError: handleSSEError,
        onComplete: handleSSEComplete,
    })
}

const copyContent = async () => {
    const content = article.value.fullContent || article.value.content || ''
    try {
        await navigator.clipboard.writeText(content)
        message.success('已复制到剪贴板')
    } catch {
        message.error('复制失败')
    }
}

const viewArticle = () => {
    router.push(`/article/${taskId.value}`)
}

const resetCreate = () => {
    topic.value = ''
    isCreating.value = false
    isCompleted.value = false
    isStreaming.value = false
    isOutlineStreaming.value = false
    currentStep.value = 0
    imageCount.value = 0
    imageProgress.value = 0
    outlineRaw.value = ''
    article.value = {
        mainTitle: '',
        subTitle: '',
        content: '',
        fullContent: '',
        images: [],
    }
}

onMounted(() => {
    if (route.query.topic) {
        topic.value = route.query.topic as string
    }
})

onBeforeUnmount(() => {
    closeSSE(eventSource)
})
</script>

<style scoped>
.article-create-page {
    --primary-color: #22C55E;
    --primary-color-hover: #16A34A;
    --primary-color-light: #dcfce7;
    --bg-page: #f5f7fa;
    --bg-card: #ffffff;
    --text-main: #1f2937;
    --text-sub: #6b7280;
    --border-color: #e5e7eb;
    --border-color-light: #f3f4f6;

    background-color: var(--bg-page);
    min-height: calc(100vh - 64px);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    padding-bottom: 40px;
}

.create-layout {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    max-width: 1440px;
    margin: 0 auto;
    padding: 24px;
    gap: 24px;
}

.sidebar-left {
    width: 260px;
    flex-shrink: 0;
    position: sticky;
    top: 24px;
}

.sidebar-header {
    margin-bottom: 32px;
}

.sidebar-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--text-main);
    margin: 0 0 8px 0;
}

.sidebar-subtitle {
    font-size: 13px;
    color: var(--text-sub);
    margin: 0;
}

.flow-timeline {
    display: flex;
    flex-direction: column;
}

.flow-item {
    display: flex;
    position: relative;
    padding-bottom: 36px;
}

.flow-item:last-child {
    padding-bottom: 0;
}

.flow-item::before {
    content: '';
    position: absolute;
    left: 15px;
    top: 32px;
    bottom: 0;
    width: 2px;
    background-color: var(--border-color);
    z-index: 1;
}

.flow-item:last-child::before {
    display: none;
}

.flow-item.completed::before {
    background-color: var(--primary-color-light);
}

.flow-indicator {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background-color: var(--bg-card);
    border: 2px solid var(--border-color);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 2;
    margin-right: 16px;
    color: var(--text-sub);
    font-size: 14px;
    font-weight: 500;
}

.flow-item.active .flow-indicator {
    border-color: var(--primary-color);
    color: var(--primary-color);
}

.flow-item.completed .flow-indicator {
    border-color: var(--border-color);
    background-color: var(--border-color-light);
    color: var(--text-sub);
}

.flow-content {
    flex: 1;
    padding-top: 4px;
}

.flow-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-sub);
    margin-bottom: 4px;
    transition: color 0.3s;
}

.flow-item.active .flow-title {
    color: var(--primary-color);
}

.flow-item.completed .flow-title {
    color: var(--text-sub);
}

.flow-desc {
    font-size: 12px;
    color: #9ca3af;
}

.flow-status {
    margin-top: 8px;
    font-size: 12px;
    color: var(--primary-color);
    display: flex;
    align-items: center;
    gap: 6px;
}

.status-dot {
    width: 6px;
    height: 6px;
    background-color: var(--primary-color);
    border-radius: 50%;
    animation: blink 1.5s infinite;
}

@keyframes blink {

    0%,
    100% {
        opacity: 1;
    }

    50% {
        opacity: 0.4;
    }
}

.main-content {
    flex: 1;
    min-width: 0;
    background: var(--bg-card);
    border-radius: 12px;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
    min-height: 600px;
    display: flex;
    flex-direction: column;
}

.input-state {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 60px 40px;
}

.input-card {
    width: 100%;
    max-width: 640px;
}

.input-header {
    text-align: center;
    margin-bottom: 40px;
}

.input-title {
    font-size: 28px;
    font-weight: 600;
    color: var(--text-main);
    margin-bottom: 12px;
}

.input-subtitle {
    font-size: 15px;
    color: var(--text-sub);
}

.input-area {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.topic-textarea {
    border-radius: 12px;
    padding: 16px;
    font-size: 15px;
    border: 1px solid var(--border-color);
    background-color: #fafafa;
    resize: none;
    transition: all 0.3s;
}

.topic-textarea:focus {
    background-color: var(--bg-card);
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px var(--primary-color-light);
}

.create-btn {
    height: 52px;
    border-radius: 12px;
    font-size: 16px;
    font-weight: 500;
    background-color: var(--border-color-light);
    color: #9ca3af;
    border: none;
    width: 100%;
}

.create-btn:not([disabled]) {
    background-color: var(--primary-color);
    color: white;
}

.create-btn:not([disabled]):hover {
    background-color: var(--primary-color-hover);
}

.creating-state,
.completed-state {
    padding: 40px;
    flex: 1;
}

.image-progress-box {
    background: var(--border-color-light);
    border-radius: 12px;
    padding: 40px 30px;
    margin-top: 32px;
}

.progress-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
    color: var(--text-main);
    margin-bottom: 30px;
}

:deep(.ant-progress-inner) {
    background-color: #e5e7eb !important;
    border-radius: 10px !important;
}

:deep(.ant-progress-bg) {
    height: 12px !important;
    border-radius: 10px !important;
}

:deep(.ant-progress-text) {
    color: var(--text-sub);
    font-size: 14px;
    margin-left: 12px;
}

.progress-hint {
    margin-top: 20px;
    font-size: 14px;
    color: #9ca3af;
    text-align: center;
}

.sidebar-right {
    width: 300px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 20px;
    position: sticky;
    top: 24px;
}

.panel-section {
    background: var(--bg-card);
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
}

.panel-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-main);
    margin: 0 0 20px 0;
    display: flex;
    align-items: center;
    gap: 8px;
}

.hot-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.hot-tag {
    padding: 6px 14px;
    background-color: var(--bg-page);
    color: var(--text-sub);
    border-radius: 20px;
    font-size: 12px;
    cursor: pointer;
    border: 1px solid var(--border-color);
    transition: all 0.2s;
}

.hot-tag:hover {
    color: var(--primary-color);
    border-color: var(--primary-color);
    background-color: var(--primary-color-light);
}

.tips-list {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.tip-item {
    display: flex;
    gap: 12px;
    align-items: flex-start;
}

.tip-icon {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background-color: var(--primary-color);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: bold;
    flex-shrink: 0;
}

.tip-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-main);
    margin-bottom: 4px;
}

.tip-desc {
    font-size: 12px;
    color: #9ca3af;
}

.preview-header {
    margin-bottom: 32px;
}

.article-title {
    font-size: 24px;
    font-weight: 700;
    color: var(--text-main);
    margin-bottom: 8px;
}

.article-subtitle {
    font-size: 15px;
    color: var(--text-sub);
}

.action-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.stats-grid {
    display: flex;
    gap: 16px;
}

.stat-item {
    flex: 1;
    background: var(--bg-page);
    border-radius: 8px;
    padding: 16px;
    text-align: center;
}

.stat-value {
    font-size: 20px;
    font-weight: 600;
    color: var(--text-main);
    margin-bottom: 4px;
}

.stat-label {
    font-size: 12px;
    color: var(--text-sub);
}

:deep(.markdown-body img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 16px 0;
}
</style>
