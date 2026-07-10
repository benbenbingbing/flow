package com.workflow.service;

import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体流程状态映射服务业务测试
 * 覆盖映射配置的保存（空配置保留、非空替换）、查询
 */
class EntityFlowStatusBusinessTest {

    private EntityFlowStatusMappingMapper mapper;
    private EntityFlowStatusService service;

    @BeforeEach
    void setUp() {
        mapper = mock(EntityFlowStatusMappingMapper.class);
        service = new EntityFlowStatusService(mapper);
    }

    @Nested
    @DisplayName("保存状态映射")
    class SaveMappings {

        @Test
        @DisplayName("非空映射 - 先删旧再逐条插入")
        void saveMappings_nonEmpty_replace() {
            EntityFlowStatusMapping m1 = new EntityFlowStatusMapping();
            m1.setSequenceFlowId("flow1");
            m1.setEntityStatusCode("APPROVING");

            EntityFlowStatusMapping m2 = new EntityFlowStatusMapping();
            m2.setSequenceFlowId("flow2");
            m2.setEntityStatusCode("APPROVED");

            service.saveStatusMappings("pc1", "processKey", "expense", List.of(m1, m2));

            verify(mapper).deleteByProcessConfigId("pc1");
            verify(mapper).insert(argThat((com.workflow.entity.EntityFlowStatusMapping m) ->
                    "pc1".equals(m.getProcessConfigId()) && "processKey".equals(m.getProcessKey()) &&
                    "expense".equals(m.getEntityCode()) && m.getDeleted() != null && m.getDeleted() == 0));
            verify(mapper, times(2)).insert(any());
        }

        @Test
        @DisplayName("空映射列表 - 保留原配置不覆盖（防 BPMN 属性清理丢数据）")
        void saveMappings_empty_preserveOriginal() {
            service.saveStatusMappings("pc1", "processKey", "expense", null);

            verify(mapper, never()).deleteByProcessConfigId(anyString());
            verify(mapper, never()).insert(any());
        }

        @Test
        @DisplayName("空 List - 同样保留原配置")
        void saveMappings_emptyList_preserveOriginal() {
            service.saveStatusMappings("pc1", "processKey", "expense", List.of());

            verify(mapper, never()).deleteByProcessConfigId(anyString());
            verify(mapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("查询状态映射")
    class QueryMappings {

        @Test
        @DisplayName("按流程配置ID查询所有映射")
        void getStatusMappings() {
            EntityFlowStatusMapping m = new EntityFlowStatusMapping();
            m.setId("m1");
            when(mapper.findByProcessConfigId("pc1")).thenReturn(List.of(m));

            List<EntityFlowStatusMapping> result = service.getStatusMappings("pc1");

            assertEquals(1, result.size());
            assertEquals("m1", result.get(0).getId());
        }

        @Test
        @DisplayName("按源节点查询出向流转")
        void getStatusMappingsBySourceNode() {
            EntityFlowStatusMapping m = new EntityFlowStatusMapping();
            m.setSourceNodeId("node1");
            m.setTargetNodeId("node2");
            when(mapper.findBySourceNode("pc1", "node1")).thenReturn(List.of(m));

            List<EntityFlowStatusMapping> result = service.getStatusMappingsBySourceNode("pc1", "node1");

            assertEquals(1, result.size());
            assertEquals("node2", result.get(0).getTargetNodeId());
        }

        @Test
        @DisplayName("精确按 source+target 查单条映射")
        void getStatusMapping() {
            EntityFlowStatusMapping m = new EntityFlowStatusMapping();
            m.setEntityStatusCode("APPROVED");
            when(mapper.findBySourceAndTarget("pc1", "node1", "node2")).thenReturn(m);

            EntityFlowStatusMapping result = service.getStatusMapping("pc1", "node1", "node2");

            assertEquals("APPROVED", result.getEntityStatusCode());
        }

        @Test
        @DisplayName("按流程标识查询全部映射")
        void getStatusMappingsByProcessKey() {
            EntityFlowStatusMapping m = new EntityFlowStatusMapping();
            when(mapper.findByProcessKey("expense_process")).thenReturn(List.of(m));

            List<EntityFlowStatusMapping> result = service.getStatusMappingsByProcessKey("expense_process");

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("删除状态映射")
    class DeleteMappings {

        @Test
        @DisplayName("按流程配置ID删除所有映射")
        void deleteByProcessConfigId() {
            service.deleteByProcessConfigId("pc1");

            verify(mapper).deleteByProcessConfigId("pc1");
        }
    }
}
