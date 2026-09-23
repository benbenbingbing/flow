package com.workflow.biz.project.action;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.entity.data.api.response.EntityDataDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates the governed system asset after a system application is approved.
 *
 * <p>This is intentionally the only project-specific backend extension. Entity CRUD, forms,
 * lists, permissions, process routing, approval options, and status synchronization are all
 * supplied by platform configuration.</p>
 */
@Component("createSystemAssetHandler")
public class CreateSystemAssetHandler implements FlowActionHandler {

    private static final String SOURCE_ENTITY = "system_application";
    private static final String TARGET_ENTITY = "system_asset";

    private final EntityMutationPort entityMutationPort;

    /**
     * 初始化创建系统资产处理器，保存构造参数供后续方法使用。
     *
     * @param entityMutationPort 实体变更端口依赖，保存到当前对象供后续业务方法调用
     */
    public CreateSystemAssetHandler(
            EntityMutationPort entityMutationPort) {
        this.entityMutationPort = entityMutationPort;
    }

    /**
     * 列出支持的触发条件时机集合；结果供调用方的后续步骤使用。
     *
     * @return 创建系统资产集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    /**
     * 列出支持的执行模式集合；结果供调用方的后续步骤使用。
     *
     * @return 创建系统资产集合，供调用方遍历或展示
     */
    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    /**
     * 生成推荐执行模式文本，供后续匹配或展示。
     *
     * @return 处理后的推荐执行模式文本，供调用方比较或展示
     */
    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    /**
     * 执行创建系统资产，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续创建系统资产步骤传递身份、配置或状态
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public void execute(FlowActionContext context) {
        if (!SOURCE_ENTITY.equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "The process did not finish with approval.");
            return;
        }

        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO application)) {
            throw new IllegalStateException("System application data is unavailable.");
        }
        Map<String, Object> source = application.getData() == null
                ? Map.of()
                : application.getData();
        Object existingAssetId = read(source, "approved_system_id");
        if (existingAssetId != null && !String.valueOf(existingAssetId).isBlank()) {
            context.setExecutionResult(Map.of("systemAssetId", existingAssetId, "reused", true));
            return;
        }

        Map<String, Object> createPayload =
                new LinkedHashMap<>();
        createPayload.put(
                "name",
                text(read(source, "proposed_system_name")));
        createPayload.put(
                "submitterId",
                application.getSubmitterId());
        createPayload.put(
                "submitterName",
                application.getSubmitterName());
        createPayload.put(
                "data",
                buildAssetData(application, source));
        EntityMutationResult saved =
                entityMutationPort.execute(
                        mutationCommand(
                                context,
                                1,
                                TARGET_ENTITY,
                                null,
                                EntityMutationOperationType.CREATE,
                                createPayload));

        Map<String, Object> update = new LinkedHashMap<>();
        Map<String, Object> applicationData = new LinkedHashMap<>();
        applicationData.put(
                "approved_system_id",
                saved.recordId());
        applicationData.put("approved_at", LocalDateTime.now());
        update.put("data", applicationData);
        entityMutationPort.execute(
                mutationCommand(
                        context,
                        2,
                        SOURCE_ENTITY,
                        application.getId(),
                        EntityMutationOperationType.UPDATE,
                        update));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemAssetId", saved.recordId());
        result.put(
                "systemAssetCode",
                saved.record().get("code"));
        result.put("reused", false);
        context.setExecutionResult(result);
        context.addExecutionTrace("CREATED", "Created system asset and linked it to the application.", result);
    }

    /**
     * 为审批生效的每一步实体写入生成独立幂等键，并传递流程动作参数。
     * EntityMutationContext 在构建时复制参数，避免后续写入与动作上下文共享可变 Map。
     *
     * @param flowContext 执行上下文，向后续变更命令步骤传递身份、配置或状态
     * @param sequence 序列，供本方法处理变更命令时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param operationType 操作类型标识，决定后续变更命令采用的处理分支
     * @param payload 载荷，后续用于处理变更命令并传递处理结果
     * @return 处理后的变更命令结果，供调用方继续处理
     */
    private EntityMutationCommand mutationCommand(
            FlowActionContext flowContext,
            int sequence,
            String entityCode,
            String recordId,
            EntityMutationOperationType operationType,
            Map<String, Object> payload) {
        String baseKey = StringUtils.hasText(
                flowContext.getIdempotencyKey())
                ? flowContext.getIdempotencyKey()
                : flowContext.getProcessInstanceId()
                        + ":"
                        + flowContext.getActionId();
        String idempotencyKey =
                baseKey + ":mutation:" + sequence;
        EntityMutationContext mutationContext =
                EntityMutationContext.builder(
                                EntityMutationSourceType.FLOW_ACTION,
                                "INITIAL_EFFECTIVE",
                                "系统申请审批生效")
                        .sourceId(flowContext.getActionId())
                        .sourceRecord(
                                flowContext.getEntityCode(),
                                flowContext.getEntityDataId())
                        .process(
                                flowContext.getProcessDefinitionId(),
                                flowContext.getProcessInstanceId(),
                                flowContext.getTaskId())
                        .operator(
                                flowContext.getOperatorId(),
                                flowContext.getOperatorId())
                        .trace(
                                flowContext.getProcessInstanceId(),
                                idempotencyKey)
                        .extraParams(flowContext.getExtraParams())
                        .build();
        return new EntityMutationCommand(
                idempotencyKey,
                entityCode,
                recordId,
                operationType,
                payload,
                mutationContext);
    }

