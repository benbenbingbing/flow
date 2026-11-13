package com.workflow.dto;

import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.task.api.response.TaskDetailDTO;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证首屏与进度的独立契约，以及未在本次变更范围内的任务详情单表单投影。 */
class FormConfigsCompatibilityTest {

    /**
     * 首屏只有单个 formConfig，流程进度不再携带实体或表单。
     */
    @Test
    void formRuntimeHasSingleFormAndProgressHasNoFormPayload() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var dto = new com.workflow.process.instance.api.response.ProcessFormRuntimeDTO();
        var form = new com.workflow.process.instance.api.response.ProcessFormRuntimeDTO.FormConfigDTO();
        form.setFormId("form-1"); dto.setFormConfig(form);
        var json = mapper.valueToTree(dto);
        assertEquals("form-1", json.path("formConfig").path("formId").asText());
        org.junit.jupiter.api.Assertions.assertFalse(json.has("formConfigs"));
        org.junit.jupiter.api.Assertions.assertFalse(json.has("bpmnXml"));
        org.junit.jupiter.api.Assertions.assertFalse(mapper.valueToTree(new ProcessProgressDTO()).has("entityData"));
    }

    /**
     * 任务详情 DTO 应将历史多表单输入收敛为首个表单。
     */
    @Test
    void taskDetailKeepsOnlyFirstFormConfig() {
        TaskDetailDTO dto = new TaskDetailDTO();
        TaskDetailDTO.FormConfigDTO first = new TaskDetailDTO.FormConfigDTO();
        first.setEntityFormId("form-1");
        TaskDetailDTO.FormConfigDTO second = new TaskDetailDTO.FormConfigDTO();
        second.setEntityFormId("form-2");

        dto.setFormConfig(first);
        dto.setFormConfigs(List.of(first, second));

        assertEquals("form-1", dto.getFormConfig().getEntityFormId());
        assertEquals(1, dto.getFormConfigs().size());
        assertEquals("form-1", dto.getFormConfigs().get(0).getEntityFormId());
    }
}
