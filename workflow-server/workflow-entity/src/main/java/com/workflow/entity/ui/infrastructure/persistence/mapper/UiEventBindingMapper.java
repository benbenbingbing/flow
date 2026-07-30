package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UI 事件绑定持久化入口。
 */
@Mapper
public interface UiEventBindingMapper extends BaseMapper<UiEventBinding> {

    @Select("SELECT * FROM ui_event_binding "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId} "
            + "AND deleted = 0 ORDER BY target_type, target_key, event_code")
    List<UiEventBinding> findByOwner(
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId);

    @Select("SELECT * FROM ui_event_binding "
            + "WHERE ((owner_type = 'ENTITY' AND owner_id = #{entityId}) "
            + "OR (owner_type = #{configType} AND owner_id = #{configId})) "
            + "AND deleted = 0 AND enabled = 1 "
            + "ORDER BY CASE owner_type WHEN 'ENTITY' THEN 0 ELSE 1 END, "
            + "target_type, target_key, event_code")
    List<UiEventBinding> findForSnapshot(
            @Param("configType") String configType,
            @Param("configId") String configId,
            @Param("entityId") String entityId);
}
