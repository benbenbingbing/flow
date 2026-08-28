package com.workflow.process.assignment.relative;

import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
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

    public InitiatorOrganizationSnapshotService(
            OrganizationPositionDirectoryPort directoryPort) {
        this.directoryPort = directoryPort;
    }

    /**
     * 在业务变量合并完成后捕获并覆盖写入可序列化快照。
     *
     * <p>本方法只由已确认引用相对职务的已部署流程调用，因此发起人
     * 缺失必须在启动事务内失败关闭，不得生成没有快照的新实例。</p>
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

    private int integer(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("快照版本必须是整数");
        }
        return number.intValue();
    }

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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private PersonResolutionException resolutionFailure(
            String code,
            String message) {
        return new PersonResolutionException(code, message);
    }
}
