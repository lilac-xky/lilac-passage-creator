<template>
    <div class="statistics-page">
        <!-- 页面头部 -->
        <div class="page-header">
            <div class="header-container">
                <div class="header-content">
                    <h1 class="page-title">数据分析</h1>
                    <p class="page-subtitle">系统运营数据概览</p>
                </div>
                <a-button @click="loadData" :loading="loading" class="refresh-btn">
                    <template #icon>
                        <ReloadOutlined />
                    </template>
                    刷新数据
                </a-button>
            </div>
        </div>

        <div class="container">
            <a-spin :spinning="loading" tip="加载中...">
                <!-- 核心指标卡片 -->
                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="stat-icon" style="background: rgba(34, 197, 94, 0.1)">
                            <FileTextOutlined style="color: var(--color-primary)" />
                        </div>
                        <div class="stat-content">
                            <div class="stat-label">今日创作</div>
                            <div class="stat-value">{{ stats?.todayCount ?? 0 }}</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-icon" style="background: rgba(59, 130, 246, 0.1)">
                            <BarChartOutlined style="color: #3B82F6" />
                        </div>
                        <div class="stat-content">
                            <div class="stat-label">本周创作</div>
                            <div class="stat-value">{{ stats?.weekCount ?? 0 }}</div>
                        </div>
                    </div>

                    <div class="stat-card">
                        <div class="stat-icon" style="background: rgba(168, 85, 247, 0.1)">
                            <RiseOutlined style="color: #A855F7" />
                        </div>
                        <div class="stat-content">
                            <div class="stat-label">本月创作</div>
                            <div class="stat-value">{{ stats?.monthCount ?? 0 }}</div>
                        </div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-icon" style="background: rgba(234, 179, 8, 0.1)">
                            <CheckCircleOutlined style="color: #EAB308" />
                        </div>
                        <div class="stat-content">
                            <div class="stat-label">成功率</div>
                            <div class="stat-value">{{ (stats?.successRate ?? 0).toFixed(1) }}%</div>
                        </div>
                    </div>
                </div>
                <!-- 图表区域 -->
                <div class="charts-grid">
                    <!-- 创作趋势图 -->
                    <a-card :bordered="false" class="chart-card">
                        <h3 class="chart-title">
                            <LineChartOutlined />
                            创作趋势
                        </h3>
                        <div ref="trendChartRef" class="chart-container"></div>
                    </a-card>

                    <!-- 性能统计图 -->
                    <a-card :bordered="false" class="chart-card">
                        <h3 class="chart-title">
                            <ThunderboltOutlined />
                            性能统计
                        </h3>
                        <div class="performance-stats">
                            <div class="perf-item">
                                <span class="perf-label">平均耗时</span>
                                <span class="perf-value">{{ formatDuration(stats?.avgDurationMs ?? 0) }}</span>
                            </div>

                            <a-divider />
                            <div class="perf-item">
                                <span class="perf-label">总创作数</span>
                                <span class="perf-value">{{ stats?.totalCount ?? 0 }}</span>
                            </div>
                        </div>
                    </a-card>
                </div>
                <!-- 用户统计 -->
                <div class="charts-grid">
                    <a-card :bordered="false" class="chart-card">
                        <h3 class="chart-title">
                            <TeamOutlined />
                            用户分析
                        </h3>
                        <div ref="userChartRef" class="chart-container"></div>
                    </a-card>
                    <a-card :bordered="false" class="chart-card">
                        <h3 class="chart-title">
                            <CrownOutlined />
                            配额使用情况
                        </h3>
                        <div ref="quotaChartRef" class="chart-container"></div>
                    </a-card>
                </div>
            </a-spin>
        </div>
    </div>
</template>

