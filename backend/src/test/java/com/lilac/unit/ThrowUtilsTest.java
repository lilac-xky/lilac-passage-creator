package com.lilac.unit;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.utils.ThrowUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 参数校验工具单测。
 *
 * <p>ThrowUtils 是 Controller 层参数校验的统一入口（全仓 30+ 处调用），
 * 一旦语义变了会波及所有接口，因此值得用测试钉住。
 */
@DisplayName("ThrowUtils 条件抛异常")
class ThrowUtilsTest {

    @Test
    @DisplayName("条件为 false 时不抛异常")
    void noThrowWhenConditionFalse() {
        assertDoesNotThrow(() -> ThrowUtils.throwIf(false, HttpsCodeEnum.PARAMS_ERROR));
    }

    @Test
    @DisplayName("条件为 true 时抛出带错误码的 BusinessException")
    void throwsWithErrorCode() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ThrowUtils.throwIf(true, HttpsCodeEnum.PARAMS_ERROR)
        );
        assertEquals(HttpsCodeEnum.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("可以覆盖默认错误信息")
    void throwsWithCustomMessage() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ThrowUtils.throwIf(true, HttpsCodeEnum.PARAMS_ERROR, "任务ID不能为空")
        );
        assertEquals("任务ID不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("可以直接抛自定义 RuntimeException")
    void throwsCustomRuntimeException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ThrowUtils.throwIf(true, new IllegalStateException("boom"))
        );
        assertEquals("boom", ex.getMessage());
    }
}
