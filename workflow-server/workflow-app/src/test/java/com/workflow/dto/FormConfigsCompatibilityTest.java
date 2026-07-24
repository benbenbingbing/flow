package com.workflow.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 表单配置兼容性单元测试。
 *
 * <p>验证流程进度 DTO 与任务详情 DTO 同时支持单个表单配置(getFormConfig)
 * 与多表单配置列表(getFormConfigs)的兼容设计。</p>
 */
class FormConfigsCompatibilityTest {

    /**
     * 流程进度 DTO 应保留单个表单配置并支持多表单配置列表。
     *
     * <p>断言 getFormConfig 返回首个表单，getFormConfigs 返回全部表单列表。</p>
     */
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

    /**
     * 任务详情 DTO 应保留单个表单配置并支持多表单配置列表。
     *
     * <p>断言 getFormConfig 返回首个表单，getFormConfigs 返回全部表单列表。</p>
     */
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
