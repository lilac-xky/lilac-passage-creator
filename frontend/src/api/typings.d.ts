declare namespace API {
  type DeleteRequest = {
    /** id */
    id?: number;
  };

  type LoginUserVO = {
    /** id */
    id?: number;
    /** 用户账号 */
    userAccount?: string;
    /** 用户名 */
    userName?: string;
    /** 用户头像 */
    userAvatar?: string;
    /** 用户简介 */
    userProfile?: string;
    /** 用户角色：user-普通用户 admin-管理员 */
    userRole?: string;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type PageLoginUserVO = {
    records?: LoginUserVO[];
    pageNumber?: number;
    pageSize?: number;
    maxPageSize?: number;
    totalPage?: number;
    totalRow?: number;
    optimizeCountQuery?: boolean;
  };

  type ResultBoolean = {
    code?: number;
    msg?: string;
    data?: boolean;
  };

  type ResultLoginUserVO = {
    code?: number;
    msg?: string;
    data?: LoginUserVO;
  };

  type ResultLong = {
    code?: number;
    msg?: string;
    data?: number;
  };

  type ResultPageLoginUserVO = {
    code?: number;
    msg?: string;
    data?: PageLoginUserVO;
  };

  type UserAddRequest = {
    userAccount?: string;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserLoginRequest = {
    /** 账号 */
    userAccount?: string;
    /** 密码 */
    userPassword?: string;
  };

  type UserQueryRequest = {
    /** 当前页 */
    current?: number;
    /** 页面大小 */
    pageSize?: number;
    /** 排序字段 */
    sortField?: string;
    /** 排序顺序（默认：升序） */
    sortOrder?: string;
    /** id */
    id?: number;
    /** 账号 */
    userAccount?: string;
    /** 用户昵称 */
    userName?: string;
    /** 用户简介 */
    userProfile?: string;
  };

  type UserRegisterRequest = {
    /** 账号 */
    userAccount?: string;
    /** 密码 */
    userPassword?: string;
    /** 校验密码 */
    checkPassword?: string;
  };

  type UserUpdateRequest = {
    /** id */
    id?: number;
    /** 账号 */
    userAccount?: string;
    /** 用户昵称 */
    userName?: string;
    /** 用户头像 */
    userAvatar?: string;
    /** 用户简介 */
    userProfile?: string;
    /** 用户角色：user/admin */
    userRole?: string;
  };
}
