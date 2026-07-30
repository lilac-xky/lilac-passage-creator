<template>
    <main class="vip-page">
        <section class="intro-section">
            <div class="intro-content">
                <div class="eyebrow">
                    <CrownOutlined /> Lilac 永久会员
                </div>
                <h1>一次升级，持续释放创作力</h1>
                <p>解除创作次数限制，使用完整的 AI 写作与配图能力。无需订阅续费，一次购买永久有效。</p>
                <div v-if="isVip" class="member-state">
                    <CheckCircleFilled /> 你的永久会员权益已生效
                </div>
            </div>
        </section>

        <section class="purchase-section">
            <div class="section-inner purchase-layout">
                <div class="benefits-panel">
                    <span class="section-kicker">会员权益</span>
                    <h2>专注内容，其他交给 Lilac</h2>
                    <div class="benefit-grid">
                        <article v-for="feature in features" :key="feature.title" class="benefit-item">
                            <div class="benefit-icon">
                                <component :is="feature.icon" />
                            </div>
                            <div>
                                <h3>{{ feature.title }}</h3>
                                <p>{{ feature.desc }}</p>
                            </div>
                        </article>
                    </div>
                </div>

                <aside class="pricing-card">
                    <div class="price-label">永久会员</div>
                    <div class="price-row"><span class="currency">$</span><strong>199</strong><span
                            class="period">一次付费</span></div>
                    <div class="original-price">原价 <del>$299</del> · 限时优惠</div>
                    <div class="divider" />
                    <ul class="price-features">
                        <li v-for="item in pricingFeatures" :key="item">
                            <CheckOutlined /> {{ item }}
                        </li>
                    </ul>
                    <a-button class="purchase-button" type="primary" size="large" block :loading="purchasing"
                        :disabled="isVip" @click="handlePurchase">
                        <CrownOutlined />
                        {{ isVip ? '永久会员已生效' : '立即升级' }}
                    </a-button>
                    <p class="payment-note">
                        <SafetyCertificateOutlined /> 由 Stripe 提供安全支付服务
                    </p>
                </aside>
            </div>
        </section>

        <section class="faq-section">
            <div class="section-inner faq-layout">
                <div><span class="section-kicker">常见问题</span>
                    <h2>购买前，你可能想了解</h2>
                </div>
                <a-collapse v-model:active-key="activeFaq" ghost accordion>
                    <a-collapse-panel v-for="(faq, index) in faqs" :key="index" :header="faq.question">
                        <p>{{ faq.answer }}</p>
                    </a-collapse-panel>
                </a-collapse>
            </div>
        </section>
    </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
    BgColorsOutlined,
    CheckCircleFilled,
    CheckOutlined,
    CrownOutlined,
    FileDoneOutlined,
    PictureOutlined,
    RocketOutlined,
    SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { createVipPaymentSession } from '@/api/paymentController'
import { hasVipAccess } from '@/constant/user'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const purchasing = ref(false)
const activeFaq = ref<string[]>([])

const isVip = computed(() => hasVipAccess(loginUserStore.loginUser.userRole))

const features = [
    { icon: RocketOutlined, title: '不限次数创作', desc: '持续生成文章，不再受普通账户创作额度限制。' },
    { icon: PictureOutlined, title: 'AI 智能配图', desc: '根据文章内容生成匹配的视觉素材，让表达更完整。' },
    { icon: FileDoneOutlined, title: '完整创作流程', desc: '从选题、标题、大纲到正文，在同一工作流中完成。' },
    { icon: BgColorsOutlined, title: '高级写作能力', desc: '使用更多内容风格与精细调整能力，适配不同场景。' },
]

const pricingFeatures = ['永久会员资格', '无限文章创作额度', 'AI 智能配图能力', '后续会员功能持续开放']

const faqs = [
    { question: '永久会员真的不需要续费吗？', answer: '是的。购买成功后会员资格永久有效，不会自动续费，也不会产生周期性扣款。' },
    { question: '支付成功后多久生效？', answer: '通常会立即生效。支付完成返回本站后，系统会自动刷新你的账户状态。' },
    { question: '支持哪些支付方式？', answer: '支付由 Stripe 安全处理，具体可用方式会根据你所在地区显示在结账页面。' },
    { question: '会员权益会继续更新吗？', answer: '会。后续上线并纳入永久会员范围的功能，将自动向你的账户开放。' },
]

onMounted(async () => {
    if (route.query.success === 'true') {
        try {
            await loginUserStore.fetchLoginUser()
            Modal.success({
                title: '支付成功',
                content: '永久会员权益已生效，现在可以使用完整创作能力。',
                okText: '开始创作',
                onOk: () => router.push('/create'),
            })
        } finally {
            await router.replace('/vip')
        }
    } else if (route.query.cancelled === 'true') {
        message.info('支付已取消，未产生扣款')
        await router.replace('/vip')
    }
})

const handlePurchase = async () => {
    if (!loginUserStore.loginUser.id) {
        message.warning('请先登录后再升级会员')
        await router.push({ path: '/user/login', query: { redirect: '/vip' } })
        return
    }
    if (isVip.value || purchasing.value) return

    purchasing.value = true
    try {
        const res = await createVipPaymentSession()
        if (res.data.code !== 200 || !res.data.data) {
            message.error(res.data.msg || '创建支付订单失败')
            return
        }
        window.location.assign(res.data.data)
    } catch (error) {
        console.error('创建支付订单失败:', error)
        message.error('暂时无法发起支付，请稍后重试')
    } finally {
        purchasing.value = false
    }
}
</script>

