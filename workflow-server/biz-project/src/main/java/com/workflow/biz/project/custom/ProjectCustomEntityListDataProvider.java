package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.list.spi.EntityListDataProvider;
import com.workflow.contracts.entity.list.model.EntityListQueryFields;
import com.workflow.contracts.entity.list.model.EntityListRuntimeContext;
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

    /**
     * 读取编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    @Override
    public String getCode() {
        return CODE;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义列表查询";
    }

    /**
     * 查询项目自定义实体列表数据提供者；查询结果供调用方展示或继续处理。
     *
     * @param context 执行上下文，向后续项目自定义实体列表数据提供者步骤传递身份、配置或状态
     * @param dataScopePlan 数据作用域方案，供本方法查询项目自定义实体列表数据提供者时使用
     * @param query 查询，供本方法查询项目自定义实体列表数据提供者时使用
     * @return 查询后的项目自定义实体列表数据提供者结果，供调用方继续处理
     */
    @Override
    public Object query(
            EntityListRuntimeContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> query) {
        Map<String, Object> safeQuery =
                query == null ? Map.of() : query;
        long pageNum = positiveLong(
                safeQuery.get(
                        EntityListQueryFields.PAGE_NUM),
                1);
        long pageSize = Math.min(
                positiveLong(
                        safeQuery.get(
                                EntityListQueryFields.PAGE_SIZE),
                        20),
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

    /**
     * 处理{@code sample}记录，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续{@code sample}记录步骤传递身份、配置或状态
     * @return 处理后的{@code sample}记录结果，供调用方继续处理
     */
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
        result.setCode("EXT-PROVIDER-001");
        result.setName("安全查询 Provider 演示记录");
        result.setStatus("DRAFT");
        result.setCreateTime(LocalDateTime.now());
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

    /**
     * 处理正数{@code long}，并将结果传给后续步骤。
     *
     * @param value 待处理正数{@code long}的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的正数{@code long}结果，供调用方继续处理
     */
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
