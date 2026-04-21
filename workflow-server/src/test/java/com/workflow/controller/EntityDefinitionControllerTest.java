package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
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

    private EntityDefinitionDTO testEntity;

    @BeforeEach
    void setUp() {
        testEntity = new EntityDefinitionDTO();
        testEntity.setId("1");
        testEntity.setEntityCode("test_entity");
        testEntity.setEntityName("测试实体");
        testEntity.setDescription("测试用实体");
        testEntity.setStatus(EntityDefinition.Status.DRAFT);
    }

    @Test
    void testList() throws Exception {
        // 准备数据
        List<EntityDefinitionDTO> entityList = Arrays.asList(testEntity);
        when(entityService.findAll()).thenReturn(entityList);

        // 执行请求并验证
        mockMvc.perform(get("/api/entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].entityCode").value("test_entity"))
                .andExpect(jsonPath("$.data[0].entityName").value("测试实体"));

        verify(entityService, times(1)).findAll();
    }

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

    @Test
    void testGetByCode() throws Exception {
        when(entityService.findByCode("test_entity")).thenReturn(testEntity);

        mockMvc.perform(get("/api/entity/code/test_entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.entityCode").value("test_entity"));

        verify(entityService, times(1)).findByCode("test_entity");
    }

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

    @Test
    void testUpdate() throws Exception {
        when(entityService.update(eq("1"), any(EntityDefinitionDTO.class))).thenReturn(testEntity);

        mockMvc.perform(put("/api/entity/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testEntity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).update(eq("1"), any(EntityDefinitionDTO.class));
    }

    @Test
    void testDelete() throws Exception {
        doNothing().when(entityService).delete("1");

        mockMvc.perform(delete("/api/entity/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(entityService, times(1)).delete("1");
    }

    @Test
    void testPublish() throws Exception {
        when(entityService.publish(anyString(), anyString(), anyString())).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).publish(anyString(), anyString(), anyString());
    }

    @Test
    void testBindProcess() throws Exception {
        when(entityService.bindProcess("1", "2")).thenReturn(testEntity);

        mockMvc.perform(post("/api/entity/1/bind-process/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityService, times(1)).bindProcess("1", "2");
    }
}
