package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityFormMetadataPatchRequest;
import com.workflow.entity.EntityForm;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.service.config.EntityFormConfigurationValidator;
import com.workflow.service.config.EntityListConfigurationValidator;
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
                                new ObjectMapper()));

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
                Map.of("sourceId", "source-init")));

        service.patchForm("form-1", request);

        ArgumentCaptor<EntityForm> captor =
                ArgumentCaptor.forClass(EntityForm.class);
        verify(formValidator).validateForm(
                captor.capture());
        assertEquals(
                "{\"FORM_INIT\":{\"sourceId\":\"source-init\"}}",
                captor.getValue()
                        .getDataSourceBindingsDocument());
        assertEquals(5, captor.getValue().getRevision());
    }
}
