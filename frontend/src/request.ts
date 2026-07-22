import axios from 'axios';
import { message } from 'ant-design-vue'

const isGetLoginUserRequest = (url?: string) => url?.includes('/user/get/login') ?? false

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090').replace(/\/$/, '')

// 创建axios实例
const myAxios = axios.create({
    baseURL: API_BASE_URL,
    timeout: 6000,
    withCredentials: true,  // 必须！携带 Cookie
});

// 全局响应拦截器
myAxios.interceptors.response.use(
    function (response) {
        const { data } = response
        // 未登录
        if (data.code === 400001) {
            // 获取登录用户接口 静默处理
            if (isGetLoginUserRequest(response.config.url)) {
                return response;
            }

            // 其他接口：未登录处理
            if (!window.location.pathname.includes('/user/login')) {
                message.warning('请先登录');
                window.location.href = `/user/login?redirect=${window.location.href}`;
            }
        }
        return response
    },
    function (error) {
        if (error.response) {
            const { data, status } = error.response;
            // 初始化登录态时应静默交给路由守卫判断。
            if (data?.code === 400001 && isGetLoginUserRequest(error.config?.url)) {
                return Promise.resolve(error.response);
            }

            const msg = data?.msg || `请求失败(${status})`;
            message.error(msg);
            error.message = msg;
        } else {
            // 网络异常等
            message.error('网络异常，请检查连接');
        }
        return Promise.reject(error);
    },
)

export default myAxios
