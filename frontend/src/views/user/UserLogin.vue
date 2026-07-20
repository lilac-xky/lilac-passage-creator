<template>
    <div id="userLoginPage">
        <div class="form-card">
            <h2 class="form-title">欢迎回来</h2>

            <p class="form-subtitle">登录您的账号继续创作</p>

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
                    { min: 6, message: '密码长度不能小于 6 位' },
                ]">
                    <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large">
                        <template #prefix>
                            <LockOutlined />
                        </template>
                    </a-input-password>
                </a-form-item>

                <a-form-item>
                    <a-button type="primary" html-type="submit" size="large" block>
                        登录
                    </a-button>
                </a-form-item>
            </a-form>

            <div class="form-footer">
                <span>还没有账号？</span>
                <RouterLink to="/user/register">立即注册</RouterLink>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'

// 表单数据
const formState = reactive<API.UserLoginRequest>({
    userAccount: '',
    userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 登录提交
const handleSubmit = async (values: any) => {
    const res = await userLogin(values)
    if (res.data.code === 200 && res.data.data) {
        await loginUserStore.fetchLoginUser()
        message.success('登录成功')
        router.push({
            path: '/',
            replace: true,
        })
    } else {
        message.error('登录失败，' + res.data.msg)
    } 
}
</script>