package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormAccessPort;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.application.EntityListReleaseContext;
import com.workflow.entity.list.application.EntityListRuntimeService;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把 Embed 原生表单访问适配到 Flow 自己的权限、DataScope 和发布运行时。
 *
 * <p>本适配器刻意不读取表单字段或组件配置。新增字段组件、日期/下拉弹层或
 * 自定义渲染器都继续由普通 Flow 页面处理，不会进入 Embed 兼容分支。</p>
 */
@Component
public class EntityEmbedNativeFormAccessAdapter
        implements EmbedNativeFormAccessPort {

    private static final Pattern SAFE_FIELD_CODE = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]{0,99}");

    private final UiConfigReleaseService releaseService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityDataActionService dataActionService;
    private final EntityListRuntimeService listRuntimeService;
    private final EntityFormActionService formActionService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化实体嵌入式原生表单访问适配器，保存构造参数供后续方法使用。
     *
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param dataActionService 数据动作服务依赖，保存到当前对象供后续业务方法调用
     * @param listRuntimeService 列表运行时服务依赖，保存到当前对象供后续业务方法调用
     * @param formActionService 表单动作服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedNativeFormAccessAdapter(
            UiConfigReleaseService releaseService,
            EntityDefinitionMapper definitionMapper,
            EntityDataActionService dataActionService,
            EntityListRuntimeService listRuntimeService,
            EntityFormActionService formActionService,
            ObjectMapper objectMapper) {
        this.releaseService = releaseService;
        this.definitionMapper = definitionMapper;
        this.dataActionService = dataActionService;
        this.listRuntimeService = listRuntimeService;
        this.formActionService = formActionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 复用普通 Flow 表单操作栏解析结果，不维护 Embed 按钮副本。
     *
     * @param target 目标，作为 {@code resolve} 的输入影响后续处理
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     */
    @Override
    public void requireCreateAction(Target target, String actionKey) {
        if (!Set.of("save", "saveAndStart").contains(actionKey)) {
            throw denied("原生创建按钮未开放");
        }
        RuntimeForm runtime = resolve(target);
        List<FormActionRuntimeDTO> actions =
                formActionService.resolveTrustedPublishedSnapshot(
                        runtime.form(), runtime.definition(), "create", null);
        boolean allowed = actions.stream().anyMatch(action ->
                actionKey.equals(action.getKey())
                        && "built-in".equals(action.getType())
                        && action.isVisible()
                        && action.isEnabled());
        if (!allowed) {
            throw denied("当前用户无权执行原生创建按钮");
        }
    }

    /**
     * 先校验固定 List Release 的成员关系，再走普通详情读取和固定过滤条件；任何
     * 一层拒绝都折叠为空，避免记录枚举。
     *
     * @param target 目标，作为 {@code requireTarget} 的输入影响后续处理
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param trustedContextFilters 可信上下文过滤条件，供本方法处理授权视图时使用
     * @return 匹配的授权视图；未找到时为空
     */
    @Override
    public Optional<ViewAccess> authorizeView(
            Target target,
            String recordId,
            Map<String, Object> trustedContextFilters) {
        requireTarget(target);
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException("Embed VIEW 记录坐标不完整");
        }
        // 精确解析固定 Form Release 只校验归属，不读取字段/组件。
        resolve(target);
        try {
            if (!matchesPinnedList(target, recordId, trustedContextFilters)) {
                return Optional.empty();
            }
            EntityDataDTO row = dataActionService.getDetailReadOnly(
                    target.entityCode(), recordId,
                    emptyToNull(target.listKey()),
                    pinnedListReleaseContext(target));
            if (row == null || !Objects.equals(recordId, row.getId())) {
                throw new IllegalStateException("实体详情读取返回了越界记录");
            }
            if (!matchesTrustedContextFilters(row, trustedContextFilters)) {
                return Optional.empty();
            }
            return Optional.of(new ViewAccess(
                    emptyToNull(row.getProcessInstanceId())));
        } catch (ForbiddenException denied) {
            return Optional.empty();
        }
    }

    /**
     * 解析实体嵌入式原生表单访问；输出作为后续校验或处理的输入。
     *
     * @param target 目标，作为 {@code requireTarget} 的输入影响后续处理
     * @return 解析后的实体嵌入式原生表单访问结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private RuntimeForm resolve(Target target) {
        requireTarget(target);
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                        target.formId(), target.formReleaseId(),
                        target.formReleaseVersion(),
                        UiRuntimeResolutionContext.historical(null, null));
        EntityForm form = resolved == null ? null : resolved.form();
        if (form == null || !resolved.pinned()
                || !Objects.equals(target.formId(), form.getId())
                || !Objects.equals(target.formReleaseId(), resolved.releaseId())
                || !Objects.equals(
                        target.formReleaseVersion(), resolved.releaseVersion())) {
            throw new IllegalStateException("固定表单发布版本解析结果不一致");
        }
        EntityDefinition definition = definitionMapper
                .findByEntityCode(target.entityCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Embed 目标实体不存在"));
        if (!Objects.equals(definition.getId(), form.getEntityId())) {
            throw new IllegalStateException("Embed 表单与目标实体不一致");
        }
        return new RuntimeForm(form, definition);
    }

    /**
     * 判断是否匹配固定列表；判断结果决定调用方的后续分支。
     *
     * @param target 目标，作为 {@code listRuntimeService.queryPinned} 的输入影响后续处理
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param contextFilters 上下文过滤条件，供本方法判断是否匹配固定列表时使用
     * @return 固定列表条件成立时为 true，否则为 false
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private boolean matchesPinnedList(
            Target target,
            String recordId,
            Map<String, Object> contextFilters) {
        boolean hasReleaseId = StringUtils.hasText(target.listReleaseId());
        boolean hasReleaseVersion = target.listReleaseVersion() != null;
        if (hasReleaseId != hasReleaseVersion
                || hasReleaseVersion && target.listReleaseVersion() < 1
                || hasReleaseId && !StringUtils.hasText(target.listKey())) {
            throw new IllegalStateException("Embed 列表固定坐标不完整");
        }
        if (!hasReleaseId) {
            return true;
        }
        Map<String, Object> trusted = new LinkedHashMap<>(
                contextFilters == null ? Map.of() : contextFilters);
        Object currentId = trusted.putIfAbsent("id", recordId);
        if (currentId != null
                && !Objects.equals(String.valueOf(currentId), recordId)) {
            return false;
        }
        Object currentOperator = trusted.putIfAbsent("id_op", "EQ");
        if (currentOperator != null
                && !"EQ".equalsIgnoreCase(String.valueOf(currentOperator))) {
            return false;
        }
        Object result = listRuntimeService.queryPinned(
                target.entityCode(), target.listKey(), target.listReleaseId(),
                target.listReleaseVersion(), 1, 2, Map.of(),
                Collections.unmodifiableMap(trusted));
        if (!(result instanceof PageResult<?> page)) {
            throw new IllegalStateException("固定列表详情校验没有返回分页结果");
        }
        List<?> records = page.getRecords() == null
                ? List.of() : page.getRecords();
        if (records.isEmpty()) {
            return false;
        }
        if (records.size() != 1
                || !Objects.equals(recordId, recordId(records.get(0)))) {
            throw new IllegalStateException("固定列表详情校验返回了越界记录");
        }
        return viewAllowed(records.get(0));
    }

    /**
     * 判断是否匹配可信上下文过滤条件；判断结果决定调用方的后续分支。
     *
     * @param row 行，作为 {@code standardValue} 的输入影响后续处理
     * @param contextFilters 上下文过滤条件，供本方法判断是否匹配可信上下文过滤条件时使用
     * @return 可信上下文过滤条件条件成立时为 true，否则为 false
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private boolean matchesTrustedContextFilters(
            EntityDataDTO row,
            Map<String, Object> contextFilters) {
        Map<String, Object> filters = contextFilters == null
                ? Map.of() : contextFilters;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.endsWith("_op")) {
                continue;
            }
            if (!SAFE_FIELD_CODE.matcher(key).matches()
                    || !"EQ".equalsIgnoreCase(String.valueOf(
                    filters.get(key + "_op")))) {
                throw new IllegalStateException("Embed 固定过滤条件无效");
            }
            Object actual = standardValue(row, key)
                    .orElseGet(() -> row.getData() == null
                            ? null : row.getData().get(key));
            if (!sameFilterValue(actual, entry.getValue())) {
                return false;
            }
        }
        for (String key : filters.keySet()) {
            if (key != null && key.endsWith("_op")
                    && !filters.containsKey(
                            key.substring(0, key.length() - 3))) {
                throw new IllegalStateException(
                        "Embed 固定过滤操作符缺少字段");
            }
        }
        return true;
    }

    /**
     * 处理固定列表发布版本上下文，并将结果传给后续步骤。
     *
     * @param target 目标，作为 {@code EntityListReleaseContext} 的输入影响后续处理
     * @return 处理后的固定列表发布版本上下文结果，供调用方继续处理
     */
    private static EntityListReleaseContext pinnedListReleaseContext(
            Target target) {
        if (!StringUtils.hasText(target.listReleaseId())
                || target.listReleaseVersion() == null) {
            return null;
        }
        return new EntityListReleaseContext(
                target.listReleaseId(), target.listReleaseVersion(), null);
    }

    /**
     * 判断视图允许条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理视图允许的原始输入，结果供调用方继续使用
     * @return 视图允许条件成立时为 true，否则为 false
     */
    private boolean viewAllowed(Object value) {
        if (value instanceof EntityDataDTO row) {
            Map<String, EntityActionCapabilityDTO> capabilities =
                    row.getActionCapabilities();
            EntityActionCapabilityDTO view = capabilities == null
                    ? null : capabilities.get("view");
            return view != null && view.isVisible() && view.isEnabled();
        }
        JsonNode action = objectMapper.valueToTree(value)
                .path("actionCapabilities").path("view");
        return action.isObject()
                && action.path("visible").asBoolean(false)
                && action.path("enabled").asBoolean(false);
    }

    /**
     * 记录ID；供后续追溯或审计使用。
     *
     * @param value 待记录ID的原始输入，结果供调用方继续使用
     * @return 记录后的ID文本，供调用方比较或展示
     */
    private String recordId(Object value) {
        if (value instanceof EntityDataDTO row) {
            return row.getId();
        }
        JsonNode node = objectMapper.valueToTree(value);
        return node.path("id").isValueNode()
                ? node.path("id").asText(null) : null;
    }

    /**
     * 处理标准值，并将结果传给后续步骤。
     *
     * @param row 行，供本方法处理标准值时使用
     * @param code 编码，后续用于处理标准值时定位或关联目标
     * @return 匹配的标准值；未找到时为空
     */
    private static Optional<Object> standardValue(
            EntityDataDTO row,
            String code) {
        Object value = switch (code) {
            case "id" -> row.getId();
            case "name" -> row.getName();
            case "code" -> row.getCode();
            case "status" -> row.getStatus();
            case "processStatus" -> row.getProcessStatus();
            case "processInstanceId" -> row.getProcessInstanceId();
            case "processStartTime" -> row.getProcessStartTime();
            case "processEndTime" -> row.getProcessEndTime();
            case "currentTaskId" -> row.getCurrentTaskId();
            case "currentTaskName" -> row.getCurrentTaskName();
            case "currentTaskAssignee" -> row.getCurrentTaskAssignee();
            case "submitterId" -> row.getSubmitterId();
            case "submitterName" -> row.getSubmitterName();
            case "deptId" -> row.getDeptId();
            case "deptName" -> row.getDeptName();
            case "submitTime" -> row.getSubmitTime();
            case "create_time" -> row.getCreateTime();
            case "update_time" -> row.getUpdateTime();
            case "create_by" -> row.getCreateBy();
            case "update_by" -> row.getUpdateBy();
            case "deleted" -> row.getDeleted();
            default -> null;
        };
        return Optional.ofNullable(value);
    }

    /**
     * 判断相同过滤值条件是否成立，供调用方选择后续分支。
     *
     * @param actual 实际，供本方法处理相同过滤值时使用
     * @param expected 预期，供本方法处理相同过滤值时使用
     * @return 相同过滤值条件成立时为 true，否则为 false
     */
    private static boolean sameFilterValue(Object actual, Object expected) {
        if (actual instanceof Number left && expected instanceof Number right) {
            try {
                return new java.math.BigDecimal(left.toString()).compareTo(
                        new java.math.BigDecimal(right.toString())) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return Objects.equals(actual, expected);
    }

    /**
     * 校验并获取目标；不满足约束时阻止后续处理。
     *
     * @param target 目标，作为 {@code hasText} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireTarget(Target target) {
        if (target == null
                || !StringUtils.hasText(target.entityCode())
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed 原生表单目标不完整");
        }
    }

    /**
     * 生成空截止空值文本，供后续匹配或展示。
     *
     * @param value 待处理空截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空截止空值文本，供调用方比较或展示
     */
    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 构造已拒绝异常，供调用方区分失败原因并终止后续处理。
     *
     * @param message 消息，作为 {@code OperationNotAllowedException} 的输入影响后续处理
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private static OperationNotAllowedException denied(String message) {
        return new OperationNotAllowedException(message);
    }

    /**
     * 封装运行时表单的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param form 表单，保存在对象中供后续校验、查询或展示
     * @param definition 定义，保存在对象中供后续校验、查询或展示
     */
    private record RuntimeForm(
            EntityForm form,
            EntityDefinition definition) {
    }
}
