// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 获取文章详情 GET /api/article/${param0} */
export async function getArticle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getArticleParams,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.ResultArticleVO>(`/api/article/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** AI 修改大纲 POST /api/article/ai-modify-outline */
export async function aiModifyOutline(
  body: API.ArticleAiModifyOutlineRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultListOutlineSection>(
    "/api/article/ai-modify-outline",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}

/** 确认大纲 POST /api/article/confirm-outline */
export async function confirmOutline(
  body: API.ArticleConfirmOutlineRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultVoid>("/api/article/confirm-outline", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 确认标题并输入补充描述 POST /api/article/confirm-title */
export async function confirmTitle(
  body: API.ArticleConfirmTitleRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultVoid>("/api/article/confirm-title", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 创建文章任务 POST /api/article/create */
export async function createArticle(
  body: API.ArticleCreateRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultString>("/api/article/create", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 删除文章 POST /api/article/delete */
export async function deleteArticle(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/article/delete", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 分页查询文章列表 POST /api/article/list */
export async function listArticle(
  body: API.ArticleQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.ResultPageArticleVO>("/api/article/list", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** SSE 进度推送 GET /api/article/progress/${param0} */
export async function getProgress(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getProgressParams,
  options?: { [key: string]: any }
) {
  const { taskId: param0, ...queryParams } = params;
  return request<API.SseEmitter>(`/api/article/progress/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}
