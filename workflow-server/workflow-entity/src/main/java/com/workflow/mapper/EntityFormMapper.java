package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体表单Mapper
 */
@Mapper
public interface EntityFormMapper extends BaseMapper<EntityForm> {
    
    /**
     * 查询实体的表单列表
     */
    @Select("SELECT * FROM entity_form WHERE entity_id = #{entityId} AND deleted = 0")
    List<EntityForm> selectByEntityId(@Param("entityId") String entityId);
    
    /**
     * 检查表单标识是否已存在
     */
    @Select("SELECT COUNT(*) > 0 FROM entity_form WHERE entity_id = #{entityId} AND form_key = #{formKey} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsFormKey(@Param("entityId") String entityId, @Param("formKey") String formKey, @Param("excludeId") String excludeId);
    
    /**
     * 根据表单Key查询
     */
    @Select("SELECT * FROM entity_form WHERE form_key = #{formKey} AND deleted = 0 LIMIT 1")
    EntityForm selectByFormKey(@Param("formKey") String formKey);
    
    /**
     * 根据实体ID和表单Key查询
     */
    @Select("SELECT * FROM entity_form WHERE entity_id = #{entityId} AND form_key = #{formKey} AND deleted = 0 LIMIT 1")
    EntityForm selectByEntityIdAndFormKey(@Param("entityId") String entityId, @Param("formKey") String formKey);
    
    /**
     * 查询实体的默认表单
     */
    @Select("SELECT * FROM entity_form WHERE entity_id = #{entityId} AND is_default = 1 AND deleted = 0 LIMIT 1")
    EntityForm selectDefaultByEntityId(@Param("entityId") String entityId);

    @Select("SELECT * FROM entity_form WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EntityForm selectByIdForUpdate(@Param("id") String id);
}
