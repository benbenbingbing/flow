package com.workflow.service.permission;

import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.SysUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 结构化实体按钮规则执行器。
 */
@Component
@RequiredArgsConstructor
public class EntityActionRuleEvaluator {

    private final List<EntityActionRuleConditionProvider> conditionProviders;

    public boolean evaluate(
            EntityActionRuleDTO rule,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        if (rule == null || rule.getRoot() == null) {
            return true;
        }
        return evaluateNode(rule.getRoot(), row, user, statusCategory);
    }

    private boolean evaluateNode(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        if (node == null || node.getType() == null) {
            return false;
        }
        return switch (node.getType().toUpperCase(Locale.ROOT)) {
            case "GROUP" -> evaluateGroup(node, row, user, statusCategory);
            case "RELATION" -> evaluateRelation(node.getRelation(), row, user);
            case "PROCESS_STATE" -> compare(processState(row, statusCategory), node.getOperator(), node.getValue());
            case "STATUS_CODE" -> compare(row == null ? null : row.getStatus(), node.getOperator(), node.getValue());
            case "STATUS_CATEGORY" -> compare(statusCategory, node.getOperator(), node.getValue());
            case "FIELD" -> compare(readField(row, node.getField()), node.getOperator(), node.getValue());
            case "USER_FIELD" -> compare(readUserField(user, node.getField()), node.getOperator(), node.getValue());
            default -> evaluateCustom(node, row, user, statusCategory);
        };
    }

    private boolean evaluateCustom(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        return conditionProviders.stream()
                .filter(provider -> provider.getType().equalsIgnoreCase(node.getType()))
                .findFirst()
                .map(provider -> provider.evaluate(node, row, user, statusCategory))
                .orElse(false);
    }

    private boolean evaluateGroup(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        List<EntityActionRuleDTO.RuleNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        if ("OR".equalsIgnoreCase(node.getLogic())) {
            return children.stream().anyMatch(child -> evaluateNode(child, row, user, statusCategory));
        }
        return children.stream().allMatch(child -> evaluateNode(child, row, user, statusCategory));
    }

    private boolean evaluateRelation(String relation, EntityDataDTO row, SysUser user) {
        if (row == null || user == null || relation == null) {
            return false;
        }
        return switch (relation.toUpperCase(Locale.ROOT)) {
            case "CURRENT_USER_IS_CREATOR" -> matchesUser(row.getCreatedBy(), user);
            case "CURRENT_USER_IS_SUBMITTER" -> matchesUser(row.getSubmitterId(), user);
            case "CURRENT_USER_IS_ASSIGNEE" -> matchesUser(row.getCurrentTaskAssignee(), user);
            case "CURRENT_USER_SAME_DEPT" -> StringUtils.hasText(row.getDeptId())
                    && Objects.equals(row.getDeptId(), user.getDeptId());
            default -> false;
        };
    }

    private boolean matchesUser(String value, SysUser user) {
        return StringUtils.hasText(value)
                && (Objects.equals(value, user.getId()) || Objects.equals(value, user.getUsername()));
    }

    private String processState(EntityDataDTO row, String statusCategory) {
        if (row == null || !StringUtils.hasText(row.getProcessInstanceId())) {
            return "NOT_STARTED";
        }
        if ("WITHDRAWN".equalsIgnoreCase(statusCategory)) {
            return "WITHDRAWN";
        }
        if (row.getProcessEndTime() == null) {
            return "RUNNING";
        }
        if ("TERMINATED".equalsIgnoreCase(statusCategory)) {
            return "TERMINATED";
        }
        return "COMPLETED";
    }

    private Object readField(EntityDataDTO row, String field) {
        if (row == null || field == null) {
            return null;
        }
        return switch (field) {
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
            case "createdAt" -> row.getCreatedAt();
            case "updatedAt" -> row.getUpdatedAt();
            case "createdBy" -> row.getCreatedBy();
            case "updatedBy" -> row.getUpdatedBy();
            default -> firstPresent(row.getData(), row.getExtData(), field);
        };
    }

    private Object readUserField(SysUser user, String field) {
        if (user == null || field == null) {
            return null;
        }
        return switch (field) {
            case "id" -> user.getId();
            case "username" -> user.getUsername();
            case "deptId" -> user.getDeptId();
            case "orgId" -> user.getOrgId();
            case "roleIds" -> user.getRoleIds();
            default -> null;
        };
    }

    private Object firstPresent(Map<String, Object> data, Map<String, Object> extData, String field) {
        if (data != null && data.containsKey(field)) {
            return data.get(field);
        }
        return extData == null ? null : extData.get(field);
    }

    private boolean compare(Object actual, String operator, Object expected) {
        String op = operator == null ? "EQ" : operator.toUpperCase(Locale.ROOT);
        return switch (op) {
            case "EMPTY" -> isEmpty(actual);
            case "NOT_EMPTY" -> !isEmpty(actual);
            case "EQ" -> equalsValue(actual, expected);
            case "NE" -> !equalsValue(actual, expected);
            case "IN" -> toCollection(expected).stream().anyMatch(value -> equalsValue(actual, value));
            case "NOT_IN" -> toCollection(expected).stream().noneMatch(value -> equalsValue(actual, value));
            case "CONTAINS" -> contains(actual, expected);
            case "NOT_CONTAINS" -> !contains(actual, expected);
            case "GT" -> compareOrdered(actual, expected) > 0;
            case "GTE" -> compareOrdered(actual, expected) >= 0;
            case "LT" -> compareOrdered(actual, expected) < 0;
            case "LTE" -> compareOrdered(actual, expected) <= 0;
            default -> false;
        };
    }

    private boolean equalsValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected))) == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> equalsValue(value, expected));
        }
        return actual != null && expected != null
                && String.valueOf(actual).contains(String.valueOf(expected));
    }

    private int compareOrdered(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return -1;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (actual instanceof Temporal || expected instanceof Temporal) {
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value != null && value.getClass().isArray()) {
            return List.of((Object[]) value);
        }
        return value == null ? List.of() : List.of(value);
    }
}