    /**
     * 构建资产数据；结果供后续流程传递或持久化。
     *
     * @param application 应用，作为 {@code target.put} 的输入影响后续处理
     * @param source 待构建资产数据的原始输入，结果供调用方继续使用
     * @return 资产数据键值结果，供调用方继续处理
     */
    private Map<String, Object> buildAssetData(
            EntityDataDTO application,
            Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        copy(source, target, "proposed_abbreviation", "system_abbreviation");
        copy(source, target, "system_type", "system_type");
        copy(source, target, "business_domain", "business_domain");
        copy(source, target, "owner_dept_id", "owner_dept_id");
        copy(source, target, "proposed_system_owner_id", "system_owner_id");
        copy(source, target, "proposed_technical_owner_id", "technical_owner_id");
        copy(source, target, "proposed_ops_owner_id", "ops_owner_id");
        copy(source, target, "criticality_level", "criticality_level");
        copy(source, target, "data_classification", "data_classification");
        copy(source, target, "security_level", "security_level");
        copy(source, target, "deployment_mode", "deployment_mode");
        copy(source, target, "availability_target", "availability_target");
        copy(source, target, "rto_minutes", "rto_minutes");
        copy(source, target, "rpo_minutes", "rpo_minutes");
        copy(source, target, "expected_go_live_date", "planned_go_live_date");
        target.put("source_application_id", application.getId());
        target.put("asset_status", "PROPOSED");
        return target;
    }

    /**
     * 复制创建系统资产；结果供后续流程传递或持久化。
     *
     * @param source 待复制创建系统资产的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法复制创建系统资产时使用
     * @param sourceKey 来源键，后续用于授权校验、关联或幂等去重
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     */
    private void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String sourceKey,
            String targetKey) {
        Object value = read(source, sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    /**
     * 读取创建系统资产；查询结果供调用方展示或继续处理。
     *
     * @param source 待读取创建系统资产的原始输入，结果供调用方继续使用
     * @param snakeCaseKey {@code snake}分支键，后续用于授权校验、关联或幂等去重
     * @return 读取后的创建系统资产结果，供调用方继续处理
     */
    private Object read(Map<String, Object> source, String snakeCaseKey) {
        if (source.containsKey(snakeCaseKey)) {
            return source.get(snakeCaseKey);
        }
        StringBuilder camelCaseKey = new StringBuilder();
        boolean capitalizeNext = false;
        for (char character : snakeCaseKey.toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                camelCaseKey.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                camelCaseKey.append(character);
            }
        }
        return source.get(camelCaseKey.toString());
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
