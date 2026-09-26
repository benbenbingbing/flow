package com.workflow.entity.permission.application;

import com.workflow.contracts.entity.permission.spi.EntityActionRuleConditionProvider;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.workflow.entity.permission.application.PermissionConditionComparison.compare;

/**
 * 结构化实体按钮规则执行器。
 */
@Component
public class EntityActionRuleEvaluator {

    private final List<EntityActionRuleConditionProvider> conditionProviders;
    private final CurrentProcessTaskAssigneeLookup assigneeLookup;

    /**
     * 初始化实体动作规则求值器，保存构造参数供后续方法使用。
     *
     * @param conditionProviders 条件提供者集合，保存在对象中供后续校验、查询或展示
     */
    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders) {
        this(conditionProviders, (CurrentProcessTaskAssigneeLookup) null);
    }

    /**
     * 初始化实体动作规则求值器，保存构造参数供后续方法使用。
     *
     * @param conditionProviders 条件提供者集合依赖，保存到当前对象供后续业务方法调用
     * @param assigneeLookup 办理人查找依赖，保存到当前对象供后续业务方法调用
     */
    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders,
            CurrentProcessTaskAssigneeLookup assigneeLookup) {
        this.conditionProviders = conditionProviders == null ? List.of() : conditionProviders;
        this.assigneeLookup = assigneeLookup;
    }

    /**
     * 初始化实体动作规则求值器，保存构造参数供后续方法使用。
     *
     * @param conditionProviders 条件提供者集合，保存在对象中供后续校验、查询或展示
     * @param assigneeLookup 办理人查找，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EntityActionRuleEvaluator(
            List<EntityActionRuleConditionProvider> conditionProviders,
            ObjectProvider<CurrentProcessTaskAssigneeLookup> assigneeLookup) {
        this(conditionProviders, assigneeLookup.getIfAvailable());
    }

    /**
     * 评估按钮可用性规则是否满足。
     *
     * @param root           规则树根节点，为空时视为始终满足
     * @param row            当前数据行，可为 null
     * @param user           当前用户
     * @param statusCategory 数据所属状态分类，可为 null
     * @return 规则满足返回 true
     */
    public boolean evaluate(
            EntityActionRule.RuleNode root,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        if (root == null) {
            return true;
        }
        return evaluateNode(root, row, user, statusCategory, false);
    }

    /**
     * 仅为审批入口评估规则，将办理人关系解释为已验证的当前可审批身份。
     *
     * <p>调用方必须先通过任务访问契约取得该记录的真实可审批任务；
     * 该上下文只扩展 CURRENT_USER_IS_ASSIGNEE，状态、字段和其他关系条件仍逐项评估。
     * 编辑、删除、转办以及任意自定义动作继续调用 evaluate，不能借用候选审批权。</p>
     *
     * @param root 审批按钮的规则树根节点
     * @param row 当前业务记录
     * @param user 当前认证用户
     * @param statusCategory 记录状态分类
     * @param hasActionableTask 调用方是否已验证当前用户的真实可审批任务
     * @return 审批入口规则满足时为 true
     */
    public boolean evaluateForApproval(
            EntityActionRule.RuleNode root,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean hasActionableTask) {
        if (!hasActionableTask || row == null || user == null) {
            return false;
        }
        if (root == null) {
            return true;
        }
        return evaluateNode(root, row, user, statusCategory, hasActionableTask);
    }

    /**
     * 判断条件树是否可在没有数据行的列表工具栏上完整求值。
     *
     * <p>目前只有 USER_FIELD 不依赖行、状态或流程上下文；扩展节点默认
     * 按依赖行处理，防止在信息不完整时做出错误的隐藏决策。</p>
     *
     * @param root 规则树根节点
     * @return 无数据行也可完整求值时返回 true
     */
    public boolean isRowIndependent(
            EntityActionRule.RuleNode root) {
        if (root == null) {
            return true;
        }
        String type = root.getType();
        if (!StringUtils.hasText(type)) {
            return false;
        }
        if ("GROUP".equalsIgnoreCase(type)) {
            return root.getChildren() != null
                    && !root.getChildren().isEmpty()
                    && root.getChildren().stream()
                    .allMatch(this::isRowIndependent);
        }
        return "USER_FIELD".equalsIgnoreCase(type);
    }

    /**
     * 求值节点，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code evaluateGroup} 的输入影响后续处理
     * @param row 行，作为 {@code evaluateGroup} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param currentApprover 当前审批人，作为 {@code evaluateGroup} 的输入影响后续处理
     * @return 节点条件成立时为 true，否则为 false
     */
    private boolean evaluateNode(
            EntityActionRule.RuleNode node,
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
            case "PROCESS_STATE" -> {
                // 新规则缺失投影时一律不授权，尤其不能因 null != RUNNING 而意外放行。
                if (node.getLifecycleVersion() != null) {
                    yield Integer.valueOf(1).equals(node.getLifecycleVersion())
                            && lifecycleState(row) != null
                            && compare(lifecycleState(row), node.getOperator(), node.getValue());
                }
                yield compare(processState(row, statusCategory), node.getOperator(), node.getValue());
            }
            case "STATUS_CODE" -> compare(row == null ? null : row.getStatus(), node.getOperator(), node.getValue());
            case "STATUS_CATEGORY" -> compare(statusCategory, node.getOperator(), node.getValue());
            case "FIELD" -> compare(readField(row, node.getField()), node.getOperator(), node.getValue());
            case "USER_FIELD" -> compare(readUserField(user, node.getField()), node.getOperator(), node.getValue());
            default -> evaluateCustom(node, row, user, statusCategory);
        };
    }

    /**
     * 求值自定义，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code evaluate} 的输入影响后续处理
     * @param row 行，作为 {@code evaluate} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @return 自定义条件成立时为 true，否则为 false
     */
    private boolean evaluateCustom(
            EntityActionRule.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        return conditionProviders.stream()
                .filter(provider -> provider.getType().equalsIgnoreCase(node.getType()))
                .findFirst()
                .map(provider -> provider.evaluate(node, com.workflow.entity.data.application.mapping.EntityRecordProjection.project(row),
                        PermissionExtensionInputs.user(user), statusCategory))
                .orElse(false);
    }

    /**
     * 求值分组，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法求值分组时使用
     * @param row 行，供本方法求值分组时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param currentApprover 当前审批人，供本方法求值分组时使用
     * @return 分组条件成立时为 true，否则为 false
     */
    private boolean evaluateGroup(
            EntityActionRule.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean currentApprover) {
        List<EntityActionRule.RuleNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        if ("OR".equalsIgnoreCase(node.getLogic())) {
            return children.stream().anyMatch(child -> evaluateNode(child, row, user, statusCategory, currentApprover));
        }
        return children.stream().allMatch(child -> evaluateNode(child, row, user, statusCategory, currentApprover));
    }

    /**
     * 求值关系，并将结果传给后续步骤。
     *
     * @param relation 关系，供本方法求值关系时使用
     * @param row 行，作为 {@code matchesUser} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param currentApprover 当前审批人，供本方法求值关系时使用
     * @return 关系条件成立时为 true，否则为 false
     */
    private boolean evaluateRelation(String relation, EntityDataDTO row, SysUser user, boolean currentApprover) {
        if (row == null || user == null || relation == null) {
            return false;
        }
        return switch (relation.toUpperCase(Locale.ROOT)) {
            case "CURRENT_USER_IS_CREATOR" -> matchesUser(row.getCreateBy(), user);
            case "CURRENT_USER_IS_SUBMITTER" -> matchesUser(row.getSubmitterId(), user);
            case "CURRENT_USER_IS_ASSIGNEE" -> currentApprover
                    || matchesUser(row.getCurrentTaskAssignee(), user)
                    || isLiveProcessTaskAssignee(row, user);
            case "CURRENT_USER_SAME_DEPT" -> StringUtils.hasText(row.getDeptId())
                    && Objects.equals(row.getDeptId(), user.getDeptId());
            default -> false;
        };
    }

    /**
     * 判断是否匹配用户；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否匹配用户的原始输入，结果供调用方继续使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 用户条件成立时为 true，否则为 false
     */
    private boolean matchesUser(String value, SysUser user) {
        return StringUtils.hasText(value)
                && (Objects.equals(value, user.getId()) || Objects.equals(value, user.getUsername()));
    }

    /**
     * 会签时实体只记其中一个办理人，回查未完成待办判断当前用户是否真正持有任务。
     *
     * @param row 行，作为 {@code assigneeLookup.isCurrentAssignee} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return {@code live}流程任务办理人条件成立时为 true，否则为 false
     */
    private boolean isLiveProcessTaskAssignee(EntityDataDTO row, SysUser user) {
        return assigneeLookup != null && assigneeLookup.isCurrentAssignee(row, user);
    }

    /**
     * 新条件只读取独立投影；缺失投影不能猜测为运行或完成。
     *
     * @param row 行，供本方法处理生命周期状态时使用
     * @return 处理后的生命周期状态文本，供调用方比较或展示
     */
    private String lifecycleState(EntityDataDTO row) {
        return row == null ? "NOT_STARTED" : row.getProcessStatus();
    }

    /**
     * 生成状态文本，供后续匹配或展示。
     *
     * @param row 行，供本方法处理状态时使用
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @return 处理后的状态文本，供调用方比较或展示
     */
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

    /**
     * 读取字段；查询结果供调用方展示或继续处理。
     *
     * @param row 行，作为 {@code firstPresent} 的输入影响后续处理
     * @param field 字段，供本方法读取字段时使用
     * @return 读取后的字段结果，供调用方继续处理
     */
    private Object readField(EntityDataDTO row, String field) {
        if (row == null || field == null) {
            return null;
        }
        return switch (field) {
            case "id" -> row.getId();
            case "name" -> row.getName();
            case "code" -> row.getCode();
            case "status" -> row.getStatus();
            case "processStatus", "process_status" -> row.getProcessStatus();
            case "processInstanceId" -> row.getProcessInstanceId();
            case "processStartTime" -> row.getProcessStartTime();
            case "processEndTime" -> row.getProcessEndTime();
            case "currentTaskId" -> row.getCurrentTaskId();
            case "currentTaskName" -> row.getCurrentTaskName();
            case "currentTaskAssignee" -> row.getCurrentTaskAssignee();
            case "submitterId" -> row.getSubmitterId();
            case "submitterName" -> row.getSubmitterName();
            case "deptId" -> row.getDeptId();
            case "create_time" -> row.getCreateTime();
            case "update_time" -> row.getUpdateTime();
            case "create_by" -> row.getCreateBy();
            case "update_by" -> row.getUpdateBy();
            case "deleted" -> row.getDeleted();
            default -> firstPresent(row.getData(), row.getExtData(), field);
        };
    }

    /**
     * 读取用户字段；查询结果供调用方展示或继续处理。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param field 字段，供本方法读取用户字段时使用
     * @return 读取后的用户字段结果，供调用方继续处理
     */
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

    /**
     * 处理首个存在，并将结果传给后续步骤。
     *
     * @param data 数据，后续用于处理首个存在并传递处理结果
     * @param extData {@code ext}数据，供本方法处理首个存在时使用
     * @param field 字段，作为 {@code data.get} 的输入影响后续处理
     * @return 处理后的首个存在结果，供调用方继续处理
     */
    private Object firstPresent(Map<String, Object> data, Map<String, Object> extData, String field) {
        if (data != null && data.containsKey(field)) {
            return data.get(field);
        }
        return extData == null ? null : extData.get(field);
    }

}
