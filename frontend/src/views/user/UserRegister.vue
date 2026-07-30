<template>
    <div id="userRegisterPage">
        <div class="auth-shell">
            <aside class="auth-brand-panel">
                <RouterLink class="auth-brand" to="/"><span>L</span> Lilac Passage</RouterLink>
                <div class="brand-message">
                    <div class="brand-kicker">
                        <BulbOutlined /> AI 智能文章创作
                    </div>
                    <h1>从今天开始，<br>更轻松地完成创作。</h1>
                    <p>分阶段确认创作方向，在保留掌控感的同时，让 AI 提升内容产出效率。</p>
                </div>
                <div class="brand-flow">
                    <div>
                        <CheckOutlined /><span><b>确定方向</b><small>输入选题，选择标题</small></span>
                    </div>
                    <div>
                        <CheckOutlined /><span><b>搭建结构</b><small>生成并调整内容大纲</small></span>
                    </div>
                    <div>
                        <EditOutlined /><span><b>完成创作</b><small>生成正文与智能配图</small></span>
                    </div>
                </div>
                <p class="brand-note">一段完整的创作旅程，从一个账号开始。</p>
            </aside>

            <div class="auth-form-panel">
                <div class="form-card">
                    <RouterLink class="mobile-brand" to="/"><span>L</span> Lilac Passage</RouterLink>
                    <div class="form-heading">
                        <span>创建账户</span>
                        <h2 class="form-title">开始创作</h2>
                        <p class="form-subtitle">注册并开启您的 AI 创作之旅</p>
                    </div>
                    <a-form :model="formState" @finish="handleSubmit">
                        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
                            <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large">
                                <template #prefix>
                                    <UserOutlined />
                                </template>
                            </a-input>
                        </a-form-item>
                        <a-form-item name="userPassword" :rules="[
                            { required: true, message: '请输入密码' },
                            { min: 6, message: '密码不能小于 6 位' },
                        ]">
                            <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large">
                                <template #prefix>
                                    <LockOutlined />
                                </template>
                            </a-input-password>
                        </a-form-item>

                        <a-form-item name="checkPassword" :rules="[
                            { required: true, message: '请确认密码' },
                            { validator: validateCheckPassword },
                        ]">
                            <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" size="large">
                                <template #prefix>
                                    <SafetyOutlined />
                                </template>
                            </a-input-password>
                        </a-form-item>

                        <a-form-item>
                            <a-button type="primary" html-type="submit" size="large" block>
                                注册
                            </a-button>
                        </a-form-item>
                    </a-form>
                    <div class="form-footer">
                        <span>已有账号？</span>
                        <RouterLink to="/user/login">立即登录</RouterLink>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { userRegister } from '@/api/userController'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
    BulbOutlined,
    CheckOutlined,
    EditOutlined,
    LockOutlined,
    SafetyOutlined,
    UserOutlined,
} from '@ant-design/icons-vue'


const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
    userAccount: '',
    userPassword: '',
    checkPassword: '',
})

// 验证确认密码
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
    if (value && value !== formState.userPassword) {
        callback(new Error('两次输入密码不一致'))
    } else {
        callback()
    }
}

// 注册提交
const handleSubmit = async (values: API.UserRegisterRequest) => {
    const res = await userRegister(values)
    if (res.data.code === 200) {
        message.success('注册成功')
        router.push({
            path: '/user/login',
            replace: true,
        })
    } else {
        message.error('注册失败，' + res.data.msg)
    }
}
</script>

<style scoped src="./auth.css"></style>
