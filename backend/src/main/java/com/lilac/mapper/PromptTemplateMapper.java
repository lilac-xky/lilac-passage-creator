package com.lilac.mapper;

import com.lilac.domain.entity.PromptTemplate;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
