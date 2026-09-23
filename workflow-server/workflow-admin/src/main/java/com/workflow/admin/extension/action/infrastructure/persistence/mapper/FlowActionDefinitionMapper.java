package com.workflow.admin.extension.action.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 流程动作定义 Mapper。
 *
 * <p>提供动作处理器目录的活跃记录查询能力。</p>
 */
@Mapper
public interface FlowActionDefinitionMapper extends BaseMapper<FlowActionDefinition> {

    /**
     * 查询全部未删除的动作定义，按展示名与处理器名排序。
     *
     * @return 活跃动作定义列表
     */
    default List<FlowActionDefinition> findAllActive() {
        return selectList(Wrappers.<FlowActionDefinition>lambdaQuery()
                .eq(FlowActionDefinition::getDeleted, 0)
                .orderByAsc(FlowActionDefinition::getDisplayName)
                .orderByAsc(FlowActionDefinition::getHandlerName));
    }

    /**
     * 按主键查询未删除的动作定义。
     *
     * @param id 动作定义 ID
     * @return 动作定义；不存在返回 Optional.empty()
     */
    default Optional<FlowActionDefinition> findActiveById(String id) {
        return Optional.ofNullable(selectById(id));
    }

    /**
     * 按处理器 Bean 名称查询未删除的动作定义。
     *
     * @param handlerName 处理器 Bean 名称
     * @return 动作定义；不存在返回 Optional.empty()
     */
    default Optional<FlowActionDefinition> findByHandlerName(String handlerName) {
        return selectPage(new Page<FlowActionDefinition>(1, 1, false), Wrappers.<FlowActionDefinition>lambdaQuery()
                .eq(FlowActionDefinition::getHandlerName, handlerName)).getRecords().stream().findFirst();
    }
}
