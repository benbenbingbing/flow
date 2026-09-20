package com.workflow.entity.runtime;

import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 实体运行时记录映射器单元测试。
 *
 * <p>被测对象为 {@link EntityRuntimeRecordMapper}，验证数据库行与 DTO 之间的双向转换：
 * 行转 DTO(系统字段与自定义字段映射)、DTO 转存储 Map(驼峰转下划线、空值过滤)，
     * 以及从请求中提取动态列数据。</p>
 */
class EntityRuntimeRecordMapperTest {

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 被测映射器实例 */
    private final EntityRuntimeRecordMapper mapper = new EntityRuntimeRecordMapper(objectMapper);

    /**
     * 行转 DTO 应正确映射系统字段与自定义字段。
     *
     * <p>断言 id、实体编码、编号、状态、当前处理人、创建时间均正确，
     * 驼峰字段 amount_total 映射为 amountTotal，JSON 字段解析为 Map。</p>
     */
    @Test
    void toDtoMapsSystemFieldsAndCustomFields() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = Map.of(
                "id", "data-1",
                "code", "NO-1",
                "name", "费用申请",
                "status", "PENDING",
                "current_task_assignee", "admin",
                "create_time", now,
                "amount_total", 12,
                "detail_json", "{\"name\":\"明细\"}"
        );

        EntityDataDTO dto = mapper.toDto(row, "expense");

