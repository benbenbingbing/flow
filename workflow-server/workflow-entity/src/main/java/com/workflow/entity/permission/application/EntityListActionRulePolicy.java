package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 列表按钮条件的完整语义校验与规范化策略。
 *
 * <p>先复用表单/列表共享的 v2 结构边界，再校验标准条件及项目扩展
 * provider。列表整包保存和关系型 create/patch/replace 必须共用此策略，
 * 防止写入成功后才在发布或读取时发现规则不合法。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityListActionRulePolicy {

    private final ObjectMapper objectMapper;
    private final List<EntityActionRuleConditionProvider> conditionProviders;

    /**
     * 规范化并校验列表按钮条件。
     *
     * @param rawRule availabilityRule 原始文档
     * @return 完整校验后的 DTO
     */
    public EntityActionRuleDTO read(Object rawRule) {
        Map<String, Object> normalized =
                EntityActionRuleStructurePolicy.normalizeAndValidate(rawRule);
        EntityActionRuleBuiltInPolicy.validate(normalized, true);
        EntityActionRuleDTO rule = objectMapper.convertValue(
                normalized, EntityActionRuleDTO.class);
        validateCustomNode(rule.getVisibleWhen());
        validateCustomNode(rule.getEnabledWhen());
        return rule;
    }

    /**
     * 返回适合直接持久化的 canonical Map。
     *
     * @param rawRule availabilityRule 原始文档
     * @return 规范化后的 v2 文档
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeDocument(Object rawRule) {
        return objectMapper.convertValue(read(rawRule), Map.class);
    }

    private void validateCustomNode(EntityActionRuleDTO.RuleNode node) {
        if (node == null) {
            return;
        }
        String type = node.getType();
        switch (type.toUpperCase()) {
            case "GROUP" -> node.getChildren()
                    .forEach(this::validateCustomNode);
            case "RELATION", "PROCESS_STATE", "STATUS_CODE",
                    "STATUS_CATEGORY", "FIELD", "USER_FIELD" -> {
                // 内置节点已由共享策略完成完整语义校验。
            }
            default -> {
                EntityActionRuleConditionProvider provider =
                        findConditionProvider(type);
                if (provider == null) {
                    throw new IllegalArgumentException(
                            "不支持的条件类型: " + type);
                }
                provider.validate(node);
            }
        }
    }

    private EntityActionRuleConditionProvider findConditionProvider(
            String type) {
        return conditionProviders.stream()
                .filter(provider -> provider.getType()
                        .equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }
}
