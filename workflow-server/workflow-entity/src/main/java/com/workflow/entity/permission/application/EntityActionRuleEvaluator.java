package com.workflow.entity.permission.application;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
public class EntityActionRuleEvaluator {

    private final List<EntityActionRuleConditionProvider> conditionProviders;
    private final CurrentProcessTaskAssigneeLookup assigneeLookup;

    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders) {
        this(conditionProviders, (CurrentProcessTaskAssigneeLookup) null);
    }

    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders,
            CurrentProcessTaskAssigneeLookup assigneeLookup) {
        this.conditionProviders = conditionProviders == null ? List.of() : conditionProviders;
        this.assigneeLookup = assigneeLookup;
    }

    @Autowired
    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders,
            ObjectProvider<CurrentProcessTaskAssigneeLookup> assigneeLookup) {
        this(conditionProviders, assigneeLookup.getIfAvailable());
    }

    /**
     * 评估按钮可用性规则是否满足。
     *
     * @param rule           规则定义，为空或无根节点时视为始终满足
     * @param row            当前数据行，可为 null
     * @param user           当前用户
     * @param statusCategory 数据所属状态分类，可为 null
     * @return 规则满足返回 true
     */
    public boolean evaluate(
            EntityActionRuleDTO rule,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        if (rule == null || rule.getRoot() == null) {
            return true;
        }
        return evaluateNode(rule.getRoot(), row, user, statusCategory, false);
    }

    /**
     * 仅为审批入口评估规则，将办理人关系解释为已验证的当前可审批身份。
     *
     * <p>调用方必须先通过任务访问契约取得该记录的真实可审批任务；
     * 该上下文只扩展 CURRENT_USER_IS_ASSIGNEE，状态、字段和其他关系条件仍逐项评估。
     * 编辑、删除、转办以及任意自定义动作继续调用 evaluate，不能借用候选审批权。</p>
     *
     * @param rule 审批按钮配置规则
     * @param row 当前业务记录
     * @param user 当前认证用户
     * @param statusCategory 记录状态分类
     * @param hasActionableTask 调用方是否已验证当前用户的真实可审批任务
     * @return 审批入口规则满足时为 true
     */
    public boolean evaluateForApproval(
            EntityActionRuleDTO rule,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean hasActionableTask) {
        if (!hasActionableTask || row == null || user == null) {
            return false;
        }
        if (rule == null || rule.getRoot() == null) {
            return true;
        }
        return evaluateNode(rule.getRoot(), row, user, statusCategory, hasActionableTask);
    }

    private boolean evaluateNode(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean currentApprover) {
        if (node == null || node.getType() == null) {
            return false;
        }
        return switch (node.getType().toUpperCase(Locale.ROOT)) {
            case "GROUP" -> evaluateGroup(node, row, user, statusCategory, currentApprover);
            case "RELATION" -> evaluateRelation(node.getRelation(), row, user, currentApprover);
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
            String statusCategory,
            boolean currentApprover) {
        List<EntityActionRuleDTO.RuleNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        if ("OR".equalsIgnoreCase(node.getLogic())) {
            return children.stream().anyMatch(child -> evaluateNode(child, row, user, statusCategory, currentApprover));
        }
        return children.stream().allMatch(child -> evaluateNode(child, row, user, statusCategory, currentApprover));
    }

    private boolean evaluateRelation(String relation, EntityDataDTO row, SysUser user, boolean currentApprover) {
        if (row == null || user == null || relation == null) {
            return false;
        }
        return switch (relation.toUpperCase(Locale.ROOT)) {
            case "CURRENT_USER_IS_CREATOR" -> matchesUser(row.getCreatedBy(), user);
            case "CURRENT_USER_IS_SUBMITTER" -> matchesUser(row.getSubmitterId(), user);
            case "CURRENT_USER_IS_ASSIGNEE" -> currentApprover
                    || matchesUser(row.getCurrentTaskAssignee(), user)
                    || isLiveProcessTaskAssignee(row, user);
            case "CURRENT_USER_SAME_DEPT" -> StringUtils.hasText(row.getDeptId())
                    && Objects.equals(row.getDeptId(), user.getDeptId());
            default -> false;
        };
    }

    private boolean matchesUser(String value, SysUser user) {
        return StringUtils.hasText(value)
                && (Objects.equals(value, user.getId()) || Objects.equals(value, user.getUsername()));
    }

    /**
     * 会签时实体只记其中一个办理人，回查未完成待办判断当前用户是否真正持有任务。
     */
    private boolean isLiveProcessTaskAssignee(EntityDataDTO row, SysUser user) {
        return assigneeLookup != null && assigneeLookup.isCurrentAssignee(row, user);
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
