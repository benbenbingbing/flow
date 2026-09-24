package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 历史发布文档保留原始哈希；读取时忽略退役绑定但不丢失实际节点配置。 */
class EntityFormNodeTemplateRetirementTest {
    @Test
    void historicalNodeStillDeserializesWithoutTemplateDependency() throws Exception {
        ObjectMapper json = new ObjectMapper();
        EntityFormNode node = json.readValue("""
                {"nodeKey":"amount","nodeType":"FIELD","templateId":"removed-template",
                 "templateVersion":7,"localOverridesDocument":"{}",
                 "propsDocument":"{\\"label\\":\\"金额\\",\\"required\\":true}"}
                """, EntityFormNode.class);
        assertEquals("amount", node.getNodeKey());
        assertEquals("金额", json.readTree(node.getPropsDocument()).path("label").asText());
        assertTrue(json.readTree(node.getPropsDocument()).path("required").asBoolean());
        assertFalse(json.writeValueAsString(node).contains("templateId"));
        assertFalse(json.writeValueAsString(node).contains("localOverrides"));
    }
}
