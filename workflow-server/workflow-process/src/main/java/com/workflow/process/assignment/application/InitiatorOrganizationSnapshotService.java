package com.workflow.process.assignment.application;

import com.workflow.process.assignment.domain.RelativeOrgPositionConfig;

import com.workflow.contracts.identity.position.model.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.model.OrganizationUnitSnapshot;
import com.workflow.contracts.process.assignment.error.PersonResolutionException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发起人组织快照的捕获、内部变量投影与严格读取入口。
 */
@Service
public class InitiatorOrganizationSnapshotService {

    public static final String VARIABLE_NAME =
            "_wfInitiatorOrgSnapshotV1";

    private final OrganizationPositionDirectoryPort directoryPort;

    /**
     * 初始化{@code initiator}组织快照服务，保存构造参数供后续方法使用。
     *
     * @param directoryPort 目录端口依赖，保存到当前对象供后续业务方法调用
     */
    public InitiatorOrganizationSnapshotService(
            OrganizationPositionDirectoryPort directoryPort) {
        this.directoryPort = directoryPort;
    }

    /**
     * 在业务变量合并完成后捕获并覆盖写入可序列化快照。
     *
     * <p>本方法只由已确认引用相对职务的已部署流程调用，因此发起人
     * 缺失必须在启动事务内失败关闭，不得生成没有快照的新实例。</p>
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param initiatorIdOrUsername {@code initiator}ID或用户名，后续用于捕获可信快照时匹配或展示
     */
    public void captureTrustedSnapshot(
            Map<String, Object> variables,
            String initiatorIdOrUsername) {
        if (variables == null) {
            throw new IllegalArgumentException("流程变量容器不能为空");
        }
        variables.remove(VARIABLE_NAME);
        if (!StringUtils.hasText(initiatorIdOrUsername)) {
            throw new PersonResolutionException(
                    "INITIATOR_NOT_FOUND",
                    "相对组织职务流程缺少可识别的发起人");
        }
        InitiatorOrganizationSnapshot snapshot = directoryPort
                .captureInitiatorSnapshot(initiatorIdOrUsername.trim());
        validate(snapshot);
        variables.put(VARIABLE_NAME, toVariable(snapshot));
    }

    /**
     * 从不可信的流程变量容器中重建并校验快照，禁止静默回退实时组织链。
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @return 校验并获取后的快照结果，供调用方继续处理
     */
    public InitiatorOrganizationSnapshot requireSnapshot(
            Map<String, Object> variables) {
        Object raw = variables == null ? null : variables.get(VARIABLE_NAME);
        if (!(raw instanceof Map<?, ?> map)) {
            throw resolutionFailure(
                    "ORG_CONTEXT_NOT_SNAPSHOTTED",
                    "流程实例缺少发起人组织快照");
        }
        try {
            InitiatorOrganizationSnapshot snapshot = fromVariable(map);
            validate(snapshot);
            return snapshot;
        } catch (PersonResolutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "发起人组织快照无法解析: " + exception.getMessage(),
                    Map.of(),
                    exception);
        }
    }

    /**
     * 转换为变量；输出作为后续校验或处理的输入。
     *
     * @param snapshot 快照，作为 {@code result.put} 的输入影响后续处理
     * @return 变量键值结果，供调用方继续处理
     */
    private Map<String, Object> toVariable(
            InitiatorOrganizationSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotVersion", snapshot.snapshotVersion());
        result.put("userId", snapshot.userId());
        result.put("username", snapshot.username());
        result.put("organizationId", snapshot.organizationId());
        result.put("departmentId", snapshot.departmentId());
        List<Map<String, Object>> units = new ArrayList<>();
        for (OrganizationUnitSnapshot unit : snapshot.units()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", unit.id());
            value.put("name", unit.name());
            value.put("type", unit.type());
            value.put("businessLevelCode", unit.businessLevelCode());
            units.add(value);
        }
        result.put("units", List.copyOf(units));
        result.put("capturedAt", snapshot.capturedAt().toString());
        return result;
    }

    /**
     * 处理起始变量，并将结果传给后续步骤。
     *
     * @param raw 待处理起始变量的原始输入，结果供调用方继续使用
     * @return 处理后的起始变量结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private InitiatorOrganizationSnapshot fromVariable(Map<?, ?> raw) {
        List<OrganizationUnitSnapshot> units = new ArrayList<>();
        Object rawUnits = raw.get("units");
        if (!(rawUnits instanceof Iterable<?> values)) {
            throw new IllegalArgumentException("units 必须是数组");
        }
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> unit)) {
                throw new IllegalArgumentException("units 成员必须是对象");
            }
            units.add(new OrganizationUnitSnapshot(
                    text(unit.get("id")),
                    text(unit.get("name")),
                    text(unit.get("type")),
                    text(unit.get("businessLevelCode"))));
        }
        return new InitiatorOrganizationSnapshot(
                integer(raw.get("snapshotVersion")),
                text(raw.get("userId")),
                text(raw.get("username")),
                text(raw.get("organizationId")),
                text(raw.get("departmentId")),
                units,
                instant(raw.get("capturedAt")));
    }

    /**
     * 校验{@code initiator}组织快照；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法校验{@code initiator}组织快照时使用
     */
    private void validate(InitiatorOrganizationSnapshot snapshot) {
        if (snapshot == null) {
            throw resolutionFailure(
                    "ORG_SNAPSHOT_INVALID", "发起人组织快照为空");
        }
        if (snapshot.units().size()
                > RelativeOrgPositionConfig.MAX_CHAIN_DEPTH) {
            throw resolutionFailure(
                    "HIERARCHY_EXHAUSTED",
                    "发起人组织链超过最大 32 层");
        }
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        for (OrganizationUnitSnapshot unit : snapshot.units()) {
            if (!visited.add(unit.id())) {
                throw resolutionFailure(
                        "HIERARCHY_CYCLE",
                        "发起人组织快照包含重复节点: " + unit.id());
            }
        }
        if (!visited.contains(snapshot.organizationId())) {
            throw resolutionFailure(
                    "ORG_SNAPSHOT_INVALID",
                    "组织快照链不包含 organizationId");
        }
        if (StringUtils.hasText(snapshot.departmentId())
                && !visited.contains(snapshot.departmentId())) {
            throw resolutionFailure(
                    "ORG_SNAPSHOT_INVALID",
                    "组织快照链不包含 departmentId");
        }
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int integer(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("快照版本必须是整数");
        }
        return number.intValue();
    }

    /**
     * 处理绝对时间，并将结果传给后续步骤。
     *
     * @param value 待处理绝对时间的原始输入，结果供调用方继续使用
     * @return 处理后的绝对时间结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        String raw = text(value);
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("快照时间不能为空");
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return OffsetDateTime.parse(raw).toInstant();
        }
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

    /**
     * 构造解析失败异常，供调用方区分失败原因。
     *
     * @param code 编码，后续用于处理解析失败时定位或关联目标
     * @param message 消息，作为 {@code PersonResolutionException} 的输入影响后续处理
     * @return 处理后的解析失败结果，供调用方继续处理
     */
    private PersonResolutionException resolutionFailure(
            String code,
            String message) {
        return new PersonResolutionException(code, message);
    }
}
