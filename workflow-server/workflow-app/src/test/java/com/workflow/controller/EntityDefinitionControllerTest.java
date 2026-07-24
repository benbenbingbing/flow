package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.PageResult;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityDefinitionQueryDTO;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.EntityDefinition;
import com.workflow.service.EntityDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
public class EntityDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntityDefinitionService entityService;

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