        assertEquals("data-1", dto.getId());
        assertEquals("expense", dto.getEntityCode());
        assertEquals("NO-1", dto.getCode());
        assertEquals("费用申请", dto.getName());
        assertEquals("PENDING", dto.getStatus());
        assertEquals("admin", dto.getCurrentTaskAssignee());
        assertEquals(now, dto.getCreateTime());
        assertEquals(12, dto.getData().get("amountTotal"));
        assertInstanceOf(Map.class, dto.getData().get("detailJson"));
    }

    /**
     * 行转 DTO 应按实体字段定义还原精确字段编码。
     */
    @Test
    void toDtoPreservesConfiguredSnakeCaseAndCamelCaseFieldCodes() {
        EntityField snakeCaseField = new EntityField();
        snakeCaseField.setFieldCode("requirement_type");
        snakeCaseField.setDbColumnName("requirement_type");
        EntityField camelCaseField = new EntityField();
        camelCaseField.setFieldCode("businessOwnerId");
        camelCaseField.setDbColumnName("business_owner_id");

        EntityDataDTO dto = mapper.toDto(
                Map.of(
                        "id", "data-1",
                        "requirement_type", "NEW_FEATURE",
                        "business_owner_id", "user-1",
                        "legacy_field", "legacy"),
                "requirement",
                java.util.List.of(snakeCaseField, camelCaseField));

        assertEquals("NEW_FEATURE", dto.getData().get("requirement_type"));
        assertEquals("user-1", dto.getData().get("businessOwnerId"));
        assertEquals("legacy", dto.getData().get("legacyField"));
    }

    /**
     * DTO 转存储 Map 应保留系统字段并规范化自定义字段。
     *
     * <p>断言 id 与 current_task_assignee 正确，驼峰转下划线 amount_total，
     * 空值字段被过滤，DTO 中的系统字段覆盖被忽略，复杂对象序列化为 JSON。</p>
     */
    @Test
    void toStorageMapKeepsSystemFieldsAndNormalizesCustomFields() throws Exception {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId("data-1");
        dto.setEntityCode("expense");
        dto.setName("费用申请");
        dto.setCode("EXP-001");
        dto.setCurrentTaskAssignee("admin");
        dto.setData(Map.of(
                "amountTotal", 12,
                "emptyValue", "",
                "currentTaskAssignee", "ignored",
                "detailRows", Map.of("name", "明细")
        ));

        Map<String, Object> row = mapper.toStorageMap(dto);

        assertEquals("data-1", row.get("id"));
        assertEquals("费用申请", row.get("name"));
        assertEquals("EXP-001", row.get("code"));
        assertFalse(row.containsKey("data_no"));
        assertFalse(row.containsKey("title"));
        Map<String, Object> serialized = objectMapper.convertValue(dto, new TypeReference<>() { });
        assertFalse(serialized.containsKey("dataNo"));
        assertFalse(serialized.containsKey("title"));
        assertEquals("admin", row.get("current_task_assignee"));
        assertEquals(12, row.get("amount_total"));
        assertNull(row.get("empty_value"));
        assertFalse(row.containsKey("entity_code"));
        Map<String, Object> detailRows = objectMapper.readValue(
                String.valueOf(row.get("detail_rows")),
                new TypeReference<>() {
                }
        );
        assertEquals("明细", detailRows.get("name"));
    }

    /**
     * 从请求 Map 提取动态列数据应仅返回自定义字段并转换为下划线命名。
     *
     * <p>断言系统字段与运行时上下文被过滤，amountTotal 转为 amount_total。</p>
     */
    @Test
    void extractRequestCustomDataReturnsDynamicColumnsOnly() {
        Map<String, Object> request = Map.of("data", Map.of(
                "status", "APPROVED",
                "currentTaskId", "task-1",
                "entityCode", "expense",
                "listKey", "default",
                "data", Map.of("nested", true),
                "amountTotal", 12
        ));

        Map<String, Object> customData = mapper.extractRequestCustomData(request);

        assertEquals(Map.of("amount_total", 12), customData);
    }

    /** 主键和审计字段使用顶层契约，客户端嵌套数据不能改写系统维护列。 */
    @Test
    void auditFieldsAreReadableButExcludedFromCustomWrites() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 20, 10, 0);
        LocalDateTime updated = created.plusHours(1);
        Map<String, Object> columns = Map.of(
                "id", "record-1", "create_time", created, "update_time", updated,
                "create_by", "creator", "update_by", "updater", "deleted", 0);
        EntityDataDTO dto = mapper.toDto(columns, "expense");
        assertEquals(created, dto.getCreateTime());
        assertEquals(updated, dto.getUpdateTime());
        assertEquals("creator", dto.getCreateBy());
        assertEquals("updater", dto.getUpdateBy());
        assertEquals(false, dto.getDeleted());
        assertEquals(Map.of(), dto.getData());
        assertEquals(true, mapper.toDto(Map.of("deleted", true), "expense").getDeleted());
        assertEquals(true, mapper.toDto(Map.of("deleted", 1), "expense").getDeleted());

        Map<String, Object> patch = Map.of(
                "id", "forged", "create_time", updated, "update_time", created,
                "create_by", "forged", "update_by", "forged", "deleted", true,
                "amountTotal", 10);
        ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> json = jsonMapper.convertValue(dto, new TypeReference<>() { });
        assertEquals("creator", json.get("create_by"));
        assertEquals("updater", json.get("update_by"));
        org.junit.jupiter.api.Assertions.assertTrue(json.containsKey("create_time"));
        org.junit.jupiter.api.Assertions.assertTrue(json.containsKey("update_time"));
        for (String retired : java.util.List.of("createdAt", "updatedAt", "createdBy", "updatedBy",
                "createTime", "updateTime", "createBy", "updateBy")) {
            assertFalse(json.containsKey(retired), retired);
        }
        EntityDataDTO roundTrip = jsonMapper.convertValue(json, EntityDataDTO.class);
        assertEquals(created, roundTrip.getCreateTime());
        assertEquals(updated, roundTrip.getUpdateTime());
        dto.setData(patch);
        assertEquals(Map.of("id", "record-1", "amount_total", 10), mapper.toStorageMap(dto));
        assertEquals(Map.of("amount_total", 10), mapper.extractRequestCustomData(Map.of("data", patch)));
        Map.of("create_time", "create_time", "update_time", "update_time",
                "create_by", "create_by", "update_by", "update_by")
                .forEach((code, column) -> assertEquals(column, mapper.toColumnName(code)));
    }
}
