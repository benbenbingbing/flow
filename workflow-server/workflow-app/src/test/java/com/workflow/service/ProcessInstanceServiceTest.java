package com.workflow.service;

import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流程实例服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessInstanceServiceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private TaskService taskService;

    @Mock
    private ProcessDefinitionConfigMapper processConfigMapper;

    @InjectMocks
    private ProcessInstanceService processInstanceService;

    @Test
    void testGetBpmnXmlByProcessKey() {
        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setProcessKey("leave_process");
        config.setBpmnXml("<bpmn>...</bpmn>");
        
        when(processConfigMapper.findByProcessKey("leave_process")).thenReturn(Optional.of(config));

        String result = processInstanceService.getBpmnXmlByProcessKey("leave_process");

        assertNotNull(result);
        assertEquals("<bpmn>...</bpmn>", result);
    }

    @Test
    void testGetBpmnXmlByProcessKeyNotFound() {
        when(processConfigMapper.findByProcessKey("not_exist")).thenReturn(Optional.empty());

        ProcessDefinitionQuery processDefQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefQuery);
        when(processDefQuery.processDefinitionKey("not_exist")).thenReturn(processDefQuery);
        when(processDefQuery.latestVersion()).thenReturn(processDefQuery);
        when(processDefQuery.singleResult()).thenReturn(null);

        String result = processInstanceService.getBpmnXmlByProcessKey("not_exist");

        assertNull(result);
    }

    @Test
    void testServiceInjected() {
        assertNotNull(processInstanceService);
        assertNotNull(runtimeService);
        assertNotNull(historyService);
        assertNotNull(repositoryService);
        assertNotNull(taskService);
        assertNotNull(processConfigMapper);
    }
}
