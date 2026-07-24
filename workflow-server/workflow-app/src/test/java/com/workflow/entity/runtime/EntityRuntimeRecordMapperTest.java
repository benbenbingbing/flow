package com.workflow.entity.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
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
                "data_no", "NO-1",
                "status", "PENDING",
                "current_task_assignee", "admin",
                "create_time", now,
                "amount_total", 12,
                "detail_json", "{\"name\":\"明细\"}"
        );

        EntityDataDTO dto = mapper.toDto(row, "expense");

        assertEquals("data-1", dto.getId());
        assertEquals("expense", dto.getEntityCode());
        assertEquals("NO-1", dto.getDataNo());
        assertEquals("PENDING", dto.getStatus());
        assertEquals("admin", dto.getCurrentTaskAssignee());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(12, dto.getData().get("amountTotal"));
        assertInstanceOf(Map.class, dto.getData().get("detailJson"));
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
        dto.setCurrentTaskAssignee("admin");
        dto.setData(Map.of(
                "amountTotal", 12,
                "emptyValue", "",
                "currentTaskAssignee", "ignored",
                "detailRows", Map.of("name", "明细")
        ));

        Map<String, Object> row = mapper.toStorageMap(dto);

        assertEquals("data-1", row.get("id"));
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
     * <p>断言 status 与 currentTaskId 被过滤，amountTotal 转为 amount_total。</p>
     */
    @Test
    void extractRequestCustomDataReturnsDynamicColumnsOnly() {
        Map<String, Object> request = Map.of("data", Map.of(
                "status", "APPROVED",
                "currentTaskId", "task-1",
                "amountTotal", 12
        ));

        Map<String, Object> customData = mapper.extractRequestCustomData(request);

        assertEquals(Map.of("amount_total", 12), customData);
    }
}
