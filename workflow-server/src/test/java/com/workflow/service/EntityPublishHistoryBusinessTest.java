package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.mapper.EntityPublishHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体发布历史服务业务测试
 * 覆盖版本创建（版本号递增、快照序列化）、版本查询、版本比较
 */
class EntityPublishHistoryBusinessTest {

    private EntityPublishHistoryMapper historyMapper;
    private ObjectMapper objectMapper;
    private EntityPublishHistoryService service;

    @BeforeEach
    void setUp() {
        historyMapper = mock(EntityPublishHistoryMapper.class);
        objectMapper = new ObjectMapper();
        service = new EntityPublishHistoryService(historyMapper, objectMapper);
    }

    @Nested
    @DisplayName("创建版本")
    class CreateVersion {

        @Test
        @DisplayName("首次发布 - 版本号=1，publishType=CREATE，versionDescription=首次发布")
        void createVersion_firstVersion() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");
            entity.setEntityName("费用");

            EntityField field = new EntityField();
            field.setFieldCode("amount");
            field.setFieldName("金额");

            when(historyMapper.getLatestVersion("e1")).thenReturn(null);

            EntityPublishHistory result = service.createVersion(
                    entity, List.of(field), "CREATE TABLE ...",
                    EntityPublishHistory.PublishType.CREATE, null, "u1", "张三");

            assertNotNull(result);
            assertEquals(1, result.getVersion());
            assertEquals(EntityPublishHistory.PublishType.CREATE, result.getPublishType());
            assertEquals(EntityPublishHistory.Status.ACTIVE, result.getStatus());
            assertEquals("首次发布", result.getVersionDescription());
            assertEquals("u1", result.getPublishedBy());
            assertEquals("张三", result.getPublishedByName());
            assertNotNull(result.getFieldsSnapshot());
            assertTrue(result.getFieldsSnapshot().contains("amount"));
            verify(historyMapper).insert(any(EntityPublishHistory.class));
        }

        @Test
        @DisplayName("二次发布 - 版本号递增，publishType=ALTER，versionDescription 含变更描述")
        void createVersion_incrementVersion() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");
            entity.setEntityCode("expense");

            when(historyMapper.getLatestVersion("e1")).thenReturn(3);

            EntityPublishHistory result = service.createVersion(
                    entity, List.of(), "ALTER TABLE ...",
                    EntityPublishHistory.PublishType.ALTER, "新增 2 个字段", "u1", "张三");

            assertEquals(4, result.getVersion());
            assertEquals(EntityPublishHistory.PublishType.ALTER, result.getPublishType());
            assertEquals("新增 2 个字段", result.getVersionDescription());
        }

        @Test
        @DisplayName("ALTER 发布无变更描述 - 默认使用'字段变更'")
        void createVersion_alterNoChangesDesc() {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("e1");

            when(historyMapper.getLatestVersion("e1")).thenReturn(1);

            EntityPublishHistory result = service.createVersion(
                    entity, List.of(), "ALTER TABLE ...",
                    EntityPublishHistory.PublishType.ALTER, null, "u1", "n");

            assertEquals("字段变更", result.getVersionDescription());
        }
    }

    @Nested
    @DisplayName("版本查询")
    class VersionQuery {

        @Test
        @DisplayName("获取版本历史列表")
        void getVersionHistory() {
            EntityPublishHistory h1 = new EntityPublishHistory();
            h1.setId("h1");
            h1.setEntityId("e1");
            h1.setVersion(1);
            h1.setFieldsSnapshot("[]");

            when(historyMapper.findByEntityId("e1")).thenReturn(List.of(h1));

            var result = service.getVersionHistory("e1");

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getVersion());
        }

        @Test
        @DisplayName("获取最新版本")
        void getLatestVersion() {
            EntityPublishHistory latest = new EntityPublishHistory();
            latest.setId("h2");
            latest.setEntityId("e1");
            latest.setVersion(2);
            latest.setFieldsSnapshot("[]");

            when(historyMapper.findLatestByEntityId("e1")).thenReturn(latest);

            var result = service.getLatestVersion("e1");

            assertNotNull(result);
            assertEquals(2, result.getVersion());
        }

        @Test
        @DisplayName("获取最新版本 - 无发布历史时返回 null")
        void getLatestVersion_noHistory() {
            when(historyMapper.findLatestByEntityId("e1")).thenReturn(null);

            var result = service.getLatestVersion("e1");

            assertNull(result);
        }

        @Test
        @DisplayName("获取版本详情 - 反序列化 fieldsSnapshot 为字段列表")
        void getVersionDetail_parseFields() throws Exception {
            EntityField field = new EntityField();
            field.setFieldCode("amount");
            field.setFieldName("金额");

            EntityPublishHistory history = new EntityPublishHistory();
            history.setId("h1");
            history.setFieldsSnapshot(objectMapper.writeValueAsString(List.of(field)));

            when(historyMapper.selectById("h1")).thenReturn(history);

            var result = service.getVersionDetail("h1");

            assertNotNull(result);
            assertEquals(1, result.getFields().size());
            assertEquals("amount", result.getFields().get(0).getFieldCode());
        }

        @Test
        @DisplayName("获取版本详情 - fieldsSnapshot 反序列化失败时降级为空列表")
        void getVersionDetail_invalidSnapshot() {
            EntityPublishHistory history = new EntityPublishHistory();
            history.setId("h1");
            history.setFieldsSnapshot("invalid json {{{");

            when(historyMapper.selectById("h1")).thenReturn(history);

            var result = service.getVersionDetail("h1");

            assertNotNull(result);
            assertTrue(result.getFields().isEmpty());
        }
    }

    @Nested
    @DisplayName("版本比较")
    class CompareVersions {

        @Test
        @DisplayName("比较两个版本 - 返回拼接的版本差异描述")
        void compareVersions_success() {
            EntityPublishHistory v1 = new EntityPublishHistory();
            v1.setVersion(1);
            v1.setChangesDescription("首次发布");

            EntityPublishHistory v2 = new EntityPublishHistory();
            v2.setVersion(2);
            v2.setChangesDescription("新增字段");

            when(historyMapper.selectById("h1")).thenReturn(v1);
            when(historyMapper.selectById("h2")).thenReturn(v2);

            String result = service.compareVersions("h1", "h2");

            assertNotNull(result);
            assertTrue(result.contains("1"));
            assertTrue(result.contains("2"));
        }

        @Test
        @DisplayName("比较两个版本 - 版本不存在返回提示")
        void compareVersions_notFound() {
            when(historyMapper.selectById("h1")).thenReturn(null);
            when(historyMapper.selectById("h2")).thenReturn(null);

            String result = service.compareVersions("h1", "h2");

            assertEquals("版本不存在", result);
        }
    }
}
