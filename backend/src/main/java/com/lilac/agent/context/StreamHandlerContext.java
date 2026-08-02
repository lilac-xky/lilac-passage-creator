package com.lilac.agent.context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 流式输出处理器上下文
 * 使用 ThreadLocal 保存 streamHandler，避免将其放入 StateGraph 状态中（无法序列化）
 */
public class StreamHandlerContext {

    private static final ThreadLocal<Consumer<String>> STREAM_HANDLER = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Consumer<String>> TASK_HANDLERS = new ConcurrentHashMap<>();

    /**
     * 设置流式输出处理器
     */
    public static void set(Consumer<String> handler) {
        STREAM_HANDLER.set(handler);
    }

    /**
     * 获取流式输出处理器
     */
    public static Consumer<String> get() {
        return STREAM_HANDLER.get();
    }

    public static void register(String taskId, Consumer<String> handler) {
        if (taskId != null && handler != null) {
            TASK_HANDLERS.put(taskId, handler);
        }
    }

    public static Consumer<String> get(String taskId) {
        Consumer<String> handler = taskId == null ? null : TASK_HANDLERS.get(taskId);
        return handler != null ? handler : get();
    }

    public static void unregister(String taskId) {
        if (taskId != null) {
            TASK_HANDLERS.remove(taskId);
        }
    }

    /**
     * 清理上下文
     * 务必在使用完毕后调用，避免内存泄漏
     */
    public static void clear() {
        STREAM_HANDLER.remove();
    }

    /**
     * 发送消息到流式输出
     * 如果 handler 不存在则忽略
     */
    public static void send(String message) {
        Consumer<String> handler = STREAM_HANDLER.get();
        if (handler != null && message != null) {
            handler.accept(message);
        }
    }
}
