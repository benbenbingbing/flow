package com.workflow.dto;

import com.workflow.entity.form.api.response.FormConfigDTO;

import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.task.api.response.TaskDetailDTO;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 表单配置兼容性单元测试。
 *
 * <p>验证流程进度 DTO 与任务详情 DTO 保留列表字段兼容性，
 * 但运行时只暴露一个节点办理表单。</p>
 */
class FormConfigsCompatibilityTest {

    /**
     * 流程进度 DTO 应将历史多表单输入收敛为首个表单。
     */
    @Test
    void processProgressKeepsOnlyFirstFormConfig() {
        ProcessProgressDTO dto = new ProcessProgressDTO();
        ProcessProgressDTO.FormConfigDTO first = new ProcessProgressDTO.FormConfigDTO();
        first.setFormId("form-1");
        ProcessProgressDTO.FormConfigDTO second = new ProcessProgressDTO.FormConfigDTO();
        second.setFormId("form-2");

        dto.setFormConfig(first);
        dto.setFormConfigs(List.of(first, second));

        assertEquals("form-1", dto.getFormConfig().getFormId());
        assertEquals(1, dto.getFormConfigs().size());
        assertEquals("form-1", dto.getFormConfigs().get(0).getFormId());
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
