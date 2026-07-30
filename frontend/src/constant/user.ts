// 用户角色
export const USER_ROLE_ADMIN = 'admin'
export const USER_ROLE_USER = 'user'
export const USER_ROLE_VIP = 'vip'

// 管理员拥有全部会员功能权限。
export const hasVipAccess = (userRole?: string) =>
  userRole === USER_ROLE_VIP || userRole === USER_ROLE_ADMIN

// 默认值
export const DEFAULT_USERNAME = '未登录'

// 配额
export const DEFAULT_QUOTA = 5
