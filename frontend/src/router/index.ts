import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import UserLogin from '@/views/user/UserLogin.vue'
import UserRegister from '@/views/user/UserRegister.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: Home,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLogin,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegister,
    },
    {
      path: '/admin/userManager',
      name: '用户管理',
      component: () => import('@/views/admin/UserManager.vue'),
      meta: {
        requiresAuth: true,
        requiresAdmin: true,
      },
    },
    {
      path: '/create',
      name: '创作文章',
      component: () => import('@/views/article/ArticleCreate.vue'),
    },
    {
      path: '/article/list',
      name: '文章列表',
      component: () => import('@/views/article/ArticleList.vue'),
    },
    {
      path: '/article/:taskId',
      name: '文章详情',
      component: () => import('@/views/article/ArticleDetail.vue'),
    },
    {
      path: '/vip',
      name: '会员购买',
      component: () => import('@/views/Vip.vue'),
    },
  ],
})

export default router
