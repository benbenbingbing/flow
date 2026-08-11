package com.workflow.project.custom;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.entity.list.EntityListDataProvider;
import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.core.logging.LogValue;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义实体列表数据源示例。
 *
 * <p>编码为 {@value #CODE}。实现严格接收平台数据范围计划，不执行任意 SQL。
 * 为了让验收页面能直接看出 Provider 已执行，第 1 页返回一条明确标记的演示
 * 记录；后续接入真实项目查询服务时可替换 {@link #sampleRecord}。</p>
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
        if (!allowed) {
            log.info(
                    "项目列表数据源返回空分页: code={}, reason=DATA_SCOPE_DENIED, entityCode={}, listKey={}",
                    CODE,
                    LogValue.safe(context == null
                            ? null : context.entityCode()),
                    LogValue.safe(context == null
                            ? null : context.listKey()));
            return new PageResult<>(
                    List.of(),
                    0,
                    pageNum,
                    pageSize);
        }
        EntityDataDTO sample = sampleRecord(context);
        List<EntityDataDTO> records =
                pageNum == 1
                        ? List.of(sample)
                        : List.of();
        log.info(
                "项目列表数据源返回验收记录: code={}, entityCode={}, listKey={}, recordId={}, recordCount={}, total=1",
                CODE,
                LogValue.safe(sample.getEntityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(sample.getId()),
                records.size());
        return new PageResult<>(
                records,
                1,
                pageNum,
                pageSize);
    }

    private EntityDataDTO sampleRecord(
            EntityListRuntimeContext context) {
        String entityCode =
                context == null
                        || context.entityCode() == null
                        || context.entityCode().isBlank()
                        ? "project_extension_acceptance"
                        : context.entityCode();
        EntityDataDTO result = new EntityDataDTO();
        result.setId("PROJECT-CUSTOM-LIST-SAMPLE");
        result.setEntityCode(entityCode);
        result.setEntityName("项目扩展验收单");
        result.setDataNo("EXT-PROVIDER-001");
        result.setCode("EXT-PROVIDER-001");
        result.setName("安全查询 Provider 演示记录");
        result.setTitle("安全查询 Provider 演示记录");
        result.setStatus("DRAFT");
        result.setCreatedAt(LocalDateTime.now());
        Map<String, Object> data =
                new LinkedHashMap<>();
        data.put("name", result.getName());
        data.put("acceptance_scene",
                "LIST_QUERY_PROVIDER");
        data.put("acceptance_score", 88);
        data.put("owner_name", "project 模块");
        data.put("provider_trace",
                "PROJECT_CUSTOM_LIST_QUERY 已返回演示记录");
        data.put("extension_result",
                "该记录不落库，仅用于验证安全查询 Provider。");
        result.setData(data);
        result.setExtData(Map.of(
                "provider_column",
                "安全查询 Provider"));
        return result;
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
