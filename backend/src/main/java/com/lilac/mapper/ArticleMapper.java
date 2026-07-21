package com.lilac.mapper;

import com.lilac.domain.entity.Article;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章数据库操作
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
}