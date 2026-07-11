package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityData;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDataExportService;
import com.workflow.service.EntityDataListConfigService;
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

    @Autowired
    private ObjectMapper objectMapper;

    private EntityDataDTO testData;

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

    @Test
    void testListByEntity() throws Exception {
        List<EntityDataDTO> dataList = Arrays.asList(testData);
        when(entityDataDynamicService.findByEntityCode("test_entity")).thenReturn(dataList);

        mockMvc.perform(get("/api/entity-data/entity/test_entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].entityCode").value("test_entity"))
                .andExpect(jsonPath("$.data[0].dataNo").value("TEST-001"));

        verify(entityDataDynamicService, times(1)).findByEntityCode("test_entity");
    }

    @Test
    void testGetById() throws Exception {
        when(entityDataDynamicService.findById("test_entity", "1")).thenReturn(testData);

        mockMvc.perform(get("/api/entity-data/entity/test_entity/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.dataNo").value("TEST-001"));

        verify(entityDataDynamicService, times(1)).findById("test_entity", "1");
    }

    @Test
    void testGetByProcessInstance() throws Exception {
        when(entityDataDynamicService.findByProcessInstanceId("test_entity", "proc-1")).thenReturn(testData);

        mockMvc.perform(get("/api/entity-data/entity/test_entity/process/proc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityDataDynamicService, times(1)).findByProcessInstanceId("test_entity", "proc-1");
    }

    @Test
    void testSave() throws Exception {
        when(entityDataDynamicService.save(any(EntityDataDTO.class))).thenReturn(testData);

        mockMvc.perform(post("/api/entity-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dataNo").value("TEST-001"));

        verify(entityDataDynamicService, times(1)).save(any(EntityDataDTO.class));
    }

    @Test
    void testUpdate() throws Exception {
        when(entityDataDynamicService.update(eq("test_entity"), eq("1"), anyMap())).thenReturn(testData);

        mockMvc.perform(put("/api/entity-data/entity/test_entity/detail/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"));

        verify(entityDataDynamicService, times(1)).update(eq("test_entity"), eq("1"), anyMap());
    }

    @Test
    void testDelete() throws Exception {
        doNothing().when(entityDataDynamicService).delete("test_entity", "1");

        mockMvc.perform(delete("/api/entity-data/entity/test_entity/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(entityDataDynamicService, times(1)).delete("test_entity", "1");
    }
}
