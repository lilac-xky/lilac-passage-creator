<template>
    <div class="title-selecting-stage">
        <div class="stage-header">
            <h2 class="stage-title">选择标题方案</h2>
            <p class="stage-subtitle">AI 为您生成了以下标题，请选择一个或自定义</p>
        </div>

        <a-radio-group v-model:value="selectedIndex" class="title-options">
            <div v-for="(option, index) in titleOptions" :key="index" class="title-option">
                <a-radio :value="index">
                    <div class="title-content">
                        <div class="title-main">{{ option.mainTitle }}</div>
                        <div class="title-sub">{{ option.subTitle }}</div>
                    </div>
                </a-radio>
            </div>

            <div class="title-option custom">
                <a-radio :value="-1">
                    <div class="title-content">
                        <div class="title-main">自定义标题</div>
                    </div>
                </a-radio>

                <div v-if="selectedIndex === -1" class="custom-inputs">
                    <a-input v-model:value="customMainTitle" placeholder="输入主标题" class="custom-input" />
                    <a-input v-model:value="customSubTitle" placeholder="输入副标题" class="custom-input" />
                </div>
            </div>
        </a-radio-group>

        <div class="description-section">
            <label class="section-label">补充描述（可选）</label>
            <p class="section-tip">补充您对文章的期望、重点强调的内容等</p>
            <a-textarea v-model:value="userDescription" placeholder="例如：请重点强调技术原理，用通俗的语言讲解..." :rows="4"
                :maxlength="500" show-count class="description-textarea" />
        </div>

        <div class="actions">
            <a-button type="primary" size="large" :loading="loading" :disabled="!canConfirm" @click="handleConfirm"
                class="confirm-btn">
                <template #icon>
                    <CheckOutlined />
                </template>
                确认并生成大纲
            </a-button>
        </div>
    </div>
</template>

<script setup lang="ts">
import { CheckOutlined } from '@ant-design/icons-vue'
import { computed, ref } from 'vue'


interface TitleOption {
    mainTitle: string
    subTitle: string
}

interface Props {
    titleOptions: TitleOption[]
    loading?: boolean
}

interface Emits {
    (e: 'confirm', data: {
        mainTitle: string
        subTitle: string
        userDescription: string
    }): void
}

const props = withDefaults(defineProps<Props>(), {
    loading: false
})

const emit = defineEmits<Emits>()

const selectedIndex = ref<number>(0)
const customMainTitle = ref('')
const customSubTitle = ref('')
const userDescription = ref('')

const canConfirm = computed(() => {
    if (selectedIndex.value === -1) {
        return customMainTitle.value.trim() && customSubTitle.value.trim()
    }
    return selectedIndex.value >= 0 && selectedIndex.value < props.titleOptions.length
})

const handleConfirm = () => {
    let mainTitle = ''
    let subTitle = ''

    if (selectedIndex.value === -1) {
        mainTitle = customMainTitle.value
        subTitle = customSubTitle.value
    } else {
        const selected = props.titleOptions[selectedIndex.value]
        if (!selected) return
        mainTitle = selected.mainTitle
        subTitle = selected.subTitle
    }

    emit('confirm', {
        mainTitle,
        subTitle,
        userDescription: userDescription.value
    })
}
</script>

<style scoped>
.title-selecting-stage {
    max-width: 760px;
    margin: 0 auto;
}

.stage-header {
    margin-bottom: 24px;
}

.stage-title {
    margin: 0 0 8px;
    font-size: 24px;
    color: var(--text-main);
}

.stage-subtitle,
.section-tip {
    margin: 0;
    color: var(--text-sub);
}

.title-options {
    display: flex;
    flex-direction: column;
    gap: 12px;
    width: 100%;
}

.title-option {
    padding: 18px;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    transition: border-color .2s, background-color .2s;
}

.title-option:hover {
    border-color: var(--primary-color);
    background: var(--primary-color-light);
}

.title-option :deep(.ant-radio-wrapper) {
    width: 100%;
    align-items: flex-start;
}

.title-content {
    padding-left: 4px;
}

.title-main {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-main);
}

.title-sub {
    margin-top: 6px;
    color: var(--text-sub);
}

.custom-inputs {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    margin: 16px 0 0 24px;
}

.description-section {
    margin-top: 24px;
}

.section-label {
    display: block;
    margin-bottom: 6px;
    font-weight: 600;
    color: var(--text-main);
}

.description-textarea {
    margin-top: 12px;
}

.actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 24px;
}

@media (max-width: 600px) {
    .custom-inputs {
        grid-template-columns: 1fr;
    }

    .confirm-btn {
        width: 100%;
    }
}
</style>
