package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证旧版 approved 布尔条件表达式在发布时被迁移为字符串比较。
 *
 * <p>approved 变量已从布尔 true/false 改为字符串 "approve"/"reject"，
 * 历史 BPMN 里的 {@code ${approved == true}} 需在发布时改写为 {@code ${approved == 'approve'}}，
 * 否则网关条件类型不匹配会导致分支永远走错。</p>
 */
class ApprovedExpressionMigrationTest {

    private final ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());

    private static final String XML_PREFIX =
            "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                    + "xmlns:flowable=\"http://flowable.org/bpmn\">"
                    + "<bpmn:process id=\"p1\">"
                    + "<bpmn:sequenceFlow id=\"f1\" sourceRef=\"g1\" targetRef=\"t1\">"
                    + "<bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">";

    private static final String XML_SUFFIX =
            "</bpmn:conditionExpression>"
                    + "</bpmn:sequenceFlow>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";

    private String wrap(String expression) {
        return XML_PREFIX + expression + XML_SUFFIX;
    }

    @Test
    void migratesApprovedEqualsTrue() {
        String result = sanitizer.sanitize(wrap("${approved == true}"), "p1");
        assertTrue(result.contains("approved == 'approve'"), "approved==true 应迁移为 'approve'");
        assertFalse(result.contains("approved == true"));
    }

    @Test
    void migratesApprovedEqualsFalse() {
        String result = sanitizer.sanitize(wrap("${approved == false}"), "p1");
        assertTrue(result.contains("approved == 'reject'"), "approved==false 应迁移为 'reject'");
        assertFalse(result.contains("approved == false"));
    }

    @Test
    void migratesApprovedNotEqualsTrue() {
        String result = sanitizer.sanitize(wrap("${approved != true}"), "p1");
        assertTrue(result.contains("approved != 'approve'"));
        assertFalse(result.contains("approved != true"));
    }

    @Test
    void doesNotTouchAlreadyStringExpressions() {
        String result = sanitizer.sanitize(wrap("${approved == 'approve'}"), "p1");
        // 已经是字符串比较，保持不变，不应被重复处理
        assertTrue(result.contains("approved == 'approve'"));
    }

    @Test
    void migratesMultipleExpressionsAndOtherConditions() {
        String result = sanitizer.sanitize(wrap("${approved == true && amount > 100}"), "p1");
        assertTrue(result.contains("approved == 'approve'"));
        assertTrue(result.contains("amount > 100"), "非 approved 的条件不应被误改");
    }
}
