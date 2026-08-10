package com.workflow.project.custom;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.EntityActionRuleConditionProvider;
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

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(
            EntityActionRuleDTO.RuleNode node) {
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

    @Override
    public boolean evaluate(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
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
                        ? null : user.getId()),
                LogValue.safe(statusCategory),
                matched);
        return matched;
    }

    private Object readField(
            EntityDataDTO row,
            String field) {
        if (row == null) {
            return null;
        }
        return switch (field) {
            case "id" -> row.getId();
            case "dataNo" -> row.getDataNo();
            case "name" -> row.getName();
            case "status" -> row.getStatus();
            case "createdBy" -> row.getCreatedBy();
            case "submitterId" -> row.getSubmitterId();
            default -> firstPresent(
                    row.getData(),
                    row.getExtData(),
                    field);
        };
    }

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
