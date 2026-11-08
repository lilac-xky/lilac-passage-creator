package com.lilac.unit;

import com.lilac.domain.entity.Article;
import com.lilac.domain.vo.ArticleVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配图降级可见化单测（步骤 E2）。
 *
 * <p>背景：实测发现 34 张配图有 5 张（14.7%）降级到占位图，
 * 最严重一篇 10 张降级 5 张（50%）且封面也降级，
 * 但 status 仍是「已完成」、errorMessage 为空，用户完全无法察觉。
 *
 * <p>本步骤让降级程度通过 VO 暴露给前端。
 */
@DisplayName("配图降级可见化")
class ArticleVOImageDegradedTest {

    private Article articleWith(Integer total, Integer fallback, Integer degraded) {
        Article a = new Article();
        a.setTaskId("t1");
        a.setStatus("已完成");
        a.setImageTotal(total);
        a.setImageFallbackCount(fallback);
        a.setImageDegraded(degraded);
        return a;
    }

    @Test
    @DisplayName("无降级时 hasImageFallback=false，降级率 0")
    void noFallback() {
        ArticleVO vo = ArticleVO.objToVO(articleWith(5, 0, 0));
        assertFalse(vo.getHasImageFallback());
        assertFalse(vo.getImageDegraded());
        assertEquals(0, vo.getImageFallbackRate());
        assertEquals(5, vo.getImageTotal());
    }

    @Test
    @DisplayName("轻微降级：有降级但不严重")
    void minorFallback() {
        // 10 张里 2 张 = 20%，低于 30% 阈值
        ArticleVO vo = ArticleVO.objToVO(articleWith(10, 2, 0));
        assertTrue(vo.getHasImageFallback());
        assertFalse(vo.getImageDegraded());
        assertEquals(20, vo.getImageFallbackRate());
        assertEquals(2, vo.getImageFallbackCount());
    }

    @Test
    @DisplayName("【真实案例】10 张降级 5 张 = 50%，应标为严重")
    void severeCaseFromProduction() {
        // 这就是实库里 368efa8a 那篇文章的真实数据
        ArticleVO vo = ArticleVO.objToVO(articleWith(10, 5, 1));
        assertTrue(vo.getHasImageFallback());
        assertTrue(vo.getImageDegraded(), "50% 降级率必须被标为严重");
        assertEquals(50, vo.getImageFallbackRate());
    }

    @Test
    @DisplayName("全量降级：100%")
    void allFallback() {
        ArticleVO vo = ArticleVO.objToVO(articleWith(3, 3, 1));
        assertTrue(vo.getHasImageFallback());
        assertTrue(vo.getImageDegraded());
        assertEquals(100, vo.getImageFallbackRate());
    }

    @Test
    @DisplayName("历史数据 imageTotal 为 null 时不 NPE，兜底为 0")
    void nullFieldsSafe() {
        ArticleVO vo = ArticleVO.objToVO(articleWith(null, null, null));
        assertEquals(0, vo.getImageTotal());
        assertEquals(0, vo.getImageFallbackCount());
        assertEquals(0, vo.getImageFallbackRate());
        assertFalse(vo.getHasImageFallback());
        assertFalse(vo.getImageDegraded());
    }

    @Test
    @DisplayName("imageTotal=0 时降级率算作 0，不除零")
    void zeroTotalNoDivideByZero() {
        ArticleVO vo = ArticleVO.objToVO(articleWith(0, 0, 0));
        assertEquals(0, vo.getImageFallbackRate());
    }

    @Test
    @DisplayName("status 语义保持不变（不引入 PARTIAL，避免破坏前端分支）")
    void statusUnchanged() {
        ArticleVO vo = ArticleVO.objToVO(articleWith(10, 5, 1));
        assertEquals("已完成", vo.getStatus(),
                "降级严重时 status 仍为「已完成」——前端有多处 status 分支判断，"
                        + "新增状态值会让会话恢复等逻辑走错分支");
    }
}
