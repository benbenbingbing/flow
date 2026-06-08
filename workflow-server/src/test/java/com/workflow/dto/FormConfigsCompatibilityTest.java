package com.workflow.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormConfigsCompatibilityTest {

    @Test
    void processProgressKeepsSingleFormConfigAndSupportsMultipleFormConfigs() {
        ProcessProgressDTO dto = new ProcessProgressDTO();
        ProcessProgressDTO.FormConfigDTO first = new ProcessProgressDTO.FormConfigDTO();
        first.setFormId("form-1");
        ProcessProgressDTO.FormConfigDTO second = new ProcessProgressDTO.FormConfigDTO();
        second.setFormId("form-2");

        dto.setFormConfig(first);
        dto.setFormConfigs(List.of(first, second));

        assertEquals("form-1", dto.getFormConfig().getFormId());
        assertEquals(2, dto.getFormConfigs().size());
    }

    @Test
    void taskDetailKeepsSingleFormConfigAndSupportsMultipleFormConfigs() {
        TaskDetailDTO dto = new TaskDetailDTO();
        TaskDetailDTO.FormConfigDTO first = new TaskDetailDTO.FormConfigDTO();
        first.setEntityFormId("form-1");
        TaskDetailDTO.FormConfigDTO second = new TaskDetailDTO.FormConfigDTO();
        second.setEntityFormId("form-2");

        dto.setFormConfig(first);
        dto.setFormConfigs(List.of(first, second));

        assertEquals("form-1", dto.getFormConfig().getEntityFormId());
        assertEquals(2, dto.getFormConfigs().size());
    }
}
