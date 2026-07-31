package com.workflow.controller;

import com.workflow.entity.definition.api.web.EntityDefinitionController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.api.request.EntityDefinitionOptionResolveRequest;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionOptionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.EntityDefinitionOptionService;
import com.workflow.entity.definition.application.EntityDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 实体定义控制器单元测试
 */
@WebMvcTest(EntityDefinitionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EntityDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntityDefinitionService entityService;

    @MockitoBean
    private EntityDefinitionOptionService entityOptionService;

    @Autowired
    private ObjectMapper objectMapper;

    /** 每个测试前初始化的实体定义测试 DTO */
    private EntityDefinitionDTO testEntity;

    /** 初始化测试用实体定义 DTO，含实体编码、名称与状态 */
    @BeforeEach
    void setUp() {
        testEntity = new EntityDefinitionDTO();
        testEntity.setId("1");
        testEntity.setEntityCode("test_entity");
        testEntity.setEntityName("测试实体");
        testEntity.setDescription("测试用实体");
        testEntity.setStatus(EntityDefinition.Status.DRAFT);
    }

    /** 测试分页查询实体定义接口，断言返回 200 且分页数据包含预期实体 */
    @Test
    void testList() throws Exception {
        // 准备数据
        PageResult<EntityDefinitionDTO> pageResult = new PageResult<>(
                Arrays.asList(testEntity), 1, 1, 10);
        when(entityService.findPage(any(EntityDefinitionQueryDTO.class))).thenReturn(pageResult);

        // 执行请求并验证
        mockMvc.perform(get("/api/entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].entityCode").value("test_entity"))
                .andExpect(jsonPath("$.data.records[0].entityName").value("测试实体"));

        verify(entityService, times(1)).findPage(any(EntityDefinitionQueryDTO.class));
    }

    /** 测试实体选择器分页接口只返回轻量选项。 */
    @Test
    void testOptions() throws Exception {
        EntityDefinitionOptionDTO option = new EntityDefinitionOptionDTO();
        option.setId("1");
        option.setEntityCode("test_entity");
        option.setEntityName("测试实体");
        option.setStatus(EntityDefinition.Status.DRAFT);
        PageResult<EntityDefinitionOptionDTO> pageResult = new PageResult<>(
                List.of(option), 1, 1, 10);
        when(entityOptionService.findPage(any(EntityDefinitionQueryDTO.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/entity/options")
                        .param("keyword", "测试")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("1"))
                .andExpect(jsonPath("$.data.records[0].entityCode").value("test_entity"))
                .andExpect(jsonPath("$.data.records[0].entityName").value("测试实体"));

        verify(entityOptionService).findPage(any(EntityDefinitionQueryDTO.class));
    }

    /** 测试已保存实体编码可以批量解析，支持分页多选回显。 */
    @Test
    void testResolveOptions() throws Exception {
        EntityDefinitionOptionDTO option = new EntityDefinitionOptionDTO();
        option.setId("1");
        option.setEntityCode("test_entity");
        option.setEntityName("测试实体");
        when(entityOptionService.resolve(any(EntityDefinitionOptionResolveRequest.class)))
                .thenReturn(List.of(option));

        mockMvc.perform(post("/api/entity/options/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityCodes\":[\"test_entity\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].entityName").value("测试实体"));

        verify(entityOptionService).resolve(any(EntityDefinitionOptionResolveRequest.class));
    }

    /** 测试按 ID 查询实体定义接口，断言返回 200 且实体编码正确 */
    @Test
    void testGetById() throws Exception {
        when(entityService.findById("1")).thenReturn(testEntity);

        mockMvc.perform(get("/api/entity/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.entityCode").value("test_entity"));

        verify(entityService, times(1)).findById("1");
    }

    /** 测试按编码查询实体定义接口，断言返回 200 且实体编码正确 */
    @Test
    void testGetByCode() throws Exception {
        when(entityService.findByCode("test_entity")).thenReturn(testEntity);

        mockMvc.perform(get("/api/entity/code/test_entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.entityCode").value("test_entity"));

        verify(entityService, times(1)).findByCode("test_entity");
    }

    /** 测试新增实体定义接口，断言返回 200 且实体编码正确 */
    @Test
    void testCreate() throws Exception {
        when(entityService.save(any(EntityDefinitionDTO.class))).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testEntity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.entityCode").value("test_entity"));

        verify(entityService, times(1)).save(any(EntityDefinitionDTO.class));
    }

    /** 测试更新实体定义接口，断言返回 200 且 update 方法被正确调用 */
    @Test
    void testUpdate() throws Exception {
        when(entityService.update(eq("1"), any(EntityDefinitionDTO.class))).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity/1/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testEntity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).update(eq("1"), any(EntityDefinitionDTO.class));
    }

    /** 测试删除实体定义接口，断言返回 200 且 delete 方法被正确调用 */
    @Test
    void testDelete() throws Exception {
        doNothing().when(entityService).delete("1");

        mockMvc.perform(post("/api/entity/1/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(entityService, times(1)).delete("1");
    }

    /** 测试发布实体定义接口，断言返回 200 且 publish 方法被正确调用 */
    @Test
    void testPublish() throws Exception {
        when(entityService.publish(
                eq("1"),
                nullable(String.class),
                nullable(String.class),
                nullable(ConfigMigrationPublishRequest.class))).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).publish(
                eq("1"),
                nullable(String.class),
                nullable(String.class),
                nullable(ConfigMigrationPublishRequest.class));
    }

    /** 测试绑定工作流接口，断言返回 200 且 bindWorkflow 方法被正确调用 */
    @Test
    void testBindWorkflow() throws Exception {
        when(entityService.bindWorkflow("1", "2")).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity/1/workflow-binding/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionId\":\"2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).bindWorkflow("1", "2");
    }
}
