package com.workflow.entity.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.mapper.EntityPublishHistoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体发布快照服务单元测试。
 *
 * <p>被测对象为 {@link EntityPublishedSnapshotService}，验证按实体 ID/编码
 * 读取最新发布快照(含字段快照 JSON 解析)，以及无快照时的报错逻辑。</p>
 */
class EntityPublishedSnapshotServiceTest {

    /**
     * 按实体 ID 读取最新快照应解析字段快照 JSON。
     *
     * <p>场景：快照含一个 amount 字段，断言解析后快照含历史 ID、实体 ID、
     * 编码、流程定义 ID、版本与字段列表。</p>
     */
    @Test
    void getLatestByEntityIdParsesFieldsSnapshot() throws Exception {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        EntityField amount = new EntityField();
        amount.setFieldCode("amount");
        amount.setFieldName("金额");
        amount.setIsRequired(true);

        EntityPublishHistory history = history("history-1", "entity-1", "expense", 3,
                new ObjectMapper().writeValueAsString(List.of(amount)));
        when(historyMapper.findLatestByEntityId("entity-1")).thenReturn(history);

        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        EntityPublishedSnapshot snapshot = service.getLatestByEntityId("entity-1");

        assertEquals("history-1", snapshot.getHistoryId());
        assertEquals("entity-1", snapshot.getEntityId());
        assertEquals("expense", snapshot.getEntityCode());
        assertEquals("process-config-1", snapshot.getProcessDefinitionId());
        assertEquals(3, snapshot.getVersion());
        assertEquals(1, snapshot.getFields().size());
        assertEquals("amount", snapshot.getFields().get(0).getFieldCode());
        assertEquals(true, snapshot.getFields().get(0).getIsRequired());
    }

    /**
     * 按实体编码读取最新发布快照应直接返回快照信息。
     *
     * <p>场景：快照字段为空数组，断言返回的实体 ID、编码与流程定义 ID 正确。</p>
     */
    @Test
    void getLatestByEntityCodeReadsPublishedSnapshotDirectly() {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        when(historyMapper.findLatestByEntityCode("expense")).thenReturn(
                history("history-1", "entity-1", "expense", 1, "[]"));

        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        EntityPublishedSnapshot snapshot = service.getLatestByEntityCode("expense");

        assertEquals("entity-1", snapshot.getEntityId());
        assertEquals("expense", snapshot.getEntityCode());
        assertEquals("process-config-1", snapshot.getProcessDefinitionId());
    }

    /**
     * 实体无发布快照时读取应抛出异常。
     *
     * <p>场景：mapper 无记录，断言抛出 RuntimeException 且消息含"实体未发布"。</p>
     */
    @Test
    void getLatestByEntityIdFailsWhenNoSnapshotExists() {
        EntityPublishHistoryMapper historyMapper = mock(EntityPublishHistoryMapper.class);
        EntityPublishedSnapshotService service = new EntityPublishedSnapshotService(
                historyMapper, new ObjectMapper());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.getLatestByEntityId("entity-1"));

        assertEquals("实体未发布: entity-1", exception.getMessage());
    }

    /**
     * 构造测试用发布历史对象。
     *
     * @param id 历史 ID
     * @param entityId 实体 ID
     * @param entityCode 实体编码
     * @param version 版本号
     * @param fieldsSnapshot 字段快照 JSON 字符串
     * @return 已填充字段的 EntityPublishHistory 实例
     */
    private static EntityPublishHistory history(String id,
                                                String entityId,
                                                String entityCode,
                                                Integer version,
                                                String fieldsSnapshot) {
        EntityPublishHistory history = new EntityPublishHistory();
        history.setId(id);
        history.setEntityId(entityId);
        history.setEntityCode(entityCode);
        history.setEntityName("费用");
        history.setProcessDefinitionId("process-config-1");
        history.setVersion(version);
        history.setFieldsSnapshot(fieldsSnapshot);
        return history;
    }
}