<script setup lang="ts">
import { getStatistics } from '@/api/statisticsController'
import {
    BarChartOutlined,
    CheckCircleOutlined,
    CrownOutlined,
    FileTextOutlined,
    LineChartOutlined,
    ReloadOutlined,
    RiseOutlined,
    TeamOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { onMounted, onUnmounted, ref } from 'vue'

const loading = ref(false)
const stats = ref<API.StatisticsVO | null>(null)

// ECharts 实例
const trendChartRef = ref<HTMLElement>()
const userChartRef = ref<HTMLElement>()
const quotaChartRef = ref<HTMLElement>()
let trendChart: ECharts | null = null
let userChart: ECharts | null = null
let quotaChart: ECharts | null = null

// 加载数据
const loadData = async () => {
    loading.value = true
    try {
        const res = await getStatistics()
        stats.value = res.data.data || null

        // 渲染图表
        setTimeout(() => {
            renderTrendChart()
            renderUserChart()
            renderQuotaChart()
        }, 100)
    } catch (error) {
        message.error((error as Error).message || '加载数据失败')
    } finally {
        loading.value = false
    }
}

// 渲染创作趋势图
const renderTrendChart = () => {
    if (!trendChartRef.value || !stats.value) return

    if (!trendChart) {
        trendChart = echarts.init(trendChartRef.value)
    }

    const option: EChartsOption = {
        tooltip: {
            trigger: 'axis'
        },
        grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true
        },
        xAxis: {
            type: 'category',
            data: ['今日', '本周', '本月', '总计'],
            axisLine: {
                lineStyle: {
                    color: '#E2E8F0'
                }
            },
            axisLabel: {
                color: '#64748B'
            }
        },
        yAxis: {
            type: 'value',
            axisLine: {
                show: false
            },
            splitLine: {
                lineStyle: {
                    color: '#F1F5F9'
                }
            },
            axisLabel: {
                color: '#64748B'
            }
        },
        series: [
            {
                name: '创作数量',
                type: 'bar',
                data: [
                    stats.value.todayCount ?? 0,
                    stats.value.weekCount ?? 0,
                    stats.value.monthCount ?? 0,
                    stats.value.totalCount ?? 0
                ],
                itemStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: '#4ADE80' },
                        { offset: 1, color: '#22C55E' }
                    ]),
                    borderRadius: [4, 4, 0, 0]
                },
                barWidth: '40%'
            }
        ]
    }

    trendChart.setOption(option)
}

// 渲染用户分析图
const renderUserChart = () => {
    if (!userChartRef.value || !stats.value) return

    if (!userChart) {
        userChart = echarts.init(userChartRef.value)
    }

    const option: EChartsOption = {
        tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} ({d}%)'
        },
        legend: {
            orient: 'vertical',
            right: '10%',
            top: 'center',
            textStyle: {
                color: '#64748B'
            }
        },
        series: [
            {
                name: '用户分布',
                type: 'pie',
                radius: ['40%', '70%'],
                avoidLabelOverlap: false,
                itemStyle: {
                    borderRadius: 8,
                    borderColor: '#fff',
                    borderWidth: 2
                },
                label: {
                    show: false
                },
                emphasis: {
                    label: {
                        show: true,
                        fontSize: 16,
                        fontWeight: 'bold'
                    }
                },
                data: [
                    {
                        value: stats.value.vipUserCount ?? 0,
                        name: 'VIP 会员',
                        itemStyle: { color: '#22C55E' }
                    },
                    {
                        value: stats.value.activeUserCount ?? 0,
                        name: '活跃用户',
                        itemStyle: { color: '#3B82F6' }
                    },
                    {
                        value: (stats.value.totalUserCount ?? 0) - (stats.value.activeUserCount ?? 0) - (stats.value.vipUserCount ?? 0),
                        name: '其他用户',
                        itemStyle: { color: '#94A3B8' }
                    }
                ]
            }
        ]
    }

    userChart.setOption(option)
}

