package com.workflow.entity.form.api.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.entity.form.api.response.EntityFormResponse;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.PublishedFormUniquePrecheckService;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiConfigDraftMetadataService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证 UTC 容器与本地开发环境的表单时间均能通过 HTTP 保留同一真实时刻。 */
@ResourceLock(Resources.TIME_ZONE)
class EntityFormControllerTimestampTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Shanghai"})
    void listReturnsUnambiguousTimeWithoutChangingSnapshots(String serverZone) throws Exception {
        TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
            Instant created = Instant.parse("2026-09-15T03:34:31Z");
            Instant updated = Instant.parse("2026-09-15T04:28:25Z");
            EntityForm form = new EntityForm();
            form.setId("form-1");
            form.setEntityId("entity-1");
            form.setFormKey("all_entity_form001");
            form.setFormName("默认表单");
            form.setStatus(1);
            form.setCreateTime(LocalDateTime.ofInstant(created, ZoneId.systemDefault()));
            form.setUpdateTime(LocalDateTime.ofInstant(updated, ZoneId.systemDefault()));
            String snapshotBeforeResponse = mapper.writeValueAsString(form);

            EntityFormService service = mock(EntityFormService.class);
            when(service.getFormsByEntityId("entity-1")).thenReturn(List.of(form));
            MockMvc mvc = controller(service);
            String json = mvc.perform(get("/api/entity-form/entity/entity-1"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ObjectNode item = (ObjectNode) mapper.readTree(json).path("data").get(0);

            assertEquals(created, OffsetDateTime.parse(item.path("createTime").asText()).toInstant());
            assertEquals(updated, OffsetDateTime.parse(item.path("updateTime").asText()).toInstant());
            // 统一输出 Z，兼容新增接口的 LocalDateTime 反序列化；审计时间由保存服务重新生成。
            assertTrue(item.path("createTime").asText().endsWith("Z"));
            assertEquals(form.getFormKey(), mapper.treeToValue(item, EntityForm.class).getFormKey(),
                    "兼容客户端回传带时区的表单元数据");
            assertEquals("2026-09-15T11:34:31", OffsetDateTime.parse(item.path("createTime").asText())
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString());
            assertFalse(item.has("form"), "响应字段必须保持原有平铺结构");

            // 只改变 HTTP 审计时间的表示，表单的其他字段和发布快照不能发生变化。
            ObjectNode expectedFields = (ObjectNode) mapper.readTree(snapshotBeforeResponse);
            expectedFields.remove(List.of("createTime", "updateTime"));
            item.remove(List.of("createTime", "updateTime"));
            assertEquals(expectedFields, item);
            assertEquals(snapshotBeforeResponse, mapper.writeValueAsString(form));
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void missingFormAndNullTimestampsRemainNull() throws Exception {
        assertNull(EntityFormResponse.from(null));
        EntityForm form = new EntityForm();
        ObjectNode response = mapper.valueToTree(EntityFormResponse.from(form));
        assertTrue(response.path("createTime").isNull());
        assertTrue(response.path("updateTime").isNull());

        EntityFormService service = mock(EntityFormService.class);
        String json = controller(service).perform(get("/api/entity-form/entity/entity-1/default"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(mapper.readTree(json).path("data").isNull());
    }

    private MockMvc controller(EntityFormService service) {
        return MockMvcBuilders.standaloneSetup(new EntityFormController(
                        service,
                        mock(UiConfigDraftMetadataService.class),
                        mock(UiConfigurationAccessService.class),
                        mock(EntityActionCapabilityService.class),
                        mock(PublishedFormUniquePrecheckService.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }
}
