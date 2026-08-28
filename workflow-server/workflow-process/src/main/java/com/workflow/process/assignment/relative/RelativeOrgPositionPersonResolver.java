package com.workflow.process.assignment.relative;

import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.PositionDefinitionView;
import com.workflow.contracts.identity.position.PositionDirectoryResultCode;
import com.workflow.contracts.identity.position.PositionHolderResolution;
import com.workflow.contracts.identity.position.PositionHolderView;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidator;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig.Anchor;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig.LookupMode;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig.MultipleMatchPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按发起时冻结的组织链，在节点激活时查询当前有效职务任职人。
 */
@Component("relativeOrgPositionPersonResolver")
public class RelativeOrgPositionPersonResolver
        implements PersonResolver, PersonResolverConfigurationValidator {

    private static final PersonResolverDescriptor DESCRIPTOR =
            new PersonResolverDescriptor(
                    RelativeOrgPositionConfig.RESOLVER_CODE,
                    "相对组织职务",
                    "按流程发起人的冻结组织链查找节点激活时的有效职务人员。",
                    1,
                    1,
                    Set.of(
                            PersonResolveUsage.ASSIGNEE,
                            PersonResolveUsage.CANDIDATE,
                            PersonResolveUsage.MULTI_INSTANCE),
                    descriptorSchema(),
                    false);

    private final OrganizationPositionDirectoryPort directoryPort;
    private final InitiatorOrganizationSnapshotService snapshotService;

    public RelativeOrgPositionPersonResolver(
            OrganizationPositionDirectoryPort directoryPort,
            InitiatorOrganizationSnapshotService snapshotService) {
        this.directoryPort = directoryPort;
        this.snapshotService = snapshotService;
    }

    @Override
    public PersonResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String resolverCode() {
        return RelativeOrgPositionConfig.RESOLVER_CODE;
    }

    /**
     * 发布阶段只验证静态职务与层级配置，不会尝试证明未来每个组织节点都有任职人。
     */
    @Override
    public void validate(
            PersonResolverConfigurationValidationRequest request) {
        RelativeOrgPositionConfig config =
                RelativeOrgPositionConfig.parse(request.extraParams());
        config.validateAssignmentMode(
                request.assignmentMode(), request.multiInstance());
        PositionDefinitionView position;
        try {
            position = directoryPort.requireEnabledPosition(
                    config.positionCode());
        } catch (OrganizationPositionDirectoryException exception) {
            throw new IllegalArgumentException(
                    exception.errorCode().name() + ": " + exception.getMessage(),
                    exception);
        }
        validateApplicableUnitTypes(position, config);
        if (config.hierarchy().mode() == LookupMode.BUSINESS_LEVEL
                && !directoryPort.isOrganizationBusinessLevelEnabled(
                config.hierarchy().businessLevelCode())) {
            throw new IllegalArgumentException(
                    "BUSINESS_LEVEL_NOT_FOUND: 业务层级不存在或已停用: "
                            + config.hierarchy().businessLevelCode());
        }
    }

    /**
     * 运行时解析始终以部署 BPMN 中的配置和实例内快照为权威输入。
     */
    @Override
    public PersonResolveResult resolve(PersonResolveRequest request) {
        RelativeOrgPositionConfig config =
                RelativeOrgPositionConfig.parse(request.extraParams());
        InitiatorOrganizationSnapshot snapshot = snapshotService
                .requireSnapshot(request.variables());
        validateInitiator(request, snapshot);
        ResolvedPosition resolved = resolvePosition(config, snapshot);
        return new PersonResolveResult(
                resolved.holders().stream()
                        .map(PositionHolderView::username)
                        .map(com.workflow.contracts.identity.resolver.PersonPrincipal::user)
                        .toList(),
                resolved.trace().warnings());
    }

    /**
     * 使用样例用户捕获当前组织快照，并调用与运行时完全相同的层级和多人策略。
     */
    public RelativeOrgPositionPreview preview(
            String sampleUserIdOrUsername,
            Map<String, Object> extraParams) {
        Map<String, Object> variables = new LinkedHashMap<>();
        snapshotService.captureTrustedSnapshot(
                variables, sampleUserIdOrUsername);
        InitiatorOrganizationSnapshot snapshot =
                snapshotService.requireSnapshot(variables);
        RelativeOrgPositionConfig config =
                RelativeOrgPositionConfig.parse(extraParams);
        ResolvedPosition resolved = resolvePosition(config, snapshot);
        List<OrganizationUnitSnapshot> chain = chainFromAnchor(
                snapshot, config.anchor());
        OrganizationUnitSnapshot anchor = chain.get(0);
        ResolutionTrace trace = resolved.trace();
        RelativeOrgPositionPreview.MatchedUnit matched = null;
        if (trace.matchedUnitId != null) {
            for (int depth = 0; depth < chain.size(); depth++) {
                OrganizationUnitSnapshot unit = chain.get(depth);
                if (trace.matchedUnitId.equals(unit.id())) {
                    matched = new RelativeOrgPositionPreview.MatchedUnit(
                            unit.id(), unit.name(), unit.type(), depth);
                    break;
                }
            }
        }
        return new RelativeOrgPositionPreview(
                anchor,
                trace.scannedUnits(),
                matched,
                resolved.holders(),
                "RESOLVED",
                null,
                null,
                trace.warnings(),
                trace.directoryRevision);
    }

    private ResolvedPosition resolvePosition(
            RelativeOrgPositionConfig config,
            InitiatorOrganizationSnapshot snapshot) {
        requireEnabledPosition(config.positionCode());

        List<OrganizationUnitSnapshot> chain = chainFromAnchor(
                snapshot, config.anchor());
        ResolutionTrace trace = new ResolutionTrace();
        List<PositionHolderView> holders = switch (
                config.hierarchy().mode()) {
            case SELF -> resolveExact(
                    config, chain, 0, Instant.now(), trace);
            case FIXED_ANCESTOR -> resolveExact(
                    config,
                    chain,
                    config.hierarchy().ancestorHops(),
                    Instant.now(),
                    trace);
            case NEAREST_WITH_HOLDER -> resolveNearest(
                    config, chain, Instant.now(), trace);
            case BUSINESS_LEVEL -> resolveBusinessLevel(
                    config, chain, Instant.now(), trace);
        };
        List<PositionHolderView> selected = applyMultiplePolicy(
                config, holders, trace);
        return new ResolvedPosition(selected, trace);
    }

    private void validateInitiator(
            PersonResolveRequest request,
            InitiatorOrganizationSnapshot snapshot) {
        if (!StringUtils.hasText(request.initiatorId())) {
            throw failure(
                    "INITIATOR_NOT_FOUND",
                    "人员解析请求缺少流程发起人",
                    Map.of());
        }
        String initiator = request.initiatorId().trim();
        if (!initiator.equals(snapshot.userId())
                && !initiator.equals(snapshot.username())) {
            throw failure(
                    "ORG_SNAPSHOT_INVALID",
                    "组织快照所属用户与流程发起人不一致",
                    Map.of("initiator", initiator));
        }
    }

    private void requireEnabledPosition(String positionCode) {
        try {
            directoryPort.requireEnabledPosition(positionCode);
        } catch (OrganizationPositionDirectoryException exception) {
            throw failure(
                    exception.errorCode().name(),
                    exception.getMessage(),
                    Map.of("positionCode", positionCode),
                    exception);
        }
    }

    private List<OrganizationUnitSnapshot> chainFromAnchor(
            InitiatorOrganizationSnapshot snapshot,
            Anchor anchor) {
        String anchorId = anchor == Anchor.DEPARTMENT
                ? snapshot.departmentId() : snapshot.organizationId();
        if (!StringUtils.hasText(anchorId)) {
            String code = anchor == Anchor.DEPARTMENT
                    ? "INITIATOR_DEPARTMENT_MISSING"
                    : "INITIATOR_ORGANIZATION_MISSING";
            throw failure(code, "发起人缺少所需组织锚点", Map.of());
        }
        for (int index = 0; index < snapshot.units().size(); index++) {
            OrganizationUnitSnapshot unit = snapshot.units().get(index);
            if (anchorId.equals(unit.id())) {
                String expectedType = anchor == Anchor.DEPARTMENT
                        ? "dept" : "org";
                if (!expectedType.equalsIgnoreCase(unit.type())) {
                    throw failure(
                            "ORG_SNAPSHOT_INVALID",
                            "组织锚点类型与快照不一致",
                            Map.of("anchorUnitId", anchorId));
                }
                return List.copyOf(snapshot.units().subList(
                        index, snapshot.units().size()));
            }
        }
        throw failure(
                "ORG_SNAPSHOT_INVALID",
                "组织快照不包含所需锚点",
                Map.of("anchorUnitId", anchorId));
    }

    private List<PositionHolderView> resolveExact(
            RelativeOrgPositionConfig config,
            List<OrganizationUnitSnapshot> chain,
            int depth,
            Instant asOf,
            ResolutionTrace trace) {
        if (depth < 0 || depth >= chain.size()) {
            throw failure(
                    "HIERARCHY_EXHAUSTED",
                    "固定上溯层数已超过冻结组织链",
                    trace.details(config, null));
        }
        // FIXED_ANCESTOR 不能跳过已删除/停用的中间节点，因此逐边验证到目标为止。
        for (int currentDepth = 0;
                currentDepth <= depth;
                currentDepth++) {
            requireActiveUnit(chain.get(currentDepth), currentDepth, trace);
        }
        OrganizationUnitSnapshot unit = chain.get(depth);
        requireEligibleUnit(config, unit, depth, trace);
        return requireHolders(config, unit, asOf, depth, trace);
    }

    private List<PositionHolderView> resolveNearest(
            RelativeOrgPositionConfig config,
            List<OrganizationUnitSnapshot> chain,
            Instant asOf,
            ResolutionTrace trace) {
        int startLevel = config.hierarchy().startLevel();
        int maxHops = config.hierarchy().maxHops();
        // startLevel 只控制从哪一层开始查找任职人，不表示可以跳过锚点到
        // 起始层之间的组织存活性校验。
        for (int depth = 0;
                depth < startLevel && depth < chain.size();
                depth++) {
            requireActiveUnit(chain.get(depth), depth, trace);
        }
        for (int depth = startLevel;
                depth < chain.size() && depth <= maxHops;
                depth++) {
            OrganizationUnitSnapshot unit = chain.get(depth);
            requireActiveUnit(unit, depth, trace);
            if (!eligible(config, unit)) {
                trace.scanned(unit, depth, "UNIT_TYPE_SKIPPED");
                continue;
            }
            PositionHolderResolution resolution = query(
                    config, unit, asOf, depth, trace);
            if (resolution.resultCode()
                    == PositionDirectoryResultCode.NO_ACTIVE_HOLDER) {
                continue;
            }
            if (resolution.resolved()) {
                trace.matchedUnitId = unit.id();
                return resolution.holders();
            }
            throw directoryFailure(config, resolution, trace);
        }
        throw failure(
                "POSITION_NO_ACTIVE_HOLDER",
                "在允许的冻结组织链范围内未找到有效任职人",
                trace.details(config, null));
    }

    private List<PositionHolderView> resolveBusinessLevel(
            RelativeOrgPositionConfig config,
            List<OrganizationUnitSnapshot> chain,
            Instant asOf,
            ResolutionTrace trace) {
        int maxHops = config.hierarchy().maxHops();
        List<Integer> matches = new ArrayList<>();
        for (int depth = 0;
                depth < chain.size() && depth <= maxHops;
                depth++) {
            OrganizationUnitSnapshot unit = chain.get(depth);
            if (StringUtils.hasText(unit.businessLevelCode())
                    && config.hierarchy().businessLevelCode()
                    .equalsIgnoreCase(unit.businessLevelCode())) {
                matches.add(depth);
            }
        }
        if (matches.isEmpty()) {
            throw failure(
                    "BUSINESS_LEVEL_NOT_FOUND",
                    "冻结组织链中不存在指定业务层级: "
                            + config.hierarchy().businessLevelCode(),
                    trace.details(config, null));
        }
        if (matches.size() > 1) {
            trace.warnings.add(
                    "DUPLICATE_BUSINESS_LEVEL_ON_PATH: "
                            + config.hierarchy().businessLevelCode());
        }
        int depth = matches.get(0);
        for (int currentDepth = 0;
                currentDepth <= depth;
                currentDepth++) {
            requireActiveUnit(chain.get(currentDepth), currentDepth, trace);
        }
        OrganizationUnitSnapshot unit = chain.get(depth);
        requireEligibleUnit(config, unit, depth, trace);
        return requireHolders(config, unit, asOf, depth, trace);
    }

    private List<PositionHolderView> requireHolders(
            RelativeOrgPositionConfig config,
            OrganizationUnitSnapshot unit,
            Instant asOf,
            int depth,
            ResolutionTrace trace) {
        PositionHolderResolution resolution = query(
                config, unit, asOf, depth, trace);
        if (resolution.resolved()) {
            trace.matchedUnitId = unit.id();
            return resolution.holders();
        }
        if (resolution.resultCode()
                == PositionDirectoryResultCode.NO_ACTIVE_HOLDER) {
            throw failure(
                    "POSITION_NO_ACTIVE_HOLDER",
                    "目标组织节点没有有效职务任职人",
                    trace.details(config, unit.id()));
        }
        throw directoryFailure(config, resolution, trace);
    }

    private PositionHolderResolution query(
            RelativeOrgPositionConfig config,
            OrganizationUnitSnapshot unit,
            Instant asOf,
            int depth,
            ResolutionTrace trace) {
        PositionHolderResolution resolution = directoryPort
                .findEffectiveHolders(
                        config.positionCode(), unit.id(), asOf);
        if (resolution == null || resolution.resultCode() == null) {
            throw failure(
                    "ORG_SNAPSHOT_INVALID",
                    "组织职务目录返回了无效结果",
                    trace.details(config, unit.id()));
        }
        trace.directoryRevision = resolution.directoryRevision();
        trace.scanned(unit, depth, resolution.resultCode().name());
        return resolution;
    }

    private PersonResolutionException directoryFailure(
            RelativeOrgPositionConfig config,
            PositionHolderResolution resolution,
            ResolutionTrace trace) {
        String code = switch (resolution.resultCode()) {
            case POSITION_NOT_FOUND -> "POSITION_NOT_FOUND";
            case POSITION_DISABLED -> "POSITION_DISABLED";
            case ORGANIZATION_UNIT_NOT_FOUND,
                    ORGANIZATION_UNIT_DISABLED -> "ORG_SNAPSHOT_INVALID";
            case POSITION_UNIT_TYPE_MISMATCH -> "POSITION_UNIT_TYPE_MISMATCH";
            case RESOLVED, NO_ACTIVE_HOLDER -> "POSITION_NO_ACTIVE_HOLDER";
        };
        return failure(
                code,
                "组织职务目录查询失败: " + resolution.resultCode(),
                trace.details(config, resolution.organizationUnitId()));
    }

    private List<PositionHolderView> applyMultiplePolicy(
            RelativeOrgPositionConfig config,
            List<PositionHolderView> rawHolders,
            ResolutionTrace trace) {
        LinkedHashMap<String, PositionHolderView> unique =
                new LinkedHashMap<>();
        for (PositionHolderView holder : rawHolders) {
            if (holder != null && StringUtils.hasText(holder.username())) {
                unique.putIfAbsent(holder.username().trim(), holder);
            }
        }
        List<PositionHolderView> holders = List.copyOf(unique.values());
        if (holders.isEmpty()) {
            throw failure(
                    "POSITION_NO_ACTIVE_HOLDER",
                    "职务查询结果中没有可用用户",
                    trace.details(config, trace.matchedUnitId));
        }
        if (config.multipleMatchPolicy() == MultipleMatchPolicy.ALL) {
            return holders;
        }
        if (holders.size() == 1) {
            return List.of(holders.get(0));
        }
        if (config.multipleMatchPolicy()
                == MultipleMatchPolicy.PRIMARY_OR_ERROR) {
            List<PositionHolderView> primaries = holders.stream()
                    .filter(PositionHolderView::primary)
                    .toList();
            if (primaries.size() == 1) {
                return List.of(primaries.get(0));
            }
        }
        throw failure(
                "AMBIGUOUS_POSITION_HOLDER",
                "DIRECT 职务审批无法确定唯一任职人",
                trace.details(config, trace.matchedUnitId));
    }

    private boolean eligible(
            RelativeOrgPositionConfig config,
            OrganizationUnitSnapshot unit) {
        return StringUtils.hasText(unit.type())
                && config.hierarchy().eligibleUnitTypes().contains(
                unit.type().trim().toLowerCase(Locale.ROOT));
    }

    private void requireEligibleUnit(
            RelativeOrgPositionConfig config,
            OrganizationUnitSnapshot unit,
            int depth,
            ResolutionTrace trace) {
        if (!eligible(config, unit)) {
            trace.scanned(unit, depth, "UNIT_TYPE_MISMATCH");
            throw failure(
                    "POSITION_UNIT_TYPE_MISMATCH",
                    "目标组织节点类型不在 eligibleUnitTypes 中",
                    trace.details(config, unit.id()));
        }
    }

    /**
     * 冻结链只冻结路由 ID，不冻结节点存活状态；每次跨越前都必须向目录确认
     * 该 ID 仍活跃，否则 Fail Closed 而不是沿冻结的上一层 ID 继续路由。
     */
    private void requireActiveUnit(
            OrganizationUnitSnapshot frozenUnit,
            int depth,
            ResolutionTrace trace) {
        final OrganizationUnitStateView current;
        try {
            current = directoryPort.requireActiveOrganizationUnit(
                    frozenUnit.id());
        } catch (OrganizationPositionDirectoryException exception) {
            throw failure(
                    "ORG_SNAPSHOT_INVALID",
                    exception.getMessage(),
                    Map.of(
                            "organizationUnitId", frozenUnit.id(),
                            "directoryReason", exception.errorCode().name()),
                    exception);
        }
        if (current == null
                || !frozenUnit.id().equals(current.id())
                || !frozenUnit.type().equalsIgnoreCase(current.type())) {
            throw failure(
                    "ORG_SNAPSHOT_INVALID",
                    "冻结组织节点与实时目录类型不一致",
                    Map.of("organizationUnitId", frozenUnit.id()));
        }
        trace.directoryRevision = current.directoryRevision();
        trace.scanned(frozenUnit, depth, "UNIT_ACTIVE");
    }

    private void validateApplicableUnitTypes(
            PositionDefinitionView position,
            RelativeOrgPositionConfig config) {
        String applicable = position.applicableUnitType() == null
                ? "" : position.applicableUnitType().trim().toUpperCase(Locale.ROOT);
        if ("ANY".equals(applicable)) {
            return;
        }
        String allowed = switch (applicable) {
            case "ORG" -> "org";
            case "DEPT" -> "dept";
            default -> throw new IllegalArgumentException(
                    "POSITION_UNIT_TYPE_MISMATCH: 职务适用单位类型无效: "
                            + position.applicableUnitType());
        };
        if (!config.hierarchy().eligibleUnitTypes().stream()
                .allMatch(allowed::equals)) {
            throw new IllegalArgumentException(
                    "POSITION_UNIT_TYPE_MISMATCH: eligibleUnitTypes 超出职务适用范围 "
                            + applicable);
        }
    }

    private PersonResolutionException failure(
            String code,
            String message,
            Map<String, Object> details) {
        return new PersonResolutionException(code, message, details);
    }

    private PersonResolutionException failure(
            String code,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        return new PersonResolutionException(code, message, details, cause);
    }

    private static Map<String, Object> descriptorSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("schemaVersion", RelativeOrgPositionConfig.SCHEMA_VERSION);
        schema.put("subject", List.of("PROCESS_INITIATOR"));
        schema.put("anchor", List.of("DEPARTMENT", "ORGANIZATION"));
        schema.put("lookupModes", List.of(
                "SELF",
                "FIXED_ANCESTOR",
                "NEAREST_WITH_HOLDER",
                "BUSINESS_LEVEL"));
        schema.put("multipleMatchPolicies", List.of(
                "ERROR", "PRIMARY_OR_ERROR", "ALL"));
        return Map.copyOf(schema);
    }

    /** 将扫描轨迹保留在结构化失败中，便于 incident 处置和试算解释。 */
    private static final class ResolutionTrace {
        private final List<RelativeOrgPositionPreview.ScannedUnit> scannedUnits =
                new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String matchedUnitId;
        private String directoryRevision;

        private void scanned(
                OrganizationUnitSnapshot unit,
                int depth,
                String result) {
            RelativeOrgPositionPreview.ScannedUnit value =
                    new RelativeOrgPositionPreview.ScannedUnit(
                            unit.id(), unit.name(), unit.type(), depth, result);
            // 活跃性检查与任职查询是同一层的两个阶段，试算轨迹只保留最终状态。
            for (int index = 0; index < scannedUnits.size(); index++) {
                RelativeOrgPositionPreview.ScannedUnit previous =
                        scannedUnits.get(index);
                if (previous.depth() == depth
                        && previous.id().equals(unit.id())) {
                    scannedUnits.set(index, value);
                    return;
                }
            }
            scannedUnits.add(value);
        }

        private List<RelativeOrgPositionPreview.ScannedUnit> scannedUnits() {
            return List.copyOf(scannedUnits);
        }

        private List<String> warnings() {
            return List.copyOf(warnings);
        }

        private Map<String, Object> details(
                RelativeOrgPositionConfig config,
                String unitId) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("positionCode", config.positionCode());
            details.put("lookupMode", config.hierarchy().mode().name());
            details.put("organizationUnitId", unitId);
            details.put(
                    "scannedUnits",
                    scannedUnits.stream()
                            .map(value -> value.depth()
                                    + ":" + value.id()
                                    + ":" + value.result())
                            .toList());
            details.put("directoryRevision", directoryRevision);
            return details;
        }
    }

    private record ResolvedPosition(
            List<PositionHolderView> holders,
            ResolutionTrace trace) {
    }
}
