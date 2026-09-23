package com.workflow.process.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 流程版本历史 Mapper
 */
@Mapper
public interface ProcessVersionHistoryMapper extends BaseMapper<ProcessVersionHistory> {

    /**
     * 查询流程配置是否曾产生过任一发布版本。
     *
     * <p>逻辑删除只隐藏目录记录，不会撤销 Flowable 部署或历史实例，因此
     * deleted 版本仍然构成实体绑定的不可变运行契约。</p>
     *
     * @param processConfigId 流程配置ID，后续用于统计已发布{@code versions}时定位或关联目标
     * @return 符合条件的已发布{@code versions}数量
     */
    @Select("SELECT COUNT(*) FROM process_version_history "
            + "WHERE process_config_id = #{processConfigId}")
    long countPublishedVersions(
            @Param("processConfigId") String processConfigId);

    /**
     * 根据流程定义ID查询版本历史列表（排除已删除）
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID时定位或关联目标
     * @return 流程版本历史集合，供调用方遍历或展示
     */
    default List<ProcessVersionHistory> findByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getProcessConfigId, processConfigId)
                .eq(ProcessVersionHistory::getDeleted, 0)
                .orderByDesc(ProcessVersionHistory::getVersion));
    }

    /**
     * 查询流程的最大版本号（排除已删除）
     */
    /**
     * 按业务编码读取最大版本；聚合使用标准 SQL，过滤及逻辑删除交给 Wrapper。
     *
     * @param processConfigId 流程配置ID，后续用于查询最大版本流程配置ID时定位或关联目标
     * @return 符合条件的流程版本历史结果，供调用方继续处理
     */
    default Integer findMaxVersionByProcessConfigId(String processConfigId) {
        List<Object> values = selectObjs(Wrappers.<ProcessVersionHistory>query()
                .select("MAX(version)").eq("process_config_id", processConfigId));
        return values.isEmpty() || values.get(0) == null ? null : ((Number) values.get(0)).intValue();
    }

    /**
     * 根据流程定义ID和版本号查询（排除已删除）
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID与版本时定位或关联目标
     * @param version 版本，供本方法查询流程配置ID与版本时使用
     * @return 匹配的流程配置ID与版本；未找到时为空
     */
    default Optional<ProcessVersionHistory> findByProcessConfigIdAndVersion(String processConfigId, Integer version) {
        return Optional.ofNullable(selectOne(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getProcessConfigId, processConfigId)
                .eq(ProcessVersionHistory::getVersion, version)
                .eq(ProcessVersionHistory::getDeleted, 0)));
    }

    /**
     * 根据部署ID查询（排除已删除）
     *
     * @param deploymentId {@code deployment}ID，后续用于查询{@code deployment}ID时定位或关联目标
     * @return 匹配的{@code deployment}ID；未找到时为空
     */
    default Optional<ProcessVersionHistory> findByDeploymentId(String deploymentId) {
        return Optional.ofNullable(selectOne(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getDeploymentId, deploymentId)
                .eq(ProcessVersionHistory::getDeleted, 0)));
    }

    /**
     * 根据流程标识查询最新发布版本
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的流程版本历史结果，供调用方继续处理
     */
    default ProcessVersionHistory findLatestByProcessKey(String processKey) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessVersionHistory>(1, 1, false), Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getProcessKey, processKey)
                .eq(ProcessVersionHistory::getStatus, "ACTIVE")
                .orderByDesc(ProcessVersionHistory::getVersion)).stream().findFirst().orElse(null);
    }
}
