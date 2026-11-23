package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.system.*;
import com.workflow.port.process.ProcessAssigneeResolverPort;
import com.workflow.process.assignment.application.*;
import com.workflow.process.assignment.extension.GroupMembersByTagPersonResolver;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.engine.*;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 发布后的真实 Flowable 链路：普通审批只有筛选后候选，会签在节点进入时读取最新标签。 */
class GroupMemberTagsFlowableIntegrationTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void publishedGroupTagAssignmentUsesOneResolverAndNeverLeavesWholeGroupAccess(boolean multi) {
        ObjectMapper json = new ObjectMapper();
        var directory = mock(SysUserGroupTagPort.class);
        AtomicReference<List<String>> users = new AtomicReference<>(List.of("old-user"));
        when(directory.resolveMembersByTags(any())).thenAnswer(call -> users.get());
        var resolver = new GroupMembersByTagPersonResolver(directory);
        var membership = mock(ProcessCandidateMemberPort.class);
        when(membership.users(any())).thenAnswer(call -> call.getArgument(0));
        var runtime = new PersonResolverRuntimeService(List.of(resolver),membership,code->true);
        var sanitizer = new ProcessBpmnPublishSanitizer(json);
        ReflectionTestUtils.setField(sanitizer,"personResolverRuntimeService",runtime);
        ReflectionTestUtils.setField(sanitizer,"personResolverConfigurationValidators",List.of(resolver));
        Map<Object,Object> beans = new HashMap<>();
        var config=(ProcessEngineConfigurationImpl)ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:group_tag_engine_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);config.setBeans(beans);
        ProcessEngine engine=config.buildProcessEngine();
        try {
            var versions=mock(ProcessVersionHistoryMapper.class);
            var reference=new NodeAssignmentReferenceResolver(json);
            var overrides=new NextApproverOverrideStore(engine.getRuntimeService(),engine.getRepositoryService());
            var userMapper=mock(com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper.class);
            when(userMapper.selectByUsername(any())).thenAnswer(call->{
                var user=new com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser();
                user.setId(call.getArgument(0)); user.setUsername(call.getArgument(0)); user.setStatus("0"); user.setDeleted(0); return user;
            });
            var assignment=new MultiInstanceAssignmentResolver(null,null,null,null,userMapper,runtime);
            var collection=new RelativeOrgPositionCollectionHandler(engine.getRepositoryService(),versions,json,assignment,reference,overrides,
                    mock(EmptyAssigneePolicyResolver.class),new AssigneeResolutionService(runtime),mock(AssigneeIncidentRecorder.class));
            beans.put("relativeOrgPositionCollectionHandler",collection);
            var listener=new PersonResolverTaskAssignmentListener(versions,engine.getRepositoryService(),engine.getRuntimeService(),engine.getTaskService(),runtime,json);
            ReflectionTestUtils.setField(listener,"nextApproverOverrideStore",overrides);
            engine.getRuntimeService().addEventListener(listener);
            String published=sanitizer.sanitize(bpmn(multi),"group_tag_process");
            if(multi) assertTrue(published.contains("${relativeOrgPositionCollectionHandler}"));
            engine.getRepositoryService().createDeployment().addString("tags.bpmn20.xml",published).deploy();
            var instance=engine.getRuntimeService().startProcessInstanceByKey("group_tag_process",Map.of("reviewers",List.of("outsider")));
            users.set(List.of("alice","bob"));
            var source=engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
            engine.getTaskService().complete(source.getId());
            var tasks=engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).list();
            assertEquals(multi?2:1,tasks.size());
            if(multi) assertEquals(Set.of("alice","bob"),new HashSet<>(tasks.stream().map(task->task.getAssignee()).toList()));
            else {
                var links=engine.getTaskService().getIdentityLinksForTask(tasks.get(0).getId());
                assertEquals(Set.of("alice","bob"),new HashSet<>(links.stream().filter(link->"candidate".equals(link.getType())).map(link->link.getUserId()).toList()));
                assertTrue(links.stream().noneMatch(link->link.getGroupId()!=null));
            }
            // 已经进入节点后改变目录不重写活动任务，候选名单/当轮会签集合保持稳定。
            users.set(List.of("changed-later"));
            assertEquals(multi?2:1,engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).count());
        } finally { engine.close(); }
    }
    private String bpmn(boolean multi) {
        String rule="{\"assignmentConfigVersion\":2,\"assigneeType\":\"interface\",\"resolverCode\":\"groupMembersByTag\",\"assignmentMode\":\"CANDIDATE\",\"extraParams\":{\"groups\":[{\"groupCode\":\"TECH\",\"tagIds\":[\"backend\"]}]}}";
        String loop=multi?"<multiInstanceLoopCharacteristics isSequential=\"false\" flowable:collection=\"${reviewers}\" flowable:elementVariable=\"reviewer\"/>":"";
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
              <process id="draft" isExecutable="true">
                <startEvent id="start"/><userTask id="source" flowable:assignee="owner"/>
                <userTask id="target" %s><extensionElements><flowable:properties><flowable:property name="assigneeConfig" value="%s"/></flowable:properties></extensionElements>%s</userTask>
                <endEvent id="end"/><sequenceFlow id="f1" sourceRef="start" targetRef="source"/><sequenceFlow id="f2" sourceRef="source" targetRef="target"/><sequenceFlow id="f3" sourceRef="target" targetRef="end"/>
              </process>
            </definitions>
            """.formatted(multi?"flowable:assignee=\"${reviewer}\"":"flowable:candidateGroups=\"TECH\"",rule.replace("\"","&quot;"),loop);
    }
}
