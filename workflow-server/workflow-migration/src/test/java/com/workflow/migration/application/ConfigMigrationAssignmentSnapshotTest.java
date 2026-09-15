package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.ProcessNodeApprovalMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.workflow.migration.application.ConfigMigrationAssignmentSupportTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 对真实发布快照生成路径重现 admin 登录名与主键不同的问题。 */
class ConfigMigrationAssignmentSnapshotTest {
    @Test
    void slaUserReferencesAlsoDeferMissingAccountsToTarget() {
        ConfigMigrationAssetService service = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "userMapper", mock(SysUserMapper.class));
        List<Map<String, Object>> dependencies = new java.util.ArrayList<>();
        String document = ReflectionTestUtils.invokeMethod(service, "portableSlaUserReferences",
                "{\"userIds\":[\"future-user\"]}", dependencies, "SLA升级接收人");
        assertTrue(document.contains("wf-user://future-user"));
        assertFalse(document.contains("missing/"));
        assertEquals(true, dependencies.get(0).get("targetOnly"));
        assertEquals("future-user", dependencies.get(0).get("key"));
    }

    @Test
    void newlyPublishedSnapshotUsesLoginAndSeparatesGroupAndRole() {
        ConfigMigrationAssetService service = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().findAndRegisterModules());
        NodeConfigMapper nodes = mock(NodeConfigMapper.class);
        AssigneeConfigMapper assignees = mock(AssigneeConfigMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service, "nodeConfigMapper", nodes);
        ReflectionTestUtils.setField(service, "assigneeConfigMapper", assignees);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "groupMapper", mock(
                com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper.class));
        ReflectionTestUtils.setField(service, "roleMapper", mock(
                com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper.class));
        ReflectionTestUtils.setField(service, "organizationMapper", mock(SysOrganizationMapper.class));
        ReflectionTestUtils.setField(service, "nodeFormMapper", mock(ProcessNodeFormMapper.class));
        ReflectionTestUtils.setField(service, "nodeApprovalMapper", mock(ProcessNodeApprovalMapper.class));
        ReflectionTestUtils.setField(service, "flowActionMapper", mock(FlowActionMapper.class));
        ReflectionTestUtils.setField(service, "statusMappingMapper", mock(EntityFlowStatusMappingMapper.class));
        SysUser admin = new SysUser();
        admin.setId("1");
        admin.setUsername("admin");
        when(users.selectByUsername("admin")).thenReturn(admin);
        when(users.selectById("1")).thenReturn(admin);
        NodeConfig node = new NodeConfig();
        node.setId("db-node");
        node.setNodeId("review");
        node.setNodeName("审核");
        node.setConfigJson(ConfigMigrationAssignmentSupport.write(Map.of("assigneeConfig", Map.of(
                "assigneeType", "user", "assigneeValue", "1", "candidateUsers", "missing-login"))));
        when(nodes.findByProcessConfigId("process-id")).thenReturn(List.of(node));
        when(assignees.findByNodeConfigId("db-node")).thenReturn(List.of(
                assignee("USER", "admin"), assignee("ROLE", "finance"), assignee("ROLE", "ROLE_manager")));
        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId("process-id");
        process.setProcessKey("approval");
        process.setProcessName("审批");
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setBpmnXml(bpmn("<bpmn:userTask id=\"review\" name=\"审核\" flowable:assignee=\"1\""
                + " flowable:candidateUsers=\"missing-login\" flowable:candidateGroups=\"finance,ROLE_manager\"/>"));
        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(service, "buildProcessSnapshot", process, history);
        String rendered = ConfigMigrationAssignmentSupport.write(snapshot);
        assertFalse(rendered.contains("missing/admin"));
        assertFalse(rendered.contains("wf-user://"));
        assertTrue(String.valueOf(snapshot.get("bpmnXml")).contains("assignee=\"admin\""));
        var deps = ConfigMigrationAssignmentSupport.maps(snapshot.get("dependencies"));
        for (String ref : List.of("USER:admin", "USER:missing-login", "GROUP:finance", "ROLE:manager")) {
            assertTrue(deps.stream().anyMatch(value -> ref.equals(value.get("type") + ":" + value.get("key"))
                    && Boolean.TRUE.equals(value.get("targetOnly"))));
        }
        var savedNodes = ConfigMigrationAssignmentSupport.maps(snapshot.get("nodes"));
        String nodeJson = String.valueOf(savedNodes.get(0).get("configJson"));
        assertTrue(nodeJson.contains("admin") && nodeJson.contains("missing-login"));
        var savedPeople = ConfigMigrationAssignmentSupport.maps(savedNodes.get(0).get("assignees"));
        assertEquals(List.of("USER", "GROUP", "ROLE"), savedPeople.stream().map(value -> value.get("assigneeType")).toList());
    }

    private AssigneeConfig assignee(String type, String key) {
        AssigneeConfig value = new AssigneeConfig();
        value.setAssigneeType(AssigneeConfig.AssigneeType.valueOf(type));
        value.setAssigneeValue(key);
        return value;
    }
}
