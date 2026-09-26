package com.workflow.biz.project.custom;

import com.workflow.contracts.identity.model.IdentityUser;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.contracts.entity.permission.spi.EntityActionRuleConditionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 实体按钮规则自定义条件示例。
 *
 * <p>条件类型为 {@value #TYPE}，使用节点的 field、operator、value 配置。
 * 支持 EQ、NE、IN；字段从当前行的业务数据或少量系统字段读取。</p>
 */
@Slf4j
@Component
public class ProjectCustomActionRuleConditionProvider
        implements EntityActionRuleConditionProvider {

    public static final String TYPE =
            "PROJECT:CUSTOM_CONDITION";

    /**
     * 读取类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的类型文本，供调用方比较或展示
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 校验项目自定义动作规则条件提供者；不满足约束时阻止后续处理。
     *
     * @param node 节点，供本方法校验项目自定义动作规则条件提供者时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    public void validate(
            EntityActionRule.RuleNode node) {
        if (node == null
                || !StringUtils.hasText(node.getField())) {
            throw new IllegalArgumentException(
                    "项目自定义按钮条件必须配置 field");
        }
        String operator = StringUtils.hasText(
                node.getOperator())
                ? node.getOperator().toUpperCase()
                : "EQ";
        if (!java.util.Set.of("EQ", "NE", "IN")
                .contains(operator)) {
            throw new IllegalArgumentException(
                    "项目自定义按钮条件仅支持 EQ、NE、IN");
        }
    }

    /**
     * 求值项目自定义动作规则条件提供者，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code validate} 的输入影响后续处理
     * @param row 行，作为 {@code readField} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @return 项目自定义动作规则条件提供者条件成立时为 true，否则为 false
     */
    @Override
    public boolean evaluate(
            EntityActionRule.RuleNode node,
            EntityRecordData row,
            IdentityUser user,
            String statusCategory) {
        validate(node);
        Object actual = readField(row, node.getField());
        String operator = StringUtils.hasText(
                node.getOperator())
                ? node.getOperator().toUpperCase()
                : "EQ";
        boolean matched = switch (operator) {
            case "NE" -> !same(actual, node.getValue());
            case "IN" -> node.getValue()
                    instanceof Collection<?> values
                    && values.stream()
                            .anyMatch(value ->
                                    same(actual, value));
            default -> same(actual, node.getValue());
        };
        log.info(
                "项目按钮自定义条件评估: type={}, field={}, operator={}, rowId={}, userId={}, statusCategory={}, matched={}",
                TYPE,
                LogValue.safe(node.getField()),
                operator,
                LogValue.safe(row == null
                        ? null : row.getId()),
                LogValue.safe(user == null
                        ? null : user.id()),
                LogValue.safe(statusCategory),
                matched);
        return matched;
    }

    /**
     * 读取字段；查询结果供调用方展示或继续处理。
     *
     * @param row 行，作为 {@code firstPresent} 的输入影响后续处理
     * @param field 字段，供本方法读取字段时使用
     * @return 读取后的字段结果，供调用方继续处理
     */
    private Object readField(
            EntityRecordData row,
            String field) {
        if (row == null) {
            return null;
        }
        return switch (field) {
            case "id" -> row.getId();
            case "code" -> row.getCode();
            case "name" -> row.getName();
            case "status" -> row.getStatus();
            case "create_by" -> row.getCreateBy();
            case "submitterId" -> row.getSubmitterId();
            default -> firstPresent(
                    row.getData(),
                    row.getExtData(),
                    field);
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
    private Object firstPresent(
            Map<String, Object> data,
            Map<String, Object> extData,
            String field) {
        if (data != null && data.containsKey(field)) {
            return data.get(field);
        }
        return extData == null
                ? null : extData.get(field);
    }

    /**
     * 判断相同条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同时使用
     * @param right 右侧，供本方法处理相同时使用
     * @return 相同条件成立时为 true，否则为 false
     */
    private boolean same(
            Object left,
            Object right) {
        return Objects.equals(left, right)
                || left != null
                && right != null
                && String.valueOf(left)
                        .equals(String.valueOf(right));
    }
}
