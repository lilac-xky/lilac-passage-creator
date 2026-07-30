// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 创建 VIP 支付会话 POST /api/payment/create-vip-session */
export async function createVipPaymentSession(options?: {
  [key: string]: any;
}) {
  return request<API.ResultString>("/api/payment/create-vip-session", {
    method: "POST",
    ...(options || {}),
  });
}

/** 获取当前用户支付记录 GET /api/payment/records */
export async function getPaymentRecords(options?: { [key: string]: any }) {
  return request<API.ResultListPaymentRecord>("/api/payment/records", {
    method: "GET",
    ...(options || {}),
  });
}

/** 申请退款 POST /api/payment/refund */
export async function refund(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.refundParams,
  options?: { [key: string]: any }
) {
  return request<API.ResultBoolean>("/api/payment/refund", {
    method: "POST",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
