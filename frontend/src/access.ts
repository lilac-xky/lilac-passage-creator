import { message } from "ant-design-vue"
import router from "./router"
import { useLoginUserStore } from "./stores/loginUser"
import { USER_ROLE_ADMIN } from "./constant/user"

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  // 首次加载时，等后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  // 判断目标路由是否需要登录
  if (to.meta.requiresAuth) {
    // 用户未登录（无 userRole 或为空）
    if (!loginUser || !loginUser.userRole) {
      message.warning('请先登录');
      next(`/user/login?redirect=${to.fullPath}`);
      return;
    }
    // 如果需要管理员权限，检查角色
    if (to.meta.requiresAdmin && loginUser.userRole !== USER_ROLE_ADMIN) {
      message.error('没有权限');
      next('/');
      return;
    }
  }
  
  next()
})
