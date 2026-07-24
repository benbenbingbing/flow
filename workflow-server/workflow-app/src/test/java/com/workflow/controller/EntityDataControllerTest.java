package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.PageResult;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityData;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDataActionService;
import com.workflow.service.EntityDataExportService;
import com.workflow.service.EntityDataListConfigService;
import com.workflow.service.permission.EntityActionCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 实体数据控制器单元测试
 */
@WebMvcTest(EntityDataController.class)
public class EntityDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntityDataDynamicService entityDataDynamicService;

    @MockBean
    private EntityDataListConfigService entityDataListConfigService;

    @MockBean
    private EntityDataExportService entityDataExportService;

    @MockBean
    private EntityDataActionService entityDataActionService;

    @MockBean
    private EntityActionCapabilityService entityActionCapabilityService;

    @Autowired
    private ObjectMapper objectMapper;

    /** 每个测试前初始化的实体数据测试 DTO */
    private EntityDataDTO testData;

    /** 初始化测试用实体数据 DTO，含基础字段与自定义数据 Map */
    @BeforeEach
    void setUp() {
        testData = new EntityDataDTO();
        testData.setId("1");
        testData.setEntityCode("test_entity");
        testData.setDataNo("TEST-001");
        testData.setTitle("测试数据");
        testData.setSubmitterId("user1");
        testData.setSubmitterName("张三");
        testData.setStatus(EntityData.DataStatus.PENDING.name());
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("name", "测试");
        dataMap.put("amount", 100);
        testData.setData(dataMap);
    }

    /** 测试按实体查询数据列表接口，断言返回 200 且数据编码与编号正确 */
    @Test
    void testListByEntity() throws Exception {
        List<EntityDataDTO> dataList = Arrays.asList(testData);
        when(entityDataListConfigService.findListWithConfig("test_entity", null, null)).thenReturn(dataList);

        mockMvc.perform(get("/api/entity-data/entity/test_entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].entityCode").value("test_entity"))
                .andExpect(jsonPath("$.data[0].dataNo").value("TEST-001"));

        verify(entityDataListConfigService, times(1)).findListWithConfig("test_entity", null, null);
    }

    /**
     * 测试分页查询接口，断言兼容旧端点并返回 PageResult 结构。
     *
     * <p>场景：传入 pageNum 和 pageSize 参数，断言返回分页数据且未调用非分页查询方法。</p>
     */
    @Test
    void pagedListKeepsLegacyEndpointAndReturnsPageResult() throws Exception {
        PageResult<EntityDataDTO> page = new PageResult<>(
                List.of(testData),
                25,
                2,
                10);
        when(entityDataListConfigService.findPageWithConfig(
                "test_entity",
                null,
                null,
                2,
                10)).thenReturn(page);

        mockMvc.perform(get("/api/entity-data/entity/test_entity")
                        .param("pageNum", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(25))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value("1"));

        verify(entityDataListConfigService).findPageWithConfig(
                "test_entity",
                null,
                null,
                2,
                10);
        verify(entityDataListConfigService, never())
                .findListWithConfig(anyString(), any(), any());
    }

    /** 测试按 ID 查询实体数据详情接口，断言返回 200 且数据字段正确 */
    @Test
    void testGetById() throws Exception {
        when(entityDataActionService.getDetail("test_entity", "1", null)).thenReturn(testData);

        mockMvc.perform(get("/api/entity-data/entity/test_entity/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.dataNo").value("TEST-001"));

        verify(entityDataActionService, times(1)).getDetail("test_entity", "1", null);
    }

    /** 测试按流程实例查询实体数据详情接口，断言返回 200 且 ID 正确 */
    @Test
    void testGetByProcessInstance() throws Exception {
        when(entityDataActionService.getDetailByProcessInstance(
                "test_entity",
                "proc-1",
                null)).thenReturn(testData);

        mockMvc.perform(get("/api/entity-data/entity/test_entity/process/proc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityDataActionService, times(1)).getDetailByProcessInstance(
                "test_entity",
                "proc-1",
                null);
    }

    /** 测试新增实体数据接口，断言返回 200 且数据编号正确 */
    @Test
    void testSave() throws Exception {
        when(entityDataActionService.create(any(EntityDataDTO.class))).thenReturn(testData);

        mockMvc.perform(post("/api/entity-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dataNo").value("TEST-001"));

        verify(entityDataActionService, times(1)).create(any(EntityDataDTO.class));
    }

    /** 测试更新实体数据接口，断言返回 200 且 update 方法被正确调用 */
    @Test
    void testUpdate() throws Exception {
        when(entityDataActionService.update(eq("test_entity"), eq("1"), isNull(), anyMap())).thenReturn(testData);

        mockMvc.perform(post("/api/entity-data/entity/test_entity/detail/1/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityDataActionService, times(1)).update(eq("test_entity"), eq("1"), isNull(), anyMap());
    }

    /** 测试删除实体数据接口，断言返回 200 且 delete 方法被正确调用 */
    @Test
    void testDelete() throws Exception {
        doNothing().when(entityDataActionService).delete("test_entity", "1", null);

        mockMvc.perform(post("/api/entity-data/entity/test_entity/detail/1/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(entityDataActionService, times(1)).delete("test_entity", "1", null);
    }
}
