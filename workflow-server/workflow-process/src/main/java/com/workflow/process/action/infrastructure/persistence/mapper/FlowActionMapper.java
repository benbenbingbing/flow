package com.workflow.process.action.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程动作 Mapper。
 *
 * <p>提供草稿/已发布动作的多维查询与逻辑删除能力。</p>
 */
@Mapper
public interface FlowActionMapper extends BaseMapper<FlowAction> {
    
    /**
     * 查询流程配置下所有草稿状态的动作（排除已删除）
     *
     * @param processConfigId 流程配置ID，后续用于查询草稿动作集合流程配置ID时定位或关联目标
     * @return 流程动作集合，供调用方遍历或展示
     */
    default List<FlowAction> findDraftActionsByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getProcessConfigId, processConfigId)
                .eq(FlowAction::getStatus, "DRAFT")
                .eq(FlowAction::getDeleted, 0)
                .orderByAsc(FlowAction::getScopeType)
                .orderByAsc(FlowAction::getElementId)
                .orderByAsc(FlowAction::getTriggerTiming)
                .orderByAsc(FlowAction::getSortOrder));
    }

    /**
     * 按作用域与元素绑定查询草稿动作。
     *
     * @param processConfigId 流程配置 ID
     * @param scopeType       作用域类型
     * @param elementId       BPMN 元素 ID；流程级传 null
     * @return 草稿动作列表
     */
    default List<FlowAction> findDraftActionsByBinding(
            String processConfigId, String scopeType, String elementId) {
        return selectList(elementBinding(elementId)
                .eq(FlowAction::getProcessConfigId, processConfigId)
                .eq(FlowAction::getScopeType, scopeType)
                .eq(FlowAction::getStatus, "DRAFT")
                // 绑定查询也必须过滤逻辑删除，避免旧动作在设计器重新出现。
                .eq(FlowAction::getDeleted, 0)
                .orderByAsc(FlowAction::getTriggerTiming, FlowAction::getSortOrder));
    }
    
    /**
     * 查询版本下所有已发布的动作（排除已删除）
     *
     * @param versionId 版本ID，后续用于查询已发布动作集合版本ID时定位或关联目标
     * @return 流程动作集合，供调用方遍历或展示
     */
    default List<FlowAction> findPublishedActionsByVersionId(String versionId) {
        return selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getVersionId, versionId)
                .eq(FlowAction::getStatus, "PUBLISHED")
                .eq(FlowAction::getDeleted, 0)
                .orderByAsc(FlowAction::getScopeType)
                .orderByAsc(FlowAction::getElementId)
                .orderByAsc(FlowAction::getTriggerTiming)
                .orderByAsc(FlowAction::getSortOrder));
    }

    /**
     * 按版本、作用域、元素与触发时机查询已发布动作。
     *
     * @param versionId     流程发布版本 ID
     * @param scopeType     作用域类型
     * @param elementId     BPMN 元素 ID；流程级传 null
     * @param triggerTiming 触发时机编码
     * @return 已发布动作列表
     */
    default List<FlowAction> findPublishedActionsByBinding(
            String versionId, String scopeType, String elementId, String triggerTiming) {
        return selectList(elementBinding(elementId)
                .eq(FlowAction::getVersionId, versionId)
                .eq(FlowAction::getScopeType, scopeType)
                .eq(FlowAction::getTriggerTiming, triggerTiming)
                .eq(FlowAction::getStatus, "PUBLISHED")
                // 已删除动作不能被发布版本重新执行。
                .eq(FlowAction::getDeleted, 0)
                .orderByAsc(FlowAction::getSortOrder));
    }

    /**
     * 流程级动作绑定 NULL；空串保留数据库自身的 NULL 归一语义，避免改变既有 Oracle 查询。
     *
     * @param elementId 元素ID，后续用于处理元素绑定时定位或关联目标
     * @return 处理后的元素绑定结果，供调用方继续处理
     */
    private static LambdaQueryWrapper<FlowAction> elementBinding(String elementId) {
        var wrapper = Wrappers.<FlowAction>lambdaQuery();
        if (elementId == null) {
            return wrapper.isNull(FlowAction::getElementId);
        }
        if (elementId.isEmpty()) {
            // 仅空串需要这段固定 SQL；值仍由 Wrapper 绑定，不拼接请求内容。
            return wrapper.apply("(({0,jdbcType=VARCHAR} IS NULL AND element_id IS NULL) "
                    + "OR element_id = {0,jdbcType=VARCHAR})", elementId);
        }
        return wrapper.eq(FlowAction::getElementId, elementId);
    }
    
    /**
     * 逻辑删除动作
     *
     * @param actionId 动作ID，后续用于处理{@code logic}删除ID时定位或关联目标
     */
    default void logicDeleteById(String actionId) {
        delete(Wrappers.<FlowAction>lambdaQuery().eq(FlowAction::getId, actionId));
    }
    
    /**
     * 逻辑删除版本下的所有动作
     *
     * @param versionId 版本ID，后续用于处理{@code logic}删除版本ID时定位或关联目标
     */
    default void logicDeleteByVersionId(String versionId) {
        delete(Wrappers.<FlowAction>lambdaQuery().eq(FlowAction::getVersionId, versionId));
    }
}
