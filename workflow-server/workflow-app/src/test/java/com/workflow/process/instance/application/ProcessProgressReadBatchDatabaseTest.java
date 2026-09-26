package com.workflow.process.instance.application;

import com.workflow.process.instance.application.ProcessProgressReadBatch;

import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import java.util.*;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 用真实 Flowable 历史记录校验批量变量范围和评论类型，不仅验证 Mock 查询次数。 */
class ProcessProgressReadBatchDatabaseTest {
    @Test
    void realMultiInstanceHistoryKeepsTaskExecutionAndRootScopesSeparate() {
        var engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:progress_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setAsyncExecutorActivate(false).buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addString("progress.bpmn20.xml", """
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                      <process id="progress" isExecutable="true"><startEvent id="start"/>
                        <sequenceFlow id="a" sourceRef="start" targetRef="review"/>
                        <userTask id="review" name="审批" flowable:assignee="${reviewer}">
                          <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="reviewers" flowable:elementVariable="reviewer"/>
                        </userTask>
                        <sequenceFlow id="b" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                      </process>
                    </definitions>
                    """).deploy();
            var instance = engine.getRuntimeService().startProcessInstanceByKey("progress",
                    Map.of("reviewers", List.of("alice", "bob"), "actionLabel", "根操作名"));
            var tasks = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).list();
            var first = tasks.get(0);
            var second = tasks.get(1);
            engine.getTaskService().setVariablesLocal(first.getId(), Map.of("action", "APPROVE", "actionLabel", "任务操作名"));
            engine.getRuntimeService().setVariableLocal(second.getExecutionId(), "actionLabel", "执行局部名");
            engine.getTaskService().addComment(first.getId(), instance.getId(), "comment", "转办给: alice");
            engine.getTaskService().addComment(first.getId(), instance.getId(), "event", "此类型不能覆盖任务评论");
            engine.getTaskService().complete(first.getId());
            engine.getTaskService().setAssignee(second.getId(), null);
            engine.getTaskService().addCandidateGroup(second.getId(), "REVIEWERS");
            engine.getTaskService().addCandidateUser(second.getId(), "extra");
            var history = spy(engine.getHistoryService());
            var taskService = spy(engine.getTaskService());
            var groups = mock(SysGroupMapper.class);
            var memberships = mock(SysUserGroupMapper.class);
            var group = new SysGroupMapper.GroupDisplayRow();
            group.setLookupCode("REVIEWERS"); group.setId("g1"); group.setGroupName("审批组");
            when(groups.selectDisplayGroupsByCodes(List.of("REVIEWERS"))).thenReturn(List.of(group));
            var member = new com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup();
            member.setGroupId("g1"); member.setUserId("alice");
            when(memberships.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup>>any()))
                    .thenReturn(List.of(member));
            var batch = new ProcessProgressReadBatch(instance.getId(), history, taskService,
                    mock(ProcessTaskMapper.class), groups, memberships);
            assertEquals("任务操作名", batch.actionLabel(first.getId()));
            assertEquals("APPROVE", batch.taskValue(first.getId(), "action"));
            assertEquals("根操作名", batch.actionLabel(second.getId()));
            assertTrue(batch.nodeVariables(second.getId(), second.getExecutionId()).stream()
                    .anyMatch(value -> "执行局部名".equals(value.getValue())));
            assertTrue(batch.nodeVariables(first.getId(), first.getExecutionId()).stream()
                    .allMatch(value -> first.getId().equals(value.getTaskId())));
            assertEquals("转办给: alice", batch.latestComment(first.getId()));
            assertEquals(2, batch.historicTasks.size());
            assertEquals(1, batch.activeTasks.size());
            // 引擎候选关系随当前任务读取；组成员优先于直接候选用户，名字只在最后批量回填。
            var info = new com.workflow.process.instance.api.response.ProcessProgressDTO.AssigneeInfoDTO();
            batch.prepareCandidateNames(batch.activeTasks.get(0), info);
            var progress = new com.workflow.process.instance.api.response.ProcessProgressDTO();
            progress.setNodeHistory(List.of());
            progress.setNodeAssigneesMap(Map.of("review", List.of(info)));
            var users = mock(com.workflow.admin.identity.user.application.SysUserService.class);
            when(users.getDisplayNameMap(Set.of("alice"))).thenReturn(Map.of("alice", "爱丽丝(alice)"));
            batch.fillDisplayNames(progress, users);
            assertEquals("爱丽丝(alice)", info.getAssigneeName());
            verify(users).getDisplayNameMap(Set.of("alice"));
            verify(groups).selectDisplayGroupsByCodes(List.of("REVIEWERS"));
            verify(memberships).selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup>>any());
            verify(history).createHistoricVariableInstanceQuery();
            verify(taskService).getProcessInstanceComments(instance.getId(), "comment");
            verify(taskService, never()).getTaskComments(org.mockito.ArgumentMatchers.anyString());
        } finally { engine.close(); }
    }
}
