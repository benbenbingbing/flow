package com.workflow.service.listfield;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 列表字段条件求值器测试。
 *
 * <p>被测对象：{@link ListFieldConditionEvaluator}，覆盖 provider 增强后过滤虚拟字段、
 * 范围与 IN 操作符支持等场景。
 */
class ListFieldConditionEvaluatorTest {

    /** 被测条件求值器 */
    private final ListFieldConditionEvaluator evaluator = new ListFieldConditionEvaluator();

    /** 测试 provider 增强后过滤虚拟字段：验证 LIKE 条件仅保留匹配的行 */
    @Test
    void filtersVirtualFieldAfterProviderEnrichment() {
        EntityListField field = field("summary", "LIKE");
        EntityDataDTO first = row("A-001 张三");
        EntityDataDTO second = row("B-002 李四");

        List<EntityDataDTO> result = evaluator.filter(
                List.of(first, second),
                List.of(field),
                Map.of("summary", "张三", "summary_op", "LIKE"));

        assertEquals(List.of(first), result);
    }

    /** 测试支持范围（BETWEEN）与 IN 操作符：验证 BETWEEN 取区间内行，IN 取枚举值行 */
    @Test
    void supportsRangeAndInOperators() {
        EntityListField amount = field("amount", "BETWEEN");
        EntityDataDTO low = rowWithData(Map.of("amount", 80));
        EntityDataDTO middle = rowWithData(Map.of("amount", 120));
        EntityDataDTO high = rowWithData(Map.of("amount", 180));

        assertEquals(
                List.of(middle),
                evaluator.filter(
                        List.of(low, middle, high),
                        List.of(amount),
                        Map.of("amount_start", 100, "amount_end", 150)));

        amount.setQueryType("IN");
        assertEquals(
                List.of(low, high),
                evaluator.filter(
                        List.of(low, middle, high),
                        List.of(amount),
                        Map.of("amount", List.of(80, 180), "amount_op", "IN")));
    }

    /** 构造可查询字段，指定查询操作符 */
    private EntityListField field(String code, String operator) {
        EntityListField field = new EntityListField();
        field.setFieldCode(code);
        field.setIsQuery(true);
        field.setQueryType(operator);
        return field;
    }

    /** 构造带 summary 扩展数据的行 */
    private EntityDataDTO row(String summary) {
        EntityDataDTO row = new EntityDataDTO();
        row.setExtData(Map.of("summary", summary));
        return row;
    }

    /** 构造带业务数据的行 */
    private EntityDataDTO rowWithData(Map<String, Object> data) {
        EntityDataDTO row = new EntityDataDTO();
        row.setData(data);
        return row;
    }
}
