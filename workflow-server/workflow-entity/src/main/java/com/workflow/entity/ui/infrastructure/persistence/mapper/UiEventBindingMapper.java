package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Set;

/**
 * UI 事件绑定持久化入口。
 */
// LIKE 搜索模式由 Wrapper 绑定参数，保留已有通配符语义。
@Mapper
public interface UiEventBindingMapper extends BaseMapper<UiEventBinding> {

    /**
     * 查询可能引用指定接口服务的设计态绑定。
     *
     * <p>参数是已经完成 JSON 字符串转义的完整属性片段。SQL 只用于缩小
     * 候选集，应用层仍会解析 JSON 并做 serviceId 精确相等判断，避免 LIKE
     * 的通配或旧数据格式导致误报。</p>
     */
    default List<UiEventBinding> findDraftReferenceCandidates(String compactNeedle, String spacedNeedle) {
        if (compactNeedle == null && spacedNeedle == null) {
            return List.of();
        }
        return selectList(Wrappers.<UiEventBinding>lambdaQuery()
                .isNotNull(UiEventBinding::getStepsDocument)
                .apply("LENGTH(TRIM(steps_document)) > 0")
                .and(query -> {
                    if (compactNeedle != null) {
                        query.like(UiEventBinding::getStepsDocument, compactNeedle);
                    }
                    if (spacedNeedle != null) {
                        query.or(compactNeedle != null).like(UiEventBinding::getStepsDocument, spacedNeedle);
                    }
                })
                .orderByAsc(UiEventBinding::getOwnerType, UiEventBinding::getOwnerId,
                        UiEventBinding::getTargetType, UiEventBinding::getTargetKey, UiEventBinding::getEventCode));
    }

    /** 查询归属对象的有效事件绑定，按目标及事件编码稳定排列。 */
    default List<UiEventBinding> findByOwner(String ownerType, String ownerId) {
        return selectList(Wrappers.<UiEventBinding>lambdaQuery()
                .eq(UiEventBinding::getOwnerType, ownerType)
                .eq(UiEventBinding::getOwnerId, ownerId)
                .orderByAsc(UiEventBinding::getTargetType)
                .orderByAsc(UiEventBinding::getTargetKey)
                .orderByAsc(UiEventBinding::getEventCode));
    }

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

    /**
     * 清理已经失去字段节点的本地事件草稿，包括同目标的旧逻辑删除行。
     * 事件按字段编码而非节点 ID 绑定，清理后重新添加字段不会接上旧事件；
     * 物理清理避免 deleted 参与唯一键时反复删除/重建产生冲突。
     * 已发布事件保存在独立快照中，不受此操作影响。
     *
     * @param formId 当前表单 ID
     * @param targetKeys 已经没有有效节点承载的字段编码，必须非空
     */
    @Delete({"<script>",
            "DELETE FROM ui_event_binding WHERE owner_type = 'FORM'",
            "AND owner_id = #{formId} AND target_type = 'FIELD' AND target_key IN",
            "<foreach collection='targetKeys' item='key' open='(' separator=',' close=')'>",
            "#{key}",
            "</foreach>",
            "</script>"})
    int deleteFormFieldBindings(
            @Param("formId") String formId,
            @Param("targetKeys") Set<String> targetKeys);

    /** 快照合并实体级和当前配置级绑定，实体级顺序优先且只包含已启用的活动草稿。 */
    default List<UiEventBinding> findForSnapshot(String configType, String configId, String entityId) {
        // CASE 只表达固定的归属优先级；所有配置标识仍由 Wrapper 绑定参数。
        return selectList(Wrappers.<UiEventBinding>query()
                .orderByAsc("CASE owner_type WHEN 'ENTITY' THEN 0 ELSE 1 END",
                        "target_type", "target_key", "event_code")
                .lambda()
                .and(query -> query
                        .and(entity -> entity.eq(UiEventBinding::getOwnerType, "ENTITY")
                                .eq(UiEventBinding::getOwnerId, entityId))
                        .or(config -> config.eq(UiEventBinding::getOwnerType, configType)
                                .eq(UiEventBinding::getOwnerId, configId)))
                .eq(UiEventBinding::getEnabled, 1));
    }
}
