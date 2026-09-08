package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * UI 事件绑定持久化入口。
 */
@Mapper
public interface UiEventBindingMapper extends BaseMapper<UiEventBinding> {

    /**
     * 查询可能引用指定接口服务的设计态绑定。
     *
     * <p>参数是已经完成 JSON 字符串转义的完整属性片段。SQL 只用于缩小
     * 候选集，应用层仍会解析 JSON 并做 serviceId 精确相等判断，避免 LIKE
     * 的通配或旧数据格式导致误报。</p>
     */
    @Select("SELECT * FROM ui_event_binding "
            + "WHERE deleted = 0 AND steps_document IS NOT NULL "
            + "AND TRIM(steps_document) <> '' "
            + "AND (steps_document LIKE CONCAT('%', #{compactNeedle}, '%') "
            + "OR steps_document LIKE CONCAT('%', #{spacedNeedle}, '%')) "
            + "ORDER BY owner_type, owner_id, target_type, "
            + "target_key, event_code")
    List<UiEventBinding> findDraftReferenceCandidates(
            @Param("compactNeedle") String compactNeedle,
            @Param("spacedNeedle") String spacedNeedle);

    @Select("SELECT * FROM ui_event_binding "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId} "
            + "AND deleted = 0 ORDER BY target_type, target_key, event_code")
    List<UiEventBinding> findByOwner(
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId);

    /**
     * 锁定指定所有者的全部事件绑定（包含逻辑删除行）。
     *
     * <p>撤销草稿会以 owner 范围作为串行化边界，防止事件绑定未更新 FORM/LIST
     * revision 时绕过配置级 CAS。</p>
     */
    @Select("SELECT * FROM ui_event_binding "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId} "
            + "ORDER BY target_type, target_key, event_code FOR UPDATE")
    List<UiEventBinding> findByOwnerForUpdate(
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId);

    /** 物理清理指定所有者的草稿绑定，发布快照不在本表且不受影响。 */
    @Delete("DELETE FROM ui_event_binding "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId}")
    int deleteByOwner(
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
