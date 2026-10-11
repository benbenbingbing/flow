package com.workflow.entity.form.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import org.apache.ibatis.annotations.Mapper;

/** 当前发布配置的读写；调用方在草稿所属表单行锁内执行覆盖发布。 */
@Mapper
public interface EntityFormPublishedMapper extends BaseMapper<EntityFormPublished> {
    /** 所有发布共享一条锁，跨实体父子图及默认表单切换必须在同一并发边界校验。 */
    @org.apache.ibatis.annotations.Select("SELECT lock_id FROM entity_form_publication_lock WHERE lock_id = 1 FOR UPDATE")
    Integer lockPublication();

    @org.apache.ibatis.annotations.Delete("DELETE FROM entity_form_current_default WHERE entity_id = #{entityId}")
    int clearDefault(String entityId);
    @org.apache.ibatis.annotations.Delete("DELETE FROM entity_form_current_default WHERE form_id = #{formId}")
    int removeDefaultForm(String formId);
    @org.apache.ibatis.annotations.Insert("INSERT INTO entity_form_current_default(entity_id, form_id) VALUES(#{entityId}, #{formId})")
    int insertDefault(@org.apache.ibatis.annotations.Param("entityId") String entityId,
                      @org.apache.ibatis.annotations.Param("formId") String formId);
}
