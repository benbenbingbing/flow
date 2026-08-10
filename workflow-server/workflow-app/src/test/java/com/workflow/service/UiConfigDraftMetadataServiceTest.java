package com.workflow.service;

import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.ui.application.UiConfigDraftMetadataService;
import com.workflow.entity.ui.application.UiAvailableOperationService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.api.request.EntityFormMetadataPatchRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UI 配置草稿元数据服务测试。
 *
 * <p>被测对象：{@link UiConfigDraftMetadataService}，覆盖表单草稿元数据按乐观锁修订号（revision）补丁更新的场景。
 */
class UiConfigDraftMetadataServiceTest {

    /**
     * 测试通过乐观锁修订号补丁更新表单级数据源绑定：
     * 验证传入校验器的表单数据源绑定 JSON 文档与修订号自增（4 -> 5）符合预期。
     */
    @Test
    void patchesFormLevelDataSourceBindingsWithRevisionCas() {
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityFormService formService =
                mock(EntityFormService.class);
        EntityFormConfigurationValidator formValidator =
                mock(EntityFormConfigurationValidator.class);
        UiConfigDraftMetadataService service =
                new UiConfigDraftMetadataService(
                        formMapper,
                        mock(EntityListConfigMapper.class),
                        formService,
                        mock(EntityListConfigService.class),
                        formValidator,
                        mock(EntityListConfigurationValidator.class),
                        mock(EntityListRelationalConfigService.class),
                        new JsonDocumentCodec(
                                new ObjectMapper()),
                        mock(UiAvailableOperationService.class));

        EntityForm current = new EntityForm();
        current.setId("form-1");
        current.setEntityId("entity-1");
        current.setFormName("费用表单");
        current.setFormKey("expense_form");
        current.setRevision(4);
        when(formService.getById("form-1"))
                .thenReturn(current);
        when(formMapper.update(any(), any()))
                .thenReturn(1);

        EntityFormMetadataPatchRequest request =
                new EntityFormMetadataPatchRequest();
        request.setExpectedRevision(4);
        request.setDataSourceBindings(Map.of(
                "FORM_INIT",
                Map.of(
                        "serviceId", "source-init",
                        "operationCode", "initializeForm")));

        service.patchForm("form-1", request);

        ArgumentCaptor<EntityForm> captor =
                ArgumentCaptor.forClass(EntityForm.class);
        verify(formValidator).validateForm(
                captor.capture());
        assertEquals(
                "{\"FORM_INIT\":{\"operationCode\":\"initializeForm\",\"serviceId\":\"source-init\"}}",
                captor.getValue()
                        .getDataSourceBindingsDocument());
        assertEquals(5, captor.getValue().getRevision());
    }
}
