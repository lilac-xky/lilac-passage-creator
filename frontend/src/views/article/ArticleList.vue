<template>
    <div class="article-list-page">
        <!-- 页面顶部装饰渐变与大标题 -->
        <div class="page-header">
            <div class="header-inner">
                <div class="title-area">
                    <h1>历史记录</h1>
                    <p>管理您创作的所有文章</p>
                </div>
                <a-button type="primary" size="large" class="create-btn" @click="$router.push('/create')">
                    <PlusOutlined /> 创作新文章
                </a-button>
            </div>
        </div>

        <div class="list-container">
            <!-- 搜索栏卡片 -->
            <div class="filter-card">
                <div class="filter-inputs">
                    <a-input v-model:value="searchKeyword" placeholder="搜索文章标题..." class="search-input"
                        @pressEnter="loadData">
                        <template #prefix>
                            <SearchOutlined style="color: #9ca3af" />
                        </template>
                    </a-input>

                    <a-range-picker v-model:value="dateRange" class="date-picker" @change="loadData" />

                    <a-select v-model:value="statusFilter" placeholder="全部状态" class="status-select" allow-clear
                        @change="loadData">
                        <a-select-option value="CREATING">创作中</a-select-option>
                        <a-select-option value="COMPLETED">已完成</a-select-option>
                        <a-select-option value="FAILED">失败</a-select-option>
                    </a-select>
                </div>

                <div class="total-count">
                    共 <span>{{ pagination.total }}</span> 篇文章
                </div>
            </div>

            <!-- 表格区域 -->
            <div class="table-card">
                <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination"
                    row-key="id" @change="handleTableChange" :rowClassName="() => 'table-row-custom'">

                    <template #bodyCell="{ column, record }">
                        <!-- 选题 -->
                        <template v-if="column.key === 'topic'">
                            <span class="topic-text">{{ record.topic || '未命名选题' }}</span>
                        </template>

                        <!-- 标题区域 -->
                        <template v-else-if="column.key === 'title'">
                            <div class="title-cell">
                                <div class="main-title">{{ record.mainTitle || '未命名标题' }}</div>
                                <div class="sub-title">{{ record.subTitle || '暂无副标题...' }}</div>
                            </div>
                        </template>

                        <!-- 状态 -->
                        <template v-else-if="column.key === 'status'">
                            <div :class="['status-badge', record.status?.toLowerCase()]">
                                <span class="dot"></span>
                                {{ record.status === 'COMPLETED' ? '已完成' : record.status === 'CREATING' ? '创作中' : '失败'
                                }}
                            </div>
                        </template>

                        <!-- 创建时间 -->
                        <template v-else-if="column.key === 'createTime'">
                            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}</span>
                        </template>

                        <!-- 操作 -->
                        <template v-else-if="column.key === 'action'">
                            <div class="action-buttons">
                                <a class="action-link view" @click="viewArticle(record)">
                                    <EyeOutlined /> 查看
                                </a>
                                <a class="action-link export" @click="exportArticle(record)">
                                    <DownloadOutlined /> 导出
                                </a>
                                <a-popconfirm title="确认删除这篇文章吗？" @confirm="deleteArticle(record)">
                                    <a class="action-link delete">
                                        <DeleteOutlined /> 删除
                                    </a>
                                </a-popconfirm>
                            </div>
                        </template>
                    </template>
                </a-table>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listArticle, getArticle, deleteArticle as deleteArticleApi } from '@/api/articleController'
import dayjs from 'dayjs'
import { SearchOutlined, PlusOutlined, EyeOutlined, DownloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const columns = [
    { title: '选题', dataIndex: 'topic', key: 'topic', width: 220 },
    { title: '标题', key: 'title', width: 350 },
    { title: '状态', key: 'status', width: 120 },
    { title: '创建时间', key: 'createTime', width: 180 },
    { title: '操作', key: 'action', width: 220 },
]

const loading = ref(false)
const dataSource = ref<any[]>([])
const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
})
const searchKeyword = ref('')
const statusFilter = ref<string | undefined>(undefined)
const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)

const loadData = async () => {
    loading.value = true
    try {
        const res = await listArticle({ current: pagination.current, pageSize: pagination.pageSize })
        const pageData = res.data.data
        let records = pageData?.records || []

        if (searchKeyword.value) {
            const keyword = searchKeyword.value.toLowerCase()
            records = records.filter((item: any) =>
                item.mainTitle?.toLowerCase().includes(keyword) || item.topic?.toLowerCase().includes(keyword)
            )
        }
        if (statusFilter.value) {
            records = records.filter((item: any) => item.status === statusFilter.value)
        }
        if (dateRange.value) {
            const [start, end] = dateRange.value
            records = records.filter((item: any) => {
                const createTime = dayjs(item.createTime)
                return createTime.isAfter(start.startOf('day')) && createTime.isBefore(end.endOf('day'))
            })
        }

        dataSource.value = records
        pagination.total = pageData?.totalRow || 0
    } catch (error: any) {
        message.error(error.message || '加载失败')
    } finally {
        loading.value = false
    }
}