<style scoped>
.vip-page {
    min-height: calc(100vh - 64px);
    color: var(--color-text);
    background: #fff;
}

.section-inner {
    width: min(1120px, calc(100% - 40px));
    margin: 0 auto;
}

.intro-section {
    padding: 72px 20px 130px;
    color: #fff;
    background: #153b29;
}

.intro-content {
    width: min(760px, 100%);
    margin: 0 auto;
    text-align: center;
}

.eyebrow {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #a8e1c2;
    font-size: 14px;
    font-weight: 650;
}

.intro-content h1 {
    margin: 18px 0 16px;
    color: #fff;
    font-size: 44px;
    line-height: 1.2;
    letter-spacing: 0;
}

.intro-content>p {
    max-width: 650px;
    margin: 0 auto;
    color: #bdd0c5;
    font-size: 17px;
    line-height: 1.8;
}

.member-state {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 24px;
    padding: 8px 14px;
    border: 1px solid #4f8368;
    border-radius: 6px;
    color: #cbf1dc;
    background: #204c37;
}

.purchase-section {
    padding: 0 0 84px;
    background: var(--color-page);
}

.purchase-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 56px;
    align-items: start;
}

.benefits-panel {
    padding-top: 64px;
}

.section-kicker {
    color: var(--color-primary);
    font-size: 13px;
    font-weight: 700;
}

.benefits-panel h2,
.faq-layout h2 {
    margin: 9px 0 32px;
    color: var(--color-text);
    font-size: 29px;
    line-height: 1.35;
    letter-spacing: 0;
}

.benefit-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 34px 30px;
}

.benefit-item {
    display: flex;
    gap: 15px;
}

.benefit-icon {
    display: grid;
    flex: 0 0 42px;
    height: 42px;
    place-items: center;
    border-radius: 7px;
    color: var(--color-primary);
    background: var(--color-primary-soft);
    font-size: 20px;
}

.benefit-item h3 {
    margin: 1px 0 7px;
    font-size: 16px;
}

.benefit-item p {
    margin: 0;
    color: var(--color-text-secondary);
    line-height: 1.65;
}

.pricing-card {
    margin-top: -72px;
    padding: 30px;
    border: 1px solid var(--color-border);
    border-top: 4px solid var(--color-primary);
    border-radius: 8px;
    background: #fff;
    box-shadow: var(--shadow-md);
}

.price-label {
    color: var(--color-text-secondary);
    font-weight: 650;
}

.price-row {
    display: flex;
    align-items: baseline;
    margin-top: 14px;
}

.currency {
    align-self: flex-start;
    margin-top: 10px;
    font-size: 21px;
    font-weight: 700;
}

.price-row strong {
    margin: 0 9px 0 3px;
    font-size: 52px;
    line-height: 1;
}

.period,
.original-price {
    color: var(--color-text-muted);
    font-size: 13px;
}

.original-price {
    margin-top: 10px;
}

.divider {
    height: 1px;
    margin: 24px 0;
    background: var(--color-border-light);
}

.price-features {
    display: grid;
    gap: 14px;
    margin: 0 0 26px;
    padding: 0;
    list-style: none;
    color: var(--color-text);
}

.price-features :deep(.anticon) {
    margin-right: 8px;
    color: var(--color-primary);
}

.purchase-button {
    height: 46px;
    background: var(--color-primary);
    box-shadow: none;
    font-weight: 650;
}

.purchase-button:not(:disabled):hover {
    background: var(--color-primary-hover);
}

.payment-note {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 6px;
    margin: 15px 0 0;
    color: var(--color-text-muted);
    font-size: 12px;
}

.faq-section {
    padding: 76px 0;
    border-top: 1px solid var(--color-border-light);
}

.faq-layout {
    display: grid;
    grid-template-columns: 300px minmax(0, 1fr);
    gap: 80px;
}

.faq-layout :deep(.ant-collapse-header) {
    padding: 17px 0 !important;
    font-size: 16px;
    font-weight: 600;
}

.faq-layout :deep(.ant-collapse-content-box) {
    padding: 0 30px 18px 0 !important;
}

.faq-layout :deep(.ant-collapse-content-box p) {
    margin: 0;
    color: var(--color-text-secondary);
    line-height: 1.7;
}

@media (max-width: 820px) {
    .intro-section {
        padding: 56px 20px 112px;
    }

    .intro-content h1 {
        font-size: 36px;
    }

    .purchase-layout {
        grid-template-columns: 1fr;
        gap: 0;
    }

    .pricing-card {
        grid-row: 1;
        width: min(440px, 100%);
        margin: -72px auto 0;
    }

    .benefits-panel {
        padding-top: 56px;
    }

    .faq-layout {
        grid-template-columns: 1fr;
        gap: 12px;
    }
}

@media (max-width: 560px) {
    .section-inner {
        width: min(100% - 28px, 1120px);
    }

    .intro-section {
        padding: 44px 16px 100px;
    }

    .intro-content h1 {
        font-size: 30px;
    }

    .intro-content>p {
        font-size: 15px;
    }

    .pricing-card {
        padding: 24px;
    }

    .benefit-grid {
        grid-template-columns: 1fr;
        gap: 26px;
    }

    .benefits-panel h2,
    .faq-layout h2 {
        font-size: 25px;
    }

    .purchase-section {
        padding-bottom: 62px;
    }

    .faq-section {
        padding: 58px 0;
    }
}
</style>
