package com.workflow.entity.form.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体表单Mapper
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityFormMapper extends BaseMapper<EntityForm> {

    /**
     * 查询实体的表单列表
     */
    default List<EntityForm> selectByEntityId(String entityId) {
        return selectList(Wrappers.<EntityForm>lambdaQuery()
                .eq(EntityForm::getEntityId, entityId));
    }

    /**
     * 检查表单标识是否已存在
     */
    default boolean existsFormKey(String entityId, String formKey, String excludeId) {
        return exists(Wrappers.<EntityForm>lambdaQuery()
                .eq(EntityForm::getEntityId, entityId)
                .eq(EntityForm::getFormKey, formKey)
                .ne(excludeId != null && !excludeId.isEmpty(), EntityForm::getId, excludeId));
    }

    /**
     * 根据表单Key查询
     */
    default EntityForm selectByFormKey(String formKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityForm>lambdaQuery()
                .eq(EntityForm::getFormKey, formKey))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据实体ID和表单Key查询
     */
    default EntityForm selectByEntityIdAndFormKey(String entityId, String formKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityForm>lambdaQuery()
                .eq(EntityForm::getEntityId, entityId)
                .eq(EntityForm::getFormKey, formKey))
                .stream().findFirst().orElse(null);
    }

    /**
     * 查询实体的默认表单
     */
    default EntityForm selectDefaultByEntityId(String entityId) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityForm>lambdaQuery()
                .eq(EntityForm::getEntityId, entityId)
                .eq(EntityForm::getIsDefault, 1))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据主键 ID 加锁查询表单（FOR UPDATE），用于并发更新场景。
     *
     * @param id 主键 ID
     * @return 表单对象，无则返回 null
     */
    @Select("SELECT * FROM entity_form WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EntityForm selectByIdForUpdate(@Param("id") String id);
}
