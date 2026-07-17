package com.workflow.service.permission;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.EntityListPermissionSaveRequest;
import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.dto.permission.MatchConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListPermission;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 实体列表数据权限规则服务。
 */
@Service
public class EntityListPermissionService
        extends ServiceImpl<EntityListPermissionMapper, EntityListPermission> {

    private static final int MAX_MATCH_DEPTH = 6;
    private static final int MAX_MATCH_NODES = 100;
    private static final Set<String> BUILTIN_MATCH_TYPES = Set.of(
            "ALL_USERS", "USER", "ROLE", "GROUP", "DEPT", "ORG");

    private final EntityListPermissionMapper permissionMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final ObjectMapper objectMapper;
    private final PermissionSqlBuilder sqlBuilder;
    private final List<EntityDataPermissionMatchProvider> matchProviders;

    public EntityListPermissionService(
            EntityListPermissionMapper permissionMapper,
            EntityDefinitionMapper definitionMapper,
            EntityListConfigMapper listConfigMapper,
            ObjectMapper objectMapper,
            PermissionSqlBuilder sqlBuilder,
            List<EntityDataPermissionMatchProvider> matchProviders) {
        this.permissionMapper = permissionMapper;
        this.definitionMapper = definitionMapper;
        this.listConfigMapper = listConfigMapper;
        this.objectMapper = objectMapper;
        this.sqlBuilder = sqlBuilder;
        this.matchProviders = matchProviders == null ? List.of() : matchProviders;
    }

    public List<EntityListPermission> findByEntityCode(String entityCode) {
        return permissionMapper.findByEntityCode(entityCode);
    }

    public EntityListPermission create(
            EntityListPermissionSaveRequest request,
            String createdBy) {
        EntityListPermission permission = normalize(request, null);
        permission.setCreatedBy(createdBy);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);
        return permission;
    }

    public EntityListPermission update(
            String id,
            EntityListPermissionSaveRequest request) {
        EntityListPermission existing = permissionMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("数据权限规则不存在");
        }
        EntityListPermission permission = normalize(request, existing);
        permission.setId(id);
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
        return permissionMapper.selectById(id);
    }

    public void toggleEnabled(String id) {
        EntityListPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new IllegalArgumentException("数据权限规则不存在");
        }
        permission.setEnabled(permission.getEnabled() != null && permission.getEnabled() == 1 ? 0 : 1);
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(permission);
    }

    private EntityListPermission normalize(
            EntityListPermissionSaveRequest request,
            EntityListPermission existing) {
        if (request == null) {
            throw new IllegalArgumentException("数据权限规则不能为空");
        }
        String entityCode = textOrElse(
                request.getEntityCode(),
                existing == null ? null : existing.getEntityCode());
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        if (!StringUtils.hasText(request.getRuleName())) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        validateListScope(definition, request.getListConfigId());

        MatchConfigDTO match = parseMatchConfig(request.getMatchConfig());
        validateMatchConfig(match);
        FilterConfigDTO filter = parseFilterConfig(request.getFilterConfig());
        sqlBuilder.validateFilter(entityCode, filter);

        String effect = normalized(request.getRuleEffect(), "ALLOW");
        if (!Set.of("ALLOW", "DENY").contains(effect)) {
            throw new IllegalArgumentException("规则效果只能是 ALLOW 或 DENY");
        }
        String combineMode = normalized(request.getCombineMode(), "UNION");
        if (!Set.of("UNION", "INTERSECT").contains(combineMode)) {
            throw new IllegalArgumentException("合并方式只能是 UNION 或 INTERSECT");
        }

        EntityListPermission permission = existing == null
                ? new EntityListPermission()
                : existing;
        permission.setEntityCode(entityCode);
        permission.setRuleName(request.getRuleName().trim());
        permission.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        permission.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        permission.setListConfigId(
                StringUtils.hasText(request.getListConfigId())
                        ? request.getListConfigId()
                        : null);
        permission.setRuleEffect(effect);
        permission.setCombineMode(combineMode);
        permission.setStopProcessing(
                request.getStopProcessing() != null && request.getStopProcessing() == 1 ? 1 : 0);
        permission.setMatchConfig(writeJson(match, "匹配配置"));
        permission.setFilterConfig(writeJson(filter, "数据过滤配置"));
        return permission;
    }

    private MatchConfigDTO parseMatchConfig(String json) {
        if (!StringUtils.hasText(json)) {
            MatchConfigDTO config = new MatchConfigDTO();
            MatchConfigDTO.MatchConditionDTO condition = new MatchConfigDTO.MatchConditionDTO();
            condition.setScopeType("ALL_USERS");
            config.setConditions(List.of(condition));
            return config;
        }
        try {
            return objectMapper.readValue(json, MatchConfigDTO.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("匹配配置 JSON 格式不正确", e);
        }
    }

    private FilterConfigDTO parseFilterConfig(String json) {
        if (!StringUtils.hasText(json)) {
            FilterConfigDTO filter = new FilterConfigDTO();
            filter.setType("PERSONAL");
            return filter;
        }
        try {
            return objectMapper.readValue(json, FilterConfigDTO.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("数据过滤配置 JSON 格式不正确", e);
        }
    }

    private void validateMatchConfig(MatchConfigDTO match) {
        if (match == null) {
            throw new IllegalArgumentException("匹配配置不能为空");
        }
        int[] count = {0};
        if (match.getRoot() != null) {
            validateMatchNode(match.getRoot(), 1, count);
            return;
        }
        List<MatchConfigDTO.MatchConditionDTO> conditions = match.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("至少配置一个适用用户条件");
        }
        String logic = normalized(match.getLogic(), "OR");
        if (!Set.of("AND", "OR").contains(logic)) {
            throw new IllegalArgumentException("匹配逻辑只能是 AND 或 OR");
        }
        conditions.forEach(this::validateMatchCondition);
    }

    private void validateMatchNode(
            MatchConfigDTO.MatchNodeDTO node,
            int depth,
            int[] count) {
        if (node == null) {
            throw new IllegalArgumentException("匹配条件节点不能为空");
        }
        if (depth > MAX_MATCH_DEPTH || ++count[0] > MAX_MATCH_NODES) {
            throw new IllegalArgumentException("适用用户条件过于复杂");
        }
        if ("GROUP".equalsIgnoreCase(node.getType())) {
            if (!Set.of("AND", "OR").contains(normalized(node.getLogic(), ""))) {
                throw new IllegalArgumentException("匹配条件组逻辑只能是 AND 或 OR");
            }
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                throw new IllegalArgumentException("匹配条件组不能为空");
            }
            for (MatchConfigDTO.MatchNodeDTO child : node.getChildren()) {
                validateMatchNode(child, depth + 1, count);
            }
            return;
        }
        validateMatchCondition(node.getCondition());
    }

    private void validateMatchCondition(MatchConfigDTO.MatchConditionDTO condition) {
        if (condition == null || !StringUtils.hasText(condition.getScopeType())) {
            throw new IllegalArgumentException("适用用户条件缺少范围类型");
        }
        String scopeType = normalized(condition.getScopeType(), "");
        if ("EXPRESSION".equals(scopeType)) {
            throw new IllegalArgumentException("数据权限不再支持自由表达式，请改用结构化用户范围");
        }
        if (!BUILTIN_MATCH_TYPES.contains(scopeType)) {
            EntityDataPermissionMatchProvider provider = matchProviders.stream()
                    .filter(item -> item.getScopeType().equalsIgnoreCase(scopeType))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "不支持的适用用户范围: " + condition.getScopeType()));
            provider.validate(condition);
            return;
        }
        if (!"ALL_USERS".equals(scopeType)
                && (condition.getTargetIds() == null || condition.getTargetIds().isEmpty())) {
            throw new IllegalArgumentException("适用用户范围未选择目标");
        }
        if (!Set.of("ANY", "ALL").contains(normalized(condition.getOperator(), "ANY"))) {
            throw new IllegalArgumentException("匹配方式只能是 ANY 或 ALL");
        }
    }

    private void validateListScope(
            EntityDefinition definition,
            String listConfigId) {
        if (!StringUtils.hasText(listConfigId)) {
            return;
        }
        EntityListConfig config = listConfigMapper.selectById(listConfigId);
        if (config == null || !definition.getId().equals(config.getEntityId())) {
            throw new IllegalArgumentException("所选列表不属于当前实体");
        }
    }

    private String writeJson(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "序列化失败", e);
        }
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.toUpperCase(Locale.ROOT)
                : fallback;
    }

    private String textOrElse(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
