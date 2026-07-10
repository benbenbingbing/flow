package com.workflow.service;

import com.workflow.entity.EntityStatus;
import com.workflow.mapper.EntityStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体状态服务业务测试
 * 覆盖状态定义的增删改查、批量保存（物理删除+重插）、逻辑删除
 */
class EntityStatusBusinessTest {

    private EntityStatusMapper mapper;
    private EntityStatusService service;

    @BeforeEach
    void setUp() {
        mapper = mock(EntityStatusMapper.class);
        service = new EntityStatusService(mapper);
    }

    @Nested
    @DisplayName("批量保存状态")
    class SaveStatusList {

        @Test
        @DisplayName("批量保存 - 先物理删除旧状态，再逐条插入（ID 清空，sortOrder 按索引）")
        void saveStatusList_fullReplace() {
            EntityStatus s1 = new EntityStatus();
            s1.setStatusCode("DRAFT");
            s1.setStatusName("草稿");

            EntityStatus s2 = new EntityStatus();
            s2.setStatusCode("APPROVING");
            s2.setStatusName("审批中");
            s2.setStatusCategory("PROCESSING");

            service.saveStatusList("expense", List.of(s1, s2));

            verify(mapper).physicalDeleteByEntityCode("expense");
            verify(mapper).insert(argThat(s ->
                    "expense".equals(s.getEntityCode()) && s.getId() == null &&
                    s.getSortOrder() == 0 && s.getDeleted() != null && s.getDeleted() == 0));
            verify(mapper).insert(argThat(s ->
                    "expense".equals(s.getEntityCode()) && s.getSortOrder() == 1));
        }

        @Test
        @DisplayName("批量保存 - 清空 createdAt/updatedAt 避免覆盖数据库默认值")
        void saveStatusList_clearTimestamps() {
            EntityStatus status = new EntityStatus();
            status.setCreatedAt(java.time.LocalDateTime.now());
            status.setUpdatedAt(java.time.LocalDateTime.now());

            service.saveStatusList("expense", List.of(status));

            verify(mapper).insert(argThat(s -> s.getCreatedAt() == null && s.getUpdatedAt() == null));
        }

        @Test
        @DisplayName("批量保存 - 空列表只删不插")
        void saveStatusList_emptyList() {
            service.saveStatusList("expense", Collections.emptyList());

            verify(mapper).physicalDeleteByEntityCode("expense");
            verify(mapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("单条保存状态")
    class SaveStatus {

        @Test
        @DisplayName("新建 - ID 为空走 insert")
        void saveStatus_insert() {
            EntityStatus status = new EntityStatus();
            status.setStatusCode("DRAFT");

            service.saveStatus(status);

            verify(mapper).insert(status);
            verify(mapper, never()).updateById(any());
        }

        @Test
        @DisplayName("更新 - ID 不为空走 updateById")
        void saveStatus_update() {
            EntityStatus status = new EntityStatus();
            status.setId("s1");
            status.setStatusCode("DRAFT");

            service.saveStatus(status);

            verify(mapper).updateById(status);
            verify(mapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("删除状态")
    class DeleteStatus {

        @Test
        @DisplayName("逻辑删除 - 设 deleted=1 后 updateById")
        void deleteStatus_logicalDelete() {
            EntityStatus status = new EntityStatus();
            status.setId("s1");
            when(mapper.selectById("s1")).thenReturn(status);

            service.deleteStatus("s1");

            assertEquals(1, status.getDeleted());
            verify(mapper).updateById(status);
        }

        @Test
        @DisplayName("删除不存在的状态 - 静默返回不抛异常")
        void deleteStatus_notFound_silentReturn() {
            when(mapper.selectById("nonexistent")).thenReturn(null);

            assertDoesNotThrow(() -> service.deleteStatus("nonexistent"));
            verify(mapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("查询状态")
    class QueryStatus {

        @Test
        @DisplayName("按分类查询 - 正确传递 entityCode 和 category")
        void findByCategory() {
            service.findByCategory("expense", "PROCESSING");

            verify(mapper).findByCategory("expense", "PROCESSING");
        }

        @Test
        @DisplayName("按实体编码查询")
        void findByEntityCode() {
            service.findByEntityCode("expense");

            verify(mapper).findByEntityCode("expense");
        }
    }
}
