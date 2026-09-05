package com.workflow.service;

import com.workflow.entity.form.infrastructure.persistence.mapper.FormConfigMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.FormFieldConfigMapper;

import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.application.ProcessDefinitionService;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.definition.api.response.ProcessDefinitionDTO;
import com.workflow.process.definition.api.response.ProcessVersionHistoryDTO;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.application.ProcessDefinitionNodeSyncService;
import com.workflow.process.definition.application.ProcessDefinitionPreflightService;
import com.workflow.core.error.RevisionConflictException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流程定义服务单元测试。
 *
 * <p>被测对象：{@link ProcessDefinitionService}，覆盖流程定义的增删改查、版本历史查询、停用等核心场景。
 */
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionServiceTest {

    @Mock
    private ProcessDefinitionConfigMapper processMapper;

    @Mock
    private ProcessVersionHistoryMapper versionHistoryMapper;

    @Mock
    private NodeConfigMapper nodeMapper;

    @Mock
    private AssigneeConfigMapper assigneeMapper;

    @Mock
    private FormConfigMapper formMapper;

    @Mock
    private FormFieldConfigMapper fieldMapper;

    @Mock
    private RepositoryService activitiRepositoryService;

    @Mock
    private ProcessDefinitionNodeSyncService nodeSyncService;

    @Mock
    private ProcessDefinitionPreflightService preflightService;

    @Mock
    private EntityCodeCatalogPort entityCodeCatalogPort;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProcessDefinitionService processService;

    private ProcessDefinitionConfig testProcess;

    @BeforeEach
    void setUp() {
        testProcess = new ProcessDefinitionConfig();
        testProcess.setId("1");
        testProcess.setProcessKey("leave_process");
        testProcess.setProcessName("请假流程");
        testProcess.setDescription("员工请假审批流程");
        testProcess.setVersion(1);
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.DRAFT);
        testProcess.setBpmnXml("<bpmn:definitions>...</bpmn:definitions>");
        testProcess.setDraftRevision(1L);
        testProcess.setPublishedRevision(0L);
    }

    /**
     * 测试查询全部活跃流程定义：验证返回结果非空、数量正确并按预期调用 Mapper。
     */
    @Test
    void testFindAll() {
        when(processMapper.findAllActive()).thenReturn(Arrays.asList(testProcess));

        List<ProcessDefinitionDTO> result = processService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("leave_process", result.get(0).getProcessKey());
        verify(processMapper, times(1)).findAllActive();
    }

    /**
     * 测试按状态查询流程定义：验证能正确筛选出已发布状态的流程。
     */
    @Test
    void testFindByStatus() {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processMapper.findByStatus("PUBLISHED")).thenReturn(Arrays.asList(testProcess));

        List<ProcessDefinitionDTO> result = processService.findByStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ProcessDefinitionConfig.ProcessStatus.PUBLISHED, result.get(0).getStatus());
    }

    /**
     * 测试按 ID 查询流程定义：验证返回的 DTO 关键字段与预期一致。
     */
    @Test
    void testFindById() {
        when(processMapper.selectById("1")).thenReturn(testProcess);

        ProcessDefinitionDTO result = processService.findById("1");

        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("leave_process", result.getProcessKey());
    }

    /**
     * 测试按 ID 查询不存在流程：验证抛出 RuntimeException 且消息包含对应 ID。
     */
    @Test
    void testFindByIdNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findById("999");
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }

    /**
     * 测试按流程 key 查询流程定义：验证返回结果的 processKey 与预期一致。
     */
    @Test
    void testFindByProcessKey() {
        when(processMapper.findByProcessKey("leave_process")).thenReturn(Optional.of(testProcess));

        ProcessDefinitionDTO result = processService.findByProcessKey("leave_process");

        assertNotNull(result);
        assertEquals("leave_process", result.getProcessKey());
    }

    /**
     * 测试按不存在的 key 查询流程：验证抛出 RuntimeException 且消息包含对应 key。
     */
    @Test
    void testFindByProcessKeyNotFound() {
        when(processMapper.findByProcessKey("not_exist")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findByProcessKey("not_exist");
        });

        assertEquals("Process not found: not_exist", exception.getMessage());
    }

    /**
     * 测试新增流程定义：验证初始版本为 0 且状态为草稿（DRAFT）。
     */
    @Test
    void testSave() {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessKey("new_process");
        dto.setProcessName("新流程");

        when(processMapper.insert(any(ProcessDefinitionConfig.class))).thenReturn(1);

        ProcessDefinitionDTO result = processService.save(dto);

        assertNotNull(result);
        assertEquals("new_process", result.getProcessKey());
        assertEquals(0, result.getVersion());
        assertEquals(ProcessDefinitionConfig.ProcessStatus.DRAFT, result.getStatus());
        assertEquals(1L, result.getRevision());
        assertEquals(0L, result.getPublishedRevision());
        assertTrue(result.getHasUnpublishedChanges());
        assertNotNull(result.getDraftHash());
        assertEquals(64, result.getDraftHash().length());
    }

    /**
     * 测试更新流程定义：验证更新成功后调用了 selectById、updateById，并触发节点绑定同步。
     */
    @Test
    void testUpdate() {
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("更新后的流程名");
        dto.setDescription("更新后的描述");
        dto.setBpmnXml("<bpmn:definitions>updated</bpmn:definitions>");
        dto.setExpectedRevision(1L);

        when(processMapper.selectById("1")).thenReturn(testProcess);
        when(processMapper.updateDraftCas(
                anyString(), anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);

        ProcessDefinitionDTO result = processService.update("1", dto);

        assertNotNull(result);
        assertEquals(2L, result.getRevision());
        assertTrue(result.getHasUnpublishedChanges());
        verify(processMapper, times(1)).selectById("1");
        verify(processMapper, times(1)).updateDraftCas(
                eq("1"), eq(1L), eq("更新后的流程名"), eq("更新后的描述"),
                isNull(), eq("<bpmn:definitions>updated</bpmn:definitions>"), anyString());
        verify(nodeSyncService).syncBpmnNodeBindings(eq("1"), eq("<bpmn:definitions>updated</bpmn:definitions>"));
    }

    /** 测试流程更新强制携带 expectedRevision，避免旧客户端绕过并发保护。 */
    @Test
    void testUpdateRequiresExpectedRevision() {
        when(processMapper.selectById("1")).thenReturn(testProcess);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("更新后的流程名");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> processService.update("1", dto));

        assertTrue(exception.getMessage().contains("expectedRevision"));
        verify(processMapper, never()).updateDraftCas(
                anyString(), anyLong(), any(), any(), any(), any(), anyString());
    }

    /** 测试过期修订号返回服务端最新草稿，且不会执行数据库更新。 */
    @Test
    void testUpdateRejectsStaleRevisionWithCurrentDraft() {
        testProcess.setDraftRevision(3L);
        when(processMapper.selectById("1")).thenReturn(testProcess);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setExpectedRevision(2L);

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> processService.update("1", dto));

        ProcessDefinitionDTO current = (ProcessDefinitionDTO) exception.getCurrentData();
        assertEquals(3L, current.getRevision());
        verify(processMapper, never()).updateDraftCas(
                anyString(), anyLong(), any(), any(), any(), any(), anyString());
    }

    /** 测试 CAS 更新期间发生竞态时重新读取并返回真正的服务端最新 revision。 */
    @Test
    void testUpdateTranslatesCasRaceToRevisionConflict() {
        ProcessDefinitionConfig raced = new ProcessDefinitionConfig();
        raced.setId("1");
        raced.setProcessKey("leave_process");
        raced.setProcessName("其他管理员的修改");
        raced.setBpmnXml("<bpmn:definitions>server</bpmn:definitions>");
        raced.setDraftRevision(2L);
        raced.setPublishedRevision(0L);
        when(processMapper.selectById("1")).thenReturn(testProcess, raced);
        when(processMapper.updateDraftCas(
                anyString(), anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(0);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("本地修改");
        dto.setBpmnXml("<bpmn:definitions>local</bpmn:definitions>");
        dto.setExpectedRevision(1L);

        RevisionConflictException exception = assertThrows(
                RevisionConflictException.class,
                () -> processService.update("1", dto));

        ProcessDefinitionDTO current = (ProcessDefinitionDTO) exception.getCurrentData();
        assertEquals(2L, current.getRevision());
        assertEquals("其他管理员的修改", current.getProcessName());
    }

    /** 测试相同内容的网络重试保持幂等，不递增草稿 revision。 */
    @Test
    void testUpdateSameContentIsIdempotent() {
        when(processMapper.selectById("1")).thenReturn(testProcess);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName(testProcess.getProcessName());
        dto.setDescription(testProcess.getDescription());
        dto.setCategory(testProcess.getCategory());
        dto.setBpmnXml(testProcess.getBpmnXml());
        dto.setExpectedRevision(1L);

        ProcessDefinitionDTO result = processService.update("1", dto);

        assertEquals(1L, result.getRevision());
        verify(processMapper, never()).updateDraftCas(
                anyString(), anyLong(), any(), any(), any(), any(), anyString());
    }

    /**
     * 测试更新不存在的流程定义：验证抛出 RuntimeException 且消息包含对应 ID。
     */
    @Test
    void testUpdateNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessName("更新");

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.update("999", dto);
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }

    /**
     * 测试删除流程定义：验证触发了 updateById 调用（逻辑删除方式）。
     */
    @Test
    void testDelete() {
        when(processMapper.selectByIdForUpdate("1"))
                .thenReturn(testProcess);
        when(processMapper.updateById(any(ProcessDefinitionConfig.class))).thenReturn(1);

        processService.delete("1");

        verify(processMapper, times(1)).updateById(any(ProcessDefinitionConfig.class));
    }

    /** 删除流程与实体绑定共用行锁，已绑定流程不得留下悬空实体关联。 */
    @Test
    void boundProcessCannotBeDeleted() {
        when(processMapper.selectByIdForUpdate("1"))
                .thenReturn(testProcess);
        when(entityCodeCatalogPort.findEntityCodeByProcessDefinitionId("1"))
                .thenReturn("leave_request");

        var exception = assertThrows(
                com.workflow.core.error.BusinessConflictException.class,
                () -> processService.delete("1"));

        assertEquals("PROCESS_ENTITY_BINDING_IN_USE", exception.getErrorCode());
        verify(processMapper, never()).updateById(
                any(ProcessDefinitionConfig.class));
    }

    /**
     * 测试按流程 ID 查询版本历史：验证返回的版本数量与版本号正确。
     */
    @Test
    void testFindVersionsByProcessId() {
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setId("v1");
        version.setProcessConfigId("1");
        version.setProcessKey("leave_process");
        version.setVersion(1);

        when(versionHistoryMapper.findByProcessConfigId("1")).thenReturn(Arrays.asList(version));

        List<ProcessVersionHistoryDTO> result = processService.findVersionsByProcessId("1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getVersion());
    }

    /**
     * 测试按版本 ID 查询版本历史：验证返回 DTO 的 id 正确。
     */
    @Test
    void testFindVersionById() {
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setId("v1");
        version.setProcessKey("leave_process");
        version.setVersion(1);

        when(versionHistoryMapper.selectById("v1")).thenReturn(version);

        ProcessVersionHistoryDTO result = processService.findVersionById("v1");

        assertNotNull(result);
        assertEquals("v1", result.getId());
    }

    /**
     * 测试按不存在的版本 ID 查询：验证抛出 RuntimeException 且消息包含对应 ID。
     */
    @Test
    void testFindVersionByIdNotFound() {
        when(versionHistoryMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.findVersionById("999");
        });

        assertEquals("Version not found: 999", exception.getMessage());
    }

    /**
     * 测试停用流程定义：验证停用后状态变为 DISABLED。
     */
    @Test
    void testDisable() {
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(processMapper.selectById("1")).thenReturn(testProcess);
        when(processMapper.updateById(any(ProcessDefinitionConfig.class))).thenReturn(1);

        ProcessDefinitionDTO result = processService.disable("1");

        assertNotNull(result);
        assertEquals(ProcessDefinitionConfig.ProcessStatus.DISABLED, result.getStatus());
    }

    /**
     * 测试停用不存在的流程定义：验证抛出 RuntimeException 且消息包含对应 ID。
     */
    @Test
    void testDisableNotFound() {
        when(processMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            processService.disable("999");
        });

        assertEquals("Process not found: 999", exception.getMessage());
    }
}