// 渲染配额使用图
const renderQuotaChart = () => {
    if (!quotaChartRef.value || !stats.value) return

    if (!quotaChart) {
        quotaChart = echarts.init(quotaChartRef.value)
    }

    const totalQuota = (stats.value.totalUserCount ?? 0) * 5
    const usedQuota = stats.value.quotaUsed ?? 0
    const remainingQuota = Math.max(0, totalQuota - usedQuota)

    const option: EChartsOption = {
        tooltip: {
            trigger: 'item'
        },
        series: [
            {
                name: '配额统计',
                type: 'pie',
                radius: '70%',
                center: ['50%', '50%'],
                data: [
                    {
                        value: usedQuota,
                        name: '已使用',
                        itemStyle: { color: '#EF4444' }
                    },
                    {
                        value: remainingQuota,
                        name: '剩余',
                        itemStyle: { color: '#22C55E' }
                    }
                ],
                emphasis: {
                    itemStyle: {
                        shadowBlur: 10,
                        shadowOffsetX: 0,
                        shadowColor: 'rgba(0, 0, 0, 0.5)'
                    }
                },
                label: {
                    formatter: '{b}: {c}'
                }
            }
        ]
    }

    quotaChart.setOption(option)
}

// 格式化耗时
const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(1)}s`
}

// 响应式处理
const handleResize = () => {
    trendChart?.resize()
    userChart?.resize()
    quotaChart?.resize()
}

onMounted(() => {
    loadData()
    window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    trendChart?.dispose()
    userChart?.dispose()
    quotaChart?.dispose()
})
</script>

<style scoped>
.statistics-page {
    min-height: calc(100vh - 64px);
    padding-bottom: 64px;
}

.page-header {
    border-bottom: 1px solid var(--color-border);
    background: var(--color-surface);
}

.header-container,
.container {
    width: min(var(--content-width), calc(100% - 48px));
    margin: 0 auto;
}

.header-container {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 36px 0 30px;
}

.page-title {
    margin: 0;
    color: var(--color-text);
    font-size: 28px;
    line-height: 1.3;
}

.page-subtitle {
    margin: 8px 0 0;
    color: var(--color-text-secondary);
}

.refresh-btn {
    flex: none;
}

.container {
    padding-top: 28px;
}

.stats-grid,
.charts-grid {
    display: grid;
    gap: 20px;
}

.stats-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    margin-bottom: 20px;
}

.charts-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-bottom: 20px;
}

.stat-card,
.chart-card {
    border: 1px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-surface);
    box-shadow: var(--shadow-sm);
}

.stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;
    padding: 22px;
}

.stat-icon {
    display: grid;
    flex: 0 0 44px;
    width: 44px;
    height: 44px;
    place-items: center;
    border-radius: 8px;
    font-size: 22px;
}

.stat-content {
    min-width: 0;
}

.stat-label,
.perf-label {
    color: var(--color-text-secondary);
    font-size: 14px;
}

.stat-value {
    margin-top: 4px;
    color: var(--color-text);
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
}

.chart-card :deep(.ant-card-body) {
    padding: 24px;
}

.chart-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 20px;
    color: var(--color-text);
    font-size: 17px;
}

.chart-title :deep(.anticon) {
    color: var(--color-primary);
}

.chart-container {
    width: 100%;
    height: 300px;
}

.performance-stats {
    display: flex;
    height: 300px;
    flex-direction: column;
    justify-content: center;
}

.perf-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 18px 8px;
}

.perf-value {
    color: var(--color-text);
    font-size: 24px;
    font-weight: 700;
}

@media (max-width: 980px) {
    .stats-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}

@media (max-width: 720px) {

    .header-container,
    .container {
        width: calc(100% - 32px);
    }

    .header-container {
        align-items: flex-start;
        padding: 28px 0 24px;
    }

    .page-title {
        font-size: 24px;
    }

    .stats-grid,
    .charts-grid {
        grid-template-columns: 1fr;
    }

    .stat-card {
        padding: 18px;
    }

    .chart-container,
    .performance-stats {
        height: 260px;
    }
}

@media (max-width: 420px) {
    .header-container {
        flex-direction: column;
    }

    .stats-grid {
        grid-template-columns: 1fr;
    }
}
</style>
