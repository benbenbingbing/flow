package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体定义服务业务测试
 * 覆盖完整的实体设计生命周期：创建 → 字段管理 → 更新 → 发布 → 绑定流程 → 删除
 */
class EntityDefinitionBusinessTest {

    private EntityDefinitionMapper entityMapper;
    private EntityFieldMapper fieldMapper;
    private EntityRelationMapper relationMapper;
    private ProcessDefinitionConfigMapper processMapper;
    private EntityDataDynamicMapper entityDataDynamicMapper;
    private DynamicTableService dynamicTableService;
    private EntityPublishHistoryService publishHistoryService;
    private EntityFieldFileItemService fileItemService;

    private EntityDefinitionService service;

    @BeforeEach
    void setUp() {
        entityMapper = mock(EntityDefinitionMapper.class);
        fieldMapper = mock(EntityFieldMapper.class);
        relationMapper = mock(EntityRelationMapper.class);
        processMapper = mock(ProcessDefinitionConfigMapper.class);
        entityDataDynamicMapper = mock(EntityDataDynamicMapper.class);
        dynamicTableService = mock(DynamicTableService.class);
        publishHistoryService = mock(EntityPublishHistoryService.class);
        fileItemService = mock(EntityFieldFileItemService.class);

        service = new EntityDefinitionService(
                entityMapper, fieldMapper, relationMapper, processMapper,
                entityDataDynamicMapper, dynamicTableService,
                publishHistoryService, fileItemService, new ObjectMapper());
    }

    // ==================== 创建实体 ====================

    @Nested
    @DisplayName("创建实体")
    class CreateEntity {

        @Test
        @DisplayName("正常创建实体 - 自动注入系统字段，保存自定义字段，同步关系")
        void createEntity_success() {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode("expense");
            dto.setEntityName("费用报销");

            EntityFieldDTO amountField = new EntityFieldDTO();
            amountField.setFieldCode("amount");
            amountField.setFieldName("金额");
            amountField.setFieldType(EntityField.FieldType.DECIMAL);
            dto.setFields(List.of(amountField));

            when(entityMapper.selectList(null)).thenReturn(Collections.emptyList());
            when(entityMapper.findByEntityCode("expense")).thenReturn(Optional.empty());

            EntityDefinitionDTO result = service.save(dto);

            assertNotNull(result);
            verify(entityMapper).insert(any(EntityDefinition.class));
            // 验证系统字段注入（9个系统字段 + 1个自定义字段 = 10次 insert）
            verify(fieldMapper, atLeast(9)).insert(argThat(f -> Boolean.TRUE.equals(f.getIsSystem())));
            verify(fieldMapper).insert(argThat(f -> "amount".equals(f.getFieldCode()) && f.getId() == null));
            // 验证关系同步
            verify(relationMapper).deleteByParentEntityId(anyString());
        }

        @Test
        @DisplayName("创建实体 - 实体编码为空时抛异常")
        void createEntity_emptyCode_throwsException() {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode("");

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(dto));
            assertTrue(ex.getMessage().contains("实体编码不能为空"));
        }

