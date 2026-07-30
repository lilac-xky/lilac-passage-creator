declare namespace API {
  type ArticleAiModifyOutlineRequest = {
    /** 任务ID */
    taskId?: string;
    /** 用户的修改建议 */
    modifySuggestion?: string;
  };

  type ArticleConfirmOutlineRequest = {
    /** 任务ID */
    taskId?: string;
    /** 用户编辑后的大纲 */
    outline?: OutlineSection[];
  };

  type ArticleConfirmTitleRequest = {
    /** 任务ID */
    taskId?: string;
    /** 选中的主标题 */
    selectedMainTitle?: string;
    /** 选中的副标题 */
    selectedSubTitle?: string;
    /** 用户补充描述（可选） */
    userDescription?: string;
  };

  type ArticleCreateRequest = {
    /** 选题 */
    topic?: string;
    /** 文章风格（可选） */
    style?: string;
    enabledImageMethods?: string[];
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
    outline?: OutlineSection[];
    /** 内容 */
    content?: string;
    /** 全文 */
    fullContent?: string;
    /** 封面图片 */
    coverImage?: string;
    /** 图片 */
    images?: ImageResult[];
    /** 状态 */
    status?: string;
    /** 错误信息 */
    errorMessage?: string;
    /** 文章风格 */
    style?: string;
    /** 用户补充描述 */
    userDescription?: string;
    /** 允许使用的配图方式 */
    enabledImageMethods?: string[];
    /** 标题方案 */
    titleOptions?: TitleOption[];
    /** 当前生成阶段 */
    phase?: string;
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

  type ImageResult = {
    position?: number;
    url?: string;
    method?: string;
    keywords?: string;
    sectionTitle?: string;
    description?: string;
    /** 占位符ID，用于在正文中定位插入位置 */
    placeholderId?: string;
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
    /** Remaining article generation quota. */
    quota?: number;
    /** 成为会员时间 */
    vipTime?: string;
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

  type OutlineSection = {
    section?: number;
    title?: string;
    points?: string[];
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

  type PaymentRecord = {
    /** id */
    id?: number;
    /** 用户ID */
    userId?: number;
    /** Stripe Checkout Session ID */
    stripeSessionId?: string;
    /** Stripe 支付意向ID */
    stripePaymentIntentId?: string;
    /** 金额（美元） */
    amount?: number;
    /** 货币 */
    currency?: string;
    /** 状态：PENDING/SUCCEEDED/FAILED/REFUNDED */
    status?: string;
    /** 产品类型：VIP_PERMANENT */
    productType?: string;
    /** 描述 */
    description?: string;
    /** 退款时间 */
    refundTime?: string;
    /** 退款原因 */
    refundReason?: string;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type refundParams = {
    reason?: string;
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

  type ResultListOutlineSection = {
    code?: number;
    msg?: string;
    data?: OutlineSection[];
  };

  type ResultListPaymentRecord = {
    code?: number;
    msg?: string;
    data?: PaymentRecord[];
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

  type ResultVoid = {
    code?: number;
    msg?: string;
    data?: null;
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

  type TitleOption = {
    mainTitle?: string;
    subTitle?: string;
  };

  type UserAddRequest = {
    userAccount?: string;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    quota?: number;
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
    /** Remaining article generation quota. */
    quota?: number;
  };
}
