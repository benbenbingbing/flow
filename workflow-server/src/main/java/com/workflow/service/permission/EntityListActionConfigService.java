package com.workflow.service.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体列表按钮配置解析、默认值和校验。
 */
@Service
@RequiredArgsConstructor
public class EntityListActionConfigService {

    private static final int MAX_RULE_DEPTH = 6;
    private static final int MAX_RULE_NODES = 100;
    private static final TypeReference<List<Map<String, Object>>> BUTTON_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityListConfigMapper configMapper;
    private final List<EntityActionRuleConditionProvider> conditionProviders;
    private final List<EntityPermissionOptionProvider> permissionOptionProviders;

    public EntityListConfig resolveListConfig(String entityCode, String listKey) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (definition == null) {
            return null;
        }
        if (StringUtils.hasText(listKey)) {
            return configMapper.findByEntityIdAndListKey(definition.getId(), listKey);
        }
        List<EntityListConfig> configs = configMapper.findByEntityId(definition.getId());
        return configs.stream()
                .filter(config -> Boolean.TRUE.equals(config.getIsDefault()))
                .findFirst()
                .orElse(configs.isEmpty() ? null : configs.get(0));
    }

    public String resolveListConfigId(String entityCode, String listKey) {
        EntityListConfig config = resolveListConfig(entityCode, listKey);
        return config == null ? null : config.getId();
    }

    public List<Map<String, Object>> resolveRowButtons(EntityListConfig config, String entityCode) {
        return parseAndNormalize(
                config == null ? null : config.getRowActionConfig(),
                false,
                entityCode,
                false);
    }

    public List<Map<String, Object>> resolveToolbarButtons(EntityListConfig config, String entityCode) {
        return parseAndNormalize(
                config == null ? null : config.getToolbarConfig(),
                true,
                entityCode,
                false);
    }

    public Map<String, Object> resolveButton(String entityCode, String listKey, String buttonKey) {
        EntityListConfig config = resolveListConfig(entityCode, listKey);
        List<Map<String, Object>> buttons = isToolbarKey(buttonKey)
                ? resolveToolbarButtons(config, entityCode)
                : resolveRowButtons(config, entityCode);
        return buttons.stream()
                .filter(button -> buttonKey.equals(asString(button.get("key"))))
                .findFirst()
                .orElse(null);
    }

    public void normalizeForSave(EntityListConfig config) {
        config.setToolbarConfig(writeButtons(parseAndNormalize(
                config.getToolbarConfig(), true, config.getEntityCode(), true)));
        config.setRowActionConfig(writeButtons(parseAndNormalize(
                config.getRowActionConfig(), false, config.getEntityCode(), true)));
    }

    public boolean normalizeForMigration(EntityListConfig config) {
        String normalizedToolbar = writeButtons(parseAndNormalize(
                config.getToolbarConfig(), true, config.getEntityCode(), false));
        String normalizedRow = writeButtons(parseAndNormalize(
                config.getRowActionConfig(), false, config.getEntityCode(), false));
        boolean changed = !normalizedToolbar.equals(config.getToolbarConfig())
                || !normalizedRow.equals(config.getRowActionConfig());
        config.setToolbarConfig(normalizedToolbar);
        config.setRowActionConfig(normalizedRow);
        return changed;
    }

    public EntityActionRuleDTO readRule(Map<String, Object> button) {
        Object rawRule = button == null ? null : button.get("availabilityRule");
        if (rawRule == null) {
            return null;
        }
        return objectMapper.convertValue(rawRule, EntityActionRuleDTO.class);
    }

    public String permissionFor(String entityCode, Map<String, Object> button) {
        if (button == null) {
            return null;
        }
        String configured = asString(button.get("perm"));
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        EntityPermissionAction action = EntityPermissionAction.fromButtonKey(asString(button.get("key")));
        return action == null ? null : action.permissionCode(entityCode);
    }

    public String unavailableBehavior(Map<String, Object> button) {
        EntityActionRuleDTO rule = readRule(button);
        if (rule != null && StringUtils.hasText(rule.getUnavailableBehavior())) {
            return rule.getUnavailableBehavior().toUpperCase();
        }
        return isToolbarKey(asString(button == null ? null : button.get("key"))) ? "DISABLE" : "HIDE";
    }

    private List<Map<String, Object>> parseAndNormalize(
            String json,
            boolean toolbar,
            String entityCode,
            boolean strictCustomPermission) {
        List<Map<String, Object>> buttons = parseButtons(json);
        if (buttons.isEmpty()) {
            buttons = defaultButtons(toolbar);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> original : buttons) {
            Map<String, Object> button = new LinkedHashMap<>(original);
            String key = asString(button.get("key"));
            String type = asString(button.get("type"));
            boolean enabled = !Boolean.FALSE.equals(button.get("enabled"));
            EntityPermissionAction action = EntityPermissionAction.fromButtonKey(key);

            if (action != null) {
                button.put("perm", action.permissionCode(entityCode));
                if (!button.containsKey("availabilityRule")) {
                    EntityActionRuleDTO defaultRule = defaultRule(key);
                    if (defaultRule != null) {
                        button.put("availabilityRule", objectMapper.convertValue(defaultRule, Map.class));
                    }
                }
            } else if ("custom".equals(type) && enabled && !StringUtils.hasText(asString(button.get("perm")))) {
                if (strictCustomPermission) {
                    throw new IllegalArgumentException("启用的自定义按钮必须配置权限码: " + asString(button.get("label")));
                }
                button.put("enabled", false);
            }
            validatePermission(entityCode, asString(button.get("perm")), strictCustomPermission);

            EntityActionRuleDTO rule = readRule(button);
            if (rule != null) {
                normalizeRuleValues(rule.getRoot());
                validateRule(rule);
                button.put("availabilityRule", objectMapper.convertValue(rule, Map.class));
            }
            result.add(button);
        }
        return result;
    }

    private List<Map<String, Object>> parseButtons(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, BUTTON_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("按钮配置JSON格式不正确", e);
        }
    }

    private String writeButtons(List<Map<String, Object>> buttons) {
        try {
            return objectMapper.writeValueAsString(buttons);
        } catch (Exception e) {
            throw new IllegalArgumentException("按钮配置序列化失败", e);
        }
    }

    private void validateRule(EntityActionRuleDTO rule) {
        if (rule.getVersion() == null || rule.getVersion() != 1) {
            throw new IllegalArgumentException("不支持的按钮条件规则版本");
        }
        String behavior = rule.getUnavailableBehavior();
        if (StringUtils.hasText(behavior)
                && !"HIDE".equalsIgnoreCase(behavior)
                && !"DISABLE".equalsIgnoreCase(behavior)) {
            throw new IllegalArgumentException("不可用行为只能是 HIDE 或 DISABLE");
        }
        int[] count = {0};
        validateNode(rule.getRoot(), 1, count);
    }

    private void normalizeRuleValues(EntityActionRuleDTO.RuleNode node) {
        if (node == null) {
            return;
        }
        if ("GROUP".equalsIgnoreCase(node.getType())) {
            if (node.getChildren() != null) {
                node.getChildren().forEach(this::normalizeRuleValues);
            }
            return;
        }
        if (("IN".equalsIgnoreCase(node.getOperator()) || "NOT_IN".equalsIgnoreCase(node.getOperator()))
                && node.getValue() instanceof String text) {
            node.setValue(java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList());
        }
    }

    private void validateNode(EntityActionRuleDTO.RuleNode node, int depth, int[] count) {
        if (node == null) {
            return;
        }
        if (depth > MAX_RULE_DEPTH || ++count[0] > MAX_RULE_NODES) {
            throw new IllegalArgumentException("按钮条件规则过于复杂");
        }
        String type = node.getType();
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("按钮条件缺少类型");
        }
        switch (type.toUpperCase()) {
            case "GROUP" -> {
                if (!"AND".equalsIgnoreCase(node.getLogic()) && !"OR".equalsIgnoreCase(node.getLogic())) {
                    throw new IllegalArgumentException("条件组逻辑只能是 AND 或 OR");
                }
                if (node.getChildren() == null || node.getChildren().isEmpty()) {
                    throw new IllegalArgumentException("条件组不能为空");
                }
                for (EntityActionRuleDTO.RuleNode child : node.getChildren()) {
                    validateNode(child, depth + 1, count);
                }
            }
            case "RELATION" -> requireText(node.getRelation(), "用户关系不能为空");
            case "PROCESS_STATE", "STATUS_CODE", "STATUS_CATEGORY" ->
                    requireOperator(node.getOperator());
            case "FIELD", "USER_FIELD" -> {
                requireText(node.getField(), "字段条件缺少字段");
                if (!node.getField().matches("[A-Za-z][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("字段条件包含非法字段名: " + node.getField());
                }
                requireOperator(node.getOperator());
            }
            default -> {
                EntityActionRuleConditionProvider provider = findConditionProvider(type);
                if (provider == null) {
                    throw new IllegalArgumentException("不支持的条件类型: " + type);
                }
                provider.validate(node);
            }
        }
    }

    private void validatePermission(String entityCode, String permissionCode, boolean strict) {
        if (!StringUtils.hasText(permissionCode)) {
            return;
        }
        String standardPrefix = "entity:" + EntityPermissionAction.normalizeEntityCode(entityCode) + ":";
        if (permissionCode.startsWith(standardPrefix)) {
            String suffix = permissionCode.substring(standardPrefix.length());
            if (EntityPermissionAction.fromCode(suffix) != null
                    || suffix.matches("custom:[a-z][a-z0-9_-]*")) {
                return;
            }
        }
        for (EntityPermissionOptionProvider provider : permissionOptionProviders) {
            if (provider.supportsPermission(entityCode, permissionCode)) {
                provider.validatePermission(entityCode, permissionCode);
                return;
            }
        }
        if (!strict) {
            return;
        }
        throw new IllegalArgumentException("权限码不属于当前实体或未注册扩展提供器: " + permissionCode);
    }

    private EntityActionRuleConditionProvider findConditionProvider(String type) {
        return conditionProviders.stream()
                .filter(provider -> provider.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    private void requireOperator(String operator) {
        requireText(operator, "条件运算符不能为空");
        if (!List.of("EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS",
                        "EMPTY", "NOT_EMPTY", "GT", "GTE", "LT", "LTE")
                .contains(operator.toUpperCase())) {
            throw new IllegalArgumentException("不支持的条件运算符: " + operator);
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isToolbarKey(String key) {
        return List.of("create", "exportSelected", "exportAll", "batchDelete").contains(key);
    }

    private List<Map<String, Object>> defaultButtons(boolean toolbar) {
        return toolbar ? List.of(
                button("create", "新增数据", 1),
                button("exportSelected", "导出选中", 2),
                button("exportAll", "导出全部", 3),
                button("batchDelete", "批量删除", 4)
        ) : List.of(
                button("view", "查看", 1),
                button("edit", "编辑", 2),
                button("approve", "审批", 3),
                button("delete", "删除", 4)
        );
    }

    private Map<String, Object> button(String key, String label, int sort) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("key", key);
        button.put("type", "built-in");
        button.put("label", label);
        button.put("sort", sort);
        button.put("enabled", true);
        return button;
    }

    private EntityActionRuleDTO defaultRule(String buttonKey) {
        if ("delete".equals(buttonKey)) {
            return ownDraftOrWithdrawnRule("仅本人未流转草稿或已撤回数据可以删除");
        }
        if ("batchDelete".equals(buttonKey)) {
            EntityActionRuleDTO rule = ownDraftOrWithdrawnRule("选中数据中存在不可删除的数据");
            rule.setUnavailableBehavior("DISABLE");
            return rule;
        }
        if ("approve".equals(buttonKey)) {
            EntityActionRuleDTO rule = new EntityActionRuleDTO();
            rule.setUnavailableBehavior("HIDE");
            rule.setMessage("仅当前任务办理人可以审批");
            rule.setRoot(group("AND",
                    relation("CURRENT_USER_IS_ASSIGNEE"),
                    condition("PROCESS_STATE", "EQ", "RUNNING")));
            return rule;
        }
        return null;
    }

    private EntityActionRuleDTO ownDraftOrWithdrawnRule(String message) {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        rule.setUnavailableBehavior("HIDE");
        rule.setMessage(message);
        rule.setRoot(group("AND",
                group("OR",
                        relation("CURRENT_USER_IS_CREATOR"),
                        relation("CURRENT_USER_IS_SUBMITTER")),
                group("OR",
                        group("AND",
                                condition("PROCESS_STATE", "EQ", "NOT_STARTED"),
                                condition("STATUS_CATEGORY", "EQ", "NEW")),
                        condition("STATUS_CATEGORY", "EQ", "WITHDRAWN"))));
        return rule;
    }

    private EntityActionRuleDTO.RuleNode group(String logic, EntityActionRuleDTO.RuleNode... children) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("GROUP");
        node.setLogic(logic);
        node.setChildren(List.of(children));
        return node;
    }

    private EntityActionRuleDTO.RuleNode relation(String relation) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("RELATION");
        node.setRelation(relation);
        return node;
    }

    private EntityActionRuleDTO.RuleNode condition(String type, String operator, Object value) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType(type);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