        @Test
        @DisplayName("创建实体 - 实体编码重复（不区分大小写）时抛异常")
        void createEntity_duplicateCode_throwsException() {
            EntityDefinition existing = new EntityDefinition();
            existing.setEntityCode("Expense");
            when(entityMapper.selectList(null)).thenReturn(List.of(existing));

            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode("expense");

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(dto));
            assertTrue(ex.getMessage().contains("已存在"));
        }

        @Test
        @DisplayName("创建实体 - 同实体内字段编码重复时抛异常")
        void createEntity_duplicateFieldCode_throwsException() {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode("expense");

            EntityFieldDTO f1 = new EntityFieldDTO();
            f1.setFieldCode("amount");
            EntityFieldDTO f2 = new EntityFieldDTO();
            f2.setFieldCode("amount");
            dto.setFields(List.of(f1, f2));

            when(entityMapper.selectList(null)).thenReturn(Collections.emptyList());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(dto));
            assertTrue(ex.getMessage().contains("字段编码") && ex.getMessage().contains("不能重复"));
        }
    }

    // ==================== 更新实体 ====================

    @Nested
    @DisplayName("更新实体")
    class UpdateEntity {

        @Test
        @DisplayName("未发布实体 - 可以删除非系统字段")
        void updateEntity_draft_deleteFields() {
            EntityDefinition existing = new EntityDefinition();
            existing.setId("e1");
            existing.setEntityCode("expense");
            existing.setStatus(EntityDefinition.Status.DRAFT);

            EntityField customField = new EntityField();
            customField.setId("f1");
            customField.setFieldCode("amount");
            customField.setIsSystem(false);

            when(entityMapper.selectById("e1")).thenReturn(existing);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(customField));

            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityName("费用报销-更新");
            dto.setFields(Collections.emptyList());

            service.update("e1", dto);

            verify(fieldMapper).deleteById("f1");
        }

        @Test
        @DisplayName("已发布实体 - 不允许删除字段，抛异常")
        void updateEntity_published_cannotDeleteFields() {
            EntityDefinition existing = new EntityDefinition();
            existing.setId("e1");
            existing.setEntityCode("expense");
            existing.setStatus(EntityDefinition.Status.PUBLISHED);

            EntityField publishedField = new EntityField();
            publishedField.setId("f1");
            publishedField.setFieldCode("amount");
            publishedField.setIsSystem(false);

            when(entityMapper.selectById("e1")).thenReturn(existing);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(publishedField));

            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityName("费用");
            dto.setFields(Collections.emptyList());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.update("e1", dto));
            assertTrue(ex.getMessage().contains("不允许删除字段"));
        }

        @Test
        @DisplayName("已发布实体 - 允许添加新字段")
        void updateEntity_published_canAddFields() {
            EntityDefinition existing = new EntityDefinition();
            existing.setId("e1");
            existing.setEntityCode("expense");
            existing.setStatus(EntityDefinition.Status.PUBLISHED);

            EntityField oldField = new EntityField();
            oldField.setId("f1");
            oldField.setFieldCode("amount");
            oldField.setIsSystem(false);
            oldField.setIsPublished(true);

            when(entityMapper.selectById("e1")).thenReturn(existing);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(oldField));
            when(dynamicTableService.syncEntityTableStructure(any())).thenReturn(Collections.emptyList());

            EntityFieldDTO oldFieldDTO = new EntityFieldDTO();
            oldFieldDTO.setFieldCode("amount");
            oldFieldDTO.setIsSystem(false);
            oldFieldDTO.setFieldName("金额");

            EntityFieldDTO newFieldDTO = new EntityFieldDTO();
            newFieldDTO.setFieldCode("remark");
            newFieldDTO.setFieldName("备注");
            newFieldDTO.setFieldType(EntityField.FieldType.STRING);
            newFieldDTO.setIsSystem(false);

            dto.setFields(List.of(oldFieldDTO, newFieldDTO));

            EntityDefinitionDTO result = service.update("e1", dto);

            assertNotNull(result);
            verify(fieldMapper, never).deleteById(anyString());
            // 已发布实体更新后自动同步表结构
            verify(dynamicTableService).syncEntityTableStructure(any());
        }

        @Test
        @DisplayName("系统字段 - 只允许更新名称/必填/默认值/选项/排序，不允许改编码和类型")
        void updateEntity_systemField_limitedUpdate() {
            EntityDefinition existing = new EntityDefinition();
            existing.setId("e1");
            existing.setEntityCode("expense");
            existing.setStatus(EntityDefinition.Status.DRAFT);

            EntityField systemField = new EntityField();
            systemField.setId("sys1");
            systemField.setFieldCode("name");
            systemField.setFieldName("数据名称");
            systemField.setIsSystem(true);
            systemField.setFieldType(EntityField.FieldType.STRING);

            when(entityMapper.selectById("e1")).thenReturn(existing);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(systemField));

            EntityFieldDTO systemFieldDTO = new EntityFieldDTO();
            systemFieldDTO.setFieldCode("name");
            systemFieldDTO.setFieldName("标题");
            systemFieldDTO.setIsRequired(true);
            systemFieldDTO.setIsSystem(true);
            systemFieldDTO.setSortOrder(5);

            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setFields(List.of(systemFieldDTO));

            service.update("e1", dto);

            verify(fieldMapper).updateById(argThat(f ->
                    "标题".equals(f.getFieldName()) && "name".equals(f.getFieldCode()) &&
                    Boolean.TRUE.equals(f.getIsRequired()) && f.getSortOrder() == 5));
        }

        @Test
        @DisplayName("更新实体 - 实体不存在时抛异常")
        void updateEntity_notFound_throwsException() {
            when(entityMapper.selectById("nonexistent")).thenReturn(null);

            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setFields(Collections.emptyList());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.update("nonexistent", dto));
            assertTrue(ex.getMessage().contains("实体不存在"));
        }
    }

    // ==================== 发布实体 ====================

    @Nested
    @DisplayName("发布实体")
    class PublishEntity {

        @Test
        @DisplayName("首次发布 - 状态从 DRAFT 变 PUBLISHED，建表，记录版本为 CREATE")
        void publish_firstTime() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setEntityName("费用报销");
            entity.setStatus(EntityDefinition.Status.DRAFT);

            EntityField field = new EntityField();
            field.setId("f1");
            field.setFieldCode("amount");
            field.setIsSystem(false);
            field.setIsPublished(false);

            when(entityMapper.selectById("e1")).thenReturn(entity);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(field));
            when(dynamicTableService.syncEntityTableStructure(entity))
                    .thenReturn(List.of("CREATE TABLE entity_data_expense (...)"));

            EntityDefinitionDTO result = service.publish("e1", "user1", "张三");

            assertEquals(EntityDefinition.Status.PUBLISHED, result.getStatus());
            verify(dynamicTableService).syncEntityTableStructure(entity);
            verify(publishHistoryService).createVersion(
                    eq(entity), anyList(), anyString(),
                    eq(EntityPublishHistory.PublishType.CREATE),
                    anyString(), eq("user1"), eq("张三"));
            verify(fieldMapper).updateById(argThat(f -> Boolean.TRUE.equals(f.getIsPublished())));
        }

        @Test
        @DisplayName("二次发布 - 状态为 ALTER，同步表结构，未发布字段标记已发布")
        void publish_secondTime_alter() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setEntityName("费用报销");
            entity.setStatus(EntityDefinition.Status.PUBLISHED);

            EntityField oldField = new EntityField();
            oldField.setId("f1");
            oldField.setFieldCode("amount");
            oldField.setIsSystem(false);
            oldField.setIsPublished(true);

            EntityField newField = new EntityField();
            newField.setId("f2");
            newField.setFieldCode("remark");
            newField.setIsSystem(false);
            newField.setIsPublished(false);

            when(entityMapper.selectById("e1")).thenReturn(entity);
            when(fieldMapper.findByEntityId("e1")).thenReturn(List.of(oldField, newField));
            when(dynamicTableService.syncEntityTableStructure(entity))
                    .thenReturn(List.of("ALTER TABLE entity_data_expense ADD COLUMN remark VARCHAR(500)"));

            service.publish("e1", "user1", "张三");

            verify(publishHistoryService).createVersion(
                    eq(entity), anyList(), anyString(),
                    eq(EntityPublishHistory.PublishType.ALTER),
                    anyString(), eq("user1"), eq("张三"));
            // newField 被标记已发布，oldField 未被更新（已经是 published）
            verify(fieldMapper).updateById(argThat(f -> "f2".equals(f.getId()) && Boolean.TRUE.equals(f.getIsPublished())));
            verify(fieldMapper, never()).updateById(argThat(f -> "f1".equals(f.getId())));
        }

        @Test
        @DisplayName("发布实体 - 实体不存在时抛异常")
        void publish_notFound_throwsException() {
            when(entityMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.publish("nonexistent", "u1", "n"));
            assertTrue(ex.getMessage().contains("实体不存在"));
        }
    }

    // ==================== 绑定流程 ====================

    @Nested
    @DisplayName("绑定流程")
    class BindProcess {

        @Test
        @DisplayName("首次绑定流程 - 无旧流程，直接绑定")
        void bindProcess_firstTime() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setProcessDefinitionId(null);

            when(entityMapper.selectById("e1")).thenReturn(entity);

            service.bindProcess("e1", "p1");

            verify(entityMapper).updateById(argThat(e ->
                    "p1".equals(e.getProcessDefinitionId()) && Boolean.TRUE.equals(e.getEnableProcess())));
        }

        @Test
        @DisplayName("切换流程 - 无数据时允许切换")
        void bindProcess_switchNoData() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setProcessDefinitionId("p1");

            when(entityMapper.selectById("e1")).thenReturn(entity);
            when(dynamicTableService.tableExists("expense")).thenReturn(true);
            when(dynamicTableService.getTableName("expense")).thenReturn("entity_data_expense");
            when(entityDataDynamicMapper.count("entity_data_expense")).thenReturn(0L);

            service.bindProcess("e1", "p2");

            verify(entityMapper).updateById(argThat(e -> "p2".equals(e.getProcessDefinitionId())));
        }

        @Test
        @DisplayName("切换流程 - 有数据时阻止切换并抛异常")
        void bindProcess_switchWithData_throwsException() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setProcessDefinitionId("p1");

            when(entityMapper.selectById("e1")).thenReturn(entity);
            when(dynamicTableService.tableExists("expense")).thenReturn(true);
            when(dynamicTableService.getTableName("expense")).thenReturn("entity_data_expense");
            when(entityDataDynamicMapper.count("entity_data_expense")).thenReturn(5L);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.bindProcess("e1", "p2"));
            assertTrue(ex.getMessage().contains("5") && ex.getMessage().contains("无法切换"));
        }

        @Test
        @DisplayName("绑定流程 - 实体不存在时抛异常")
        void bindProcess_notFound_throwsException() {
            when(entityMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.bindProcess("nonexistent", "p1"));
            assertTrue(ex.getMessage().contains("实体不存在"));
        }
    }

    // ==================== 删除实体 ====================

    @Nested
    @DisplayName("删除实体")
    class DeleteEntity {

        @Test
        @DisplayName("删除实体 - 级联删除关系和字段")
        void deleteEntity_cascadeDelete() {
            service.delete("e1");

            verify(relationMapper).deleteByParentEntityId("e1");
            verify(fieldMapper).deleteByEntityId("e1");
            verify(entityMapper).deleteById("e1");
        }
    }

    // ==================== 查询实体 ====================

    @Nested
    @DisplayName("查询实体")
    class QueryEntity {

        @Test
        @DisplayName("根据ID查询 - 实体不存在时抛异常")
        void findById_notFound_throwsException() {
            when(entityMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.findById("nonexistent"));
            assertTrue(ex.getMessage().contains("实体不存在"));
        }

        @Test
        @DisplayName("根据编码查询 - 实体不存在时抛异常")
        void findByCode_notFound_throwsException() {
            when(entityMapper.findByEntityCode("nonexistent")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.findByCode("nonexistent"));
            assertTrue(ex.getMessage().contains("实体不存在"));
        }

        @Test
        @DisplayName("根据流程定义ID查询 - 未绑定实体时抛异常")
        void findByProcessId_notFound_throwsException() {
            when(entityMapper.findByProcessDefinitionId("p1")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.findByProcessDefinitionId("p1"));
            assertTrue(ex.getMessage().contains("未绑定实体"));
        }
    }
}