const handleTableChange = (paginationInfo: any) => {
    pagination.current = paginationInfo.current
    pagination.pageSize = paginationInfo.pageSize
    loadData()
}

const viewArticle = (record: any) => {
    router.push(`/article/${record.taskId}`)
}

const exportArticle = async (record: any) => {
    try {
        const res = await getArticle({ taskId: record.taskId })
        const article = res.data.data
        if (!article) return message.error('文章数据不存在')

        let markdown = `# ${article.mainTitle}\n\n> ${article.subTitle}\n\n`
        markdown += article.fullContent ? article.fullContent : (article.content || '')

        const blob = new Blob([markdown], { type: 'text/markdown' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${article.mainTitle || '文章'}.md`
        a.click()
        URL.revokeObjectURL(url)
        message.success('导出成功')
    } catch (error: any) {
        message.error(error.message || '导出失败')
    }
}

const deleteArticle = async (record: any) => {
    try {
        await deleteArticleApi({ id: record.id })
        message.success('删除成功')
        loadData()
    } catch (error: any) {
        message.error(error.message || '删除失败')
    }
}

onMounted(() => {
    loadData()
})
</script>

<style scoped>
.article-list-page {
    min-height: calc(100vh - 64px);
    background-color: #f5f7fa;
    position: relative;
}

/* 顶部绿色渐变背景区 */
.page-header {
    background: linear-gradient(to bottom, #dcfce7 0%, #f5f7fa 100%);
    padding: 40px 0 60px;
    /* 留出底部空间给下方的卡片往上提 */
}

.header-inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.title-area h1 {
    font-size: 28px;
    font-weight: 700;
    color: #1f2937;
    margin: 0 0 8px 0;
}

.title-area p {
    font-size: 14px;
    color: #4b5563;
    margin: 0;
}

.create-btn {
    background-color: #22c55e;
    border: none;
    border-radius: 8px;
    font-weight: 500;
    height: 40px;
    padding: 0 20px;
}

.create-btn:hover {
    background-color: #16a34a;
}

/* 列表主容器 */
.list-container {
    max-width: 1200px;
    margin: -40px auto 40px;
    /* 负边距实现悬浮效果 */
    padding: 0 24px;
}

/* 搜索过滤卡片 */
.filter-card {
    background: #ffffff;
    border-radius: 12px;
    padding: 20px 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    margin-bottom: 24px;
}

.filter-inputs {
    display: flex;
    gap: 16px;
    flex: 1;
}

.search-input {
    width: 280px;
    border-radius: 8px;
}

.date-picker {
    width: 260px;
    border-radius: 8px;
}

.status-select {
    width: 140px;
}

:deep(.ant-select-selector) {
    border-radius: 8px !important;
}

.total-count {
    color: #6b7280;
    font-size: 14px;
}

.total-count span {
    color: #1f2937;
    font-weight: 600;
}

/* 表格卡片 */
.table-card {
    background: #ffffff;
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

/* 穿透修改 Ant Table 样式以符合图纸的扁平无框风格 */
:deep(.ant-table) {
    color: #1f2937;
}

:deep(.ant-table-thead > tr > th) {
    background: transparent !important;
    border-bottom: 1px solid #f3f4f6 !important;
    color: #6b7280;
    font-weight: 500;
    padding: 16px 8px;
}

:deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid #f3f4f6 !important;
    padding: 20px 8px;
    vertical-align: top;
    /* 让多行文本从顶端对齐 */
}

:deep(.ant-table-tbody > tr:hover > td) {
    background-color: #f9fafb !important;
}

/* 表格内文字及元素样式 */
.topic-text {
    font-weight: 500;
    color: #374151;
}

.title-cell {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.main-title {
    font-size: 15px;
    font-weight: 600;
    color: #111827;
}

.sub-title {
    font-size: 13px;
    color: #9ca3af;
}

.time-text {
    color: #6b7280;
    font-size: 14px;
}

/* 状态标签样式 */
.status-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 500;
}

.status-badge .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
}

.status-badge.completed {
    background: #dcfce7;
    color: #15803d;
}

.status-badge.completed .dot {
    background: #22c55e;
}

.status-badge.creating {
    background: #dbeafe;
    color: #1d4ed8;
}

.status-badge.creating .dot {
    background: #3b82f6;
}

.status-badge.failed {
    background: #fee2e2;
    color: #b91c1c;
}

.status-badge.failed .dot {
    background: #ef4444;
}

/* 操作按钮组 */
.action-buttons {
    display: flex;
    gap: 16px;
}

.action-link {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    cursor: pointer;
    transition: opacity 0.2s;
}

.action-link:hover {
    opacity: 0.7;
}

.action-link.view {
    color: #22c55e;
}

.action-link.export {
    color: #6b7280;
}

.action-link.delete {
    color: #ef4444;
}
</style>