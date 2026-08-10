package com.workflow.project.custom;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.entity.list.EntityListDataProvider;
import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.core.logging.LogValue;
import com.workflow.core.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自定义实体列表数据源示例。
 *
 * <p>编码为 {@value #CODE}。实现严格接收平台数据范围计划，但当前不执行
 * SQL，只返回空分页并打印查询键，适合后续替换为项目自己的查询服务。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityListDataProvider
        implements EntityListDataProvider {

    public static final String CODE =
            "PROJECT_CUSTOM_LIST_QUERY";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义列表查询";
    }

    @Override
    public Object query(
            EntityListRuntimeContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> query) {
        Map<String, Object> safeQuery =
                query == null ? Map.of() : query;
        long pageNum = positiveLong(
                safeQuery.get("pageNum"), 1);
        long pageSize = Math.min(
                positiveLong(
                        safeQuery.get("pageSize"), 20),
                200);
        boolean allowed =
                dataScopePlan != null && dataScopePlan.allowed();
        log.info(
                "项目列表数据源执行: code={}, entityCode={}, listKey={}, scene={}, allowed={}, releaseVersion={}, queryKeys={}, pageNum={}, pageSize={}",
                CODE,
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(context == null
                        ? null : context.scene()),
                allowed,
                dataScopePlan == null
                        ? null : dataScopePlan.releaseVersion(),
                safeQuery.keySet(),
                pageNum,
                pageSize);
        return new PageResult<>(
                List.of(),
                0,
                pageNum,
                pageSize);
    }

    private long positiveLong(
            Object value,
            long fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.longValue());
        }
        if (value != null) {
            try {
                return Math.max(
                        1,
                        Long.parseLong(
                                String.valueOf(value)));
            } catch (NumberFormatException ignored) {
                // 使用安全默认值。
            }
        }
        return fallback;
    }
}
