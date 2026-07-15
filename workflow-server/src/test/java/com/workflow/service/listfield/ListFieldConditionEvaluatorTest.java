package com.workflow.service.listfield;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListFieldConditionEvaluatorTest {

    private final ListFieldConditionEvaluator evaluator = new ListFieldConditionEvaluator();

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

    private EntityListField field(String code, String operator) {
        EntityListField field = new EntityListField();
        field.setFieldCode(code);
        field.setIsQuery(true);
        field.setQueryType(operator);
        return field;
    }

    private EntityDataDTO row(String summary) {
        EntityDataDTO row = new EntityDataDTO();
        row.setExtData(Map.of("summary", summary));
        return row;
    }

    private EntityDataDTO rowWithData(Map<String, Object> data) {
        EntityDataDTO row = new EntityDataDTO();
        row.setData(data);
        return row;
    }
}
