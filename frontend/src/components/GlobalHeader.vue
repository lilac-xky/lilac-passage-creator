<template>
    <a-layout-header class="global-header">
        <div class="header-inner">
            <RouterLink class="brand" to="/" aria-label="返回首页">
                <span class="brand-mark">L</span>
                <span class="brand-name">Lilac Passage</span>
            </RouterLink>

            <a-menu class="desktop-nav" mode="horizontal" :selected-keys="selectedKeys" :items="menuItems"
                @click="handleMenuClick" />

            <div class="header-actions">
                <RouterLink v-if="loginUserStore.loginUser.id" class="vip-link" to="/vip">
                    <CrownOutlined />
                    <span>{{ isVip ? '永久会员' : '升级会员' }}</span>
                </RouterLink>

                <a-dropdown v-if="loginUserStore.loginUser.id" placement="bottomRight">
                    <button class="user-trigger" type="button">
                        <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="34">
                            {{ userInitial }}
                        </a-avatar>
                        <span class="user-name">{{ loginUserStore.loginUser.userName || '用户' }}</span>
                        <DownOutlined class="down-icon" />
                    </button>
                    <template #overlay>
                        <a-menu>
                            <a-menu-item key="vip" @click="router.push('/vip')">
                                <CrownOutlined />
                                {{ isVip ? '查看会员权益' : '升级永久会员' }}
                            </a-menu-item>
                            <a-menu-divider />
                            <a-menu-item key="logout" danger @click="doLogout">
                                <LogoutOutlined />
                                退出登录
                            </a-menu-item>
                        </a-menu>
                    </template>
                </a-dropdown>

                <a-button v-else type="primary" @click="goToLogin">登录</a-button>

                <a-button class="mobile-menu-button" type="text" aria-label="打开导航" @click="drawerOpen = true">
                    <MenuOutlined />
                </a-button>
            </div>
        </div>

        <a-drawer v-model:open="drawerOpen" title="导航" placement="right" :width="280">
            <a-menu mode="inline" :selected-keys="selectedKeys" :items="menuItems" @click="handleMobileMenuClick" />
        </a-drawer>
    </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
    CrownOutlined,
    DownOutlined,
    EditOutlined,
    FileTextOutlined,
    HomeOutlined,
    LogoutOutlined,
    MenuOutlined,
    TeamOutlined,
} from '@ant-design/icons-vue'
import { userLogout } from '@/api/userController'
import { hasVipAccess, USER_ROLE_ADMIN } from '@/constant/user'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const drawerOpen = ref(false)

const isVip = computed(() => hasVipAccess(loginUserStore.loginUser.userRole))
const userInitial = computed(() => (loginUserStore.loginUser.userName || 'U').slice(0, 1).toUpperCase())
const selectedKeys = computed(() => {
    if (route.path.startsWith('/article/')) return ['/article/list']
    return [route.path]
})

const baseMenuItems: NonNullable<MenuProps['items']> = [
    { key: '/', icon: () => h(HomeOutlined), label: '首页' },
    { key: '/create', icon: () => h(EditOutlined), label: '开始创作' },
    { key: '/article/list', icon: () => h(FileTextOutlined), label: '我的文章' },
    { key: '/admin/userManager', icon: () => h(TeamOutlined), label: '用户管理' },
]

const menuItems = computed<MenuProps['items']>(() =>
    baseMenuItems.filter((item) => item?.key !== '/admin/userManager' || loginUserStore.loginUser.userRole === USER_ROLE_ADMIN),
)

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    void router.push(String(key))
}

const handleMobileMenuClick: MenuProps['onClick'] = (info) => {
    drawerOpen.value = false
    handleMenuClick?.(info)
}

const goToLogin = () => {
    void router.push({ path: '/user/login', query: { redirect: route.fullPath } })
}

const doLogout = async () => {
    try {
        const res = await userLogout()
        if (res.data.code !== 200) {
            message.error(res.data.msg || '退出登录失败')
            return
        }
        loginUserStore.setLoginUser({ userName: '未登录' })
        message.success('已退出登录')
        await router.push('/user/login')
    } catch {
        message.error('退出登录失败，请稍后重试')
    }
}
</script>

<style scoped>
.global-header {
    position: sticky;
    top: 0;
    z-index: 100;
    height: 64px;
    padding: 0 24px;
    background: rgba(255, 255, 255, 0.94);
    border-bottom: 1px solid var(--color-border);
    backdrop-filter: blur(12px);
}

.header-inner {
    display: flex;
    align-items: center;
    width: min(1200px, 100%);
    height: 100%;
    margin: 0 auto;
}

.brand {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 10px;
    color: var(--color-text);
}

.brand-mark {
    display: grid;
    width: 34px;
    height: 34px;
    place-items: center;
    border-radius: 8px;
    color: #fff;
    background: var(--color-primary);
    font-size: 19px;
    font-weight: 700;
}

.brand-name {
    font-size: 17px;
    font-weight: 650;
    white-space: nowrap;
}

.desktop-nav {
    flex: 1;
    min-width: 0;
    margin-left: 36px;
    border-bottom: 0;
    background: transparent;
}

.header-actions,
.user-trigger,
.vip-link {
    display: flex;
    align-items: center;
}

.header-actions {
    gap: 12px;
}

.vip-link {
    gap: 6px;
    min-height: 34px;
    padding: 0 11px;
    border: 1px solid #d3a742;
    border-radius: 6px;
    color: #725817;
    background: #fffaf0;
    font-size: 13px;
    font-weight: 600;
}

.user-trigger {
    gap: 8px;
    padding: 3px 4px;
    border: 0;
    color: var(--color-text);
    background: transparent;
    cursor: pointer;
}

.user-name {
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.down-icon {
    color: var(--color-text-muted);
    font-size: 11px;
}

.mobile-menu-button {
    display: none;
    width: 38px;
    height: 38px;
    font-size: 20px;
}

@media (max-width: 820px) {
    .global-header {
        padding: 0 16px;
    }

    .desktop-nav {
        display: none;
    }

    .header-actions {
        margin-left: auto;
    }

    .mobile-menu-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }

    .user-name,
    .down-icon {
        display: none;
    }
}

@media (max-width: 520px) {
    .brand-name {
        display: none;
    }

    .vip-link span {
        display: none;
    }

    .vip-link {
        width: 36px;
        justify-content: center;
        padding: 0;
    }
}
</style>
