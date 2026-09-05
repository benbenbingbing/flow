package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormAccessPort;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
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

    /** 复用普通 Flow 表单操作栏解析结果，不维护 Embed 按钮副本。 */
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

    private static EntityListReleaseContext pinnedListReleaseContext(
            Target target) {
        if (!StringUtils.hasText(target.listReleaseId())
                || target.listReleaseVersion() == null) {
            return null;
        }
        return new EntityListReleaseContext(
                target.listReleaseId(), target.listReleaseVersion(), null);
    }

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

    private String recordId(Object value) {
        if (value instanceof EntityDataDTO row) {
            return row.getId();
        }
        JsonNode node = objectMapper.valueToTree(value);
        return node.path("id").isValueNode()
                ? node.path("id").asText(null) : null;
    }

    private static Optional<Object> standardValue(
            EntityDataDTO row,
            String code) {
        Object value = switch (code) {
            case "id" -> row.getId();
            case "dataNo" -> row.getDataNo();
            case "title" -> row.getTitle();
            case "name" -> row.getName();
            case "code" -> row.getCode();
            case "status" -> row.getStatus();
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
            case "createdAt" -> row.getCreatedAt();
            case "updatedAt" -> row.getUpdatedAt();
            case "createdBy" -> row.getCreatedBy();
            case "updatedBy" -> row.getUpdatedBy();
            default -> null;
        };
        return Optional.ofNullable(value);
    }

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

    private static void requireTarget(Target target) {
        if (target == null
                || !StringUtils.hasText(target.entityCode())
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed 原生表单目标不完整");
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static OperationNotAllowedException denied(String message) {
        return new OperationNotAllowedException(message);
    }

    private record RuntimeForm(
            EntityForm form,
            EntityDefinition definition) {
    }
}
