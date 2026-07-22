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
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getArticle } from '@/api/articleController'
import { marked } from 'marked'
import dayjs from 'dayjs'
import {
    ArrowLeftOutlined,
    DownloadOutlined,
    UnorderedListOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const article = ref<any>(null)

const loadArticle = async () => {
    const taskId = route.params.taskId as string
    if (!taskId) {
        message.error('文章ID不存在')
        return
    }

    loading.value = true
    try {
        const res = await getArticle({ taskId })
        article.value = res.data.data
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
    background-color: #f5f7fa;
    position: relative;
    padding-bottom: 60px;
}

/* 顶部绿色背景及操作栏 */
.detail-header-bg {
    background: linear-gradient(to bottom, #dcfce7 0%, #f5f7fa 100%);
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
    background-color: #22c55e;
    border: none;
    border-radius: 8px;
    font-weight: 500;
}

.export-btn:hover {
    background-color: #16a34a;
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
    border-radius: 16px;
    padding: 48px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
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
    border-radius: 12px;
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
    border-radius: 16px;
    padding: 100px 0;
    text-align: center;
    color: #6b7280;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}
</style>