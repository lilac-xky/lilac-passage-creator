declare namespace API {
  type ArticleCreateRequest = {
    /** 选题 */
    topic?: string;
  };

  type ArticleQueryRequest = {
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
    /** 用户id */
    userId?: number;
    /** 状态 */
    status?: string;
  };

  type ArticleVO = {
    /** id */
    id?: number;
    /** 任务ID */
    taskId?: string;
    /** 用户ID */
    userId?: number;
    /** 选题 */
    topic?: string;
    /** 标题 */
    mainTitle?: string;
    /** 副标题 */
    subTitle?: string;
    /** 概要 */
    outline?: string;
    /** 内容 */
    content?: string;
    /** 全文 */
    fullContent?: string;
    /** 封面图片 */
    coverImage?: string;
    /** 图片 */
    images?: string;
    /** 状态 */
    status?: string;
    /** 错误信息 */
    errorMessage?: string;
    /** 创建时间 */
    createTime?: string;
    /** 完成时间 */
    completedTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type DataWithMediaType = {
    data?: Record<string, any>;
    mediaType?: MediaType | null;
  };

  type DefaultCallback = {
    delegates?: Runnable[];
  };

  type DeleteRequest = {
    /** id */
    id?: number;
  };

  type ErrorCallback = {};

  type getArticleParams = {
    taskId: string;
  };

  type getProgressParams = {
    taskId: string;
  };

  type Handler = {};

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

  type MapString = {
    key?: string;
  };

  type MediaType = {
    type?: string;
    subtype?: string;
    parameters?: MapString;
    toStringValue?: string;
  };

  type PageArticleVO = {
    records?: ArticleVO[];
    pageNumber?: number;
    pageSize?: number;
    maxPageSize?: number;
    totalPage?: number;
    totalRow?: number;
    optimizeCountQuery?: boolean;
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

  type ResultArticleVO = {
    code?: number;
    msg?: string;
    data?: ArticleVO;
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

  type ResultPageArticleVO = {
    code?: number;
    msg?: string;
    data?: PageArticleVO;
  };

  type ResultPageLoginUserVO = {
    code?: number;
    msg?: string;
    data?: PageLoginUserVO;
  };

  type ResultString = {
    code?: number;
    msg?: string;
    data?: string;
  };

  type Runnable = {};

  type SseEmitter = {
    timeout?: number;
    handler?: Handler | null;
    earlySendAttempts?: DataWithMediaType[];
    complete?: boolean;
    failure?: Throwable | null;
    timeoutCallback?: DefaultCallback;
    errorCallback?: ErrorCallback;
    completionCallback?: DefaultCallback;
  };

  type StackTraceElement = {
    /** The name of the class loader. */
    classLoaderName?: string;
    /** The module name. */
    moduleName?: string;
    /** The module version. */
    moduleVersion?: string;
    /** The declaring class. */
    declaringClass?: string;
    /** The method name. */
    methodName?: string;
    /** The source file name. */
    fileName?: string;
    /** The source line number. */
    lineNumber?: number;
    /** Control to show full or partial module, package, and class names. */
    format?: number;
  };

  type Throwable = {
    /** Specific details about the Throwable.  For example, for
{@code FileNotFoundException}, this contains the name of
the file that could not be found. */
    detailMessage?: string;
    cause?: Throwable;
    /** The stack trace, as returned by{@link #getStackTrace()}.

The field is initialized to a zero-length array.  A{@code
    * null} value of this field indicates subsequent calls to{@link
    * #setStackTrace(StackTraceElement[])} and{@link
    * #fillInStackTrace()} will be no-ops. */
    stackTrace?: StackTraceElement[];
    /** The list of suppressed exceptions, as returned by{@link
    * #getSuppressed()}.  The list is initialized to a zero-element
unmodifiable sentinel list.  When a serialized Throwable is
read in, if the{@code suppressedExceptions} field points to a
zero-element list, the field is reset to the sentinel value. */
    suppressedExceptions?: Throwable[];
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
