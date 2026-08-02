package com.workflow.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMemberChangeServiceTest {

    private EntityDataDynamicService entityDataService;
    private EntityMutationPort entityMutationPort;
    private ProjectMemberChangeService service;
    private AtomicInteger roleIndex;

    @BeforeEach
    void setUp() {
        entityDataService = mock(EntityDataDynamicService.class);
        entityMutationPort = mock(EntityMutationPort.class);
        roleIndex = new AtomicInteger();
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        service = new ProjectMemberChangeService(
                entityDataService,
                new ProjectEntityMutationExecutor(
                        entityMutationPort,
                        objectMapper),
                new ProjectMemberChangeRuleSupport(
                        entityDataService,
                        objectMapper),
                new ProjectMemberChangeTraceSupport(
                        new ProjectEntityMutationExecutor(
                                entityMutationPort,
                                objectMapper),
                        new ProjectMemberChangeRuleSupport(
                                entityDataService,
                                objectMapper)));
        when(entityDataService.findByCondition(
                anyString(), anyMap()))
                .thenReturn(List.of());
        when(entityMutationPort.execute(
                any(EntityMutationCommand.class)))
                .thenAnswer(invocation -> {
                    EntityMutationCommand command =
                            invocation.getArgument(0);
                    String recordId = command.recordId();
                    if (command.operationType()
                            == EntityMutationOperationType.CREATE) {
                        recordId = generatedId(
                                command.entityCode());
                    }
                    Map<String, Object> record =
                            new LinkedHashMap<>(
                                    command.payload());
                    record.put("id", recordId);
                    record.put(
                            "entityCode",
                            command.entityCode());
                    return new EntityMutationResult(
                            command.operationId(),
                            command.entityCode(),
                            recordId,
                            command.operationType(),
                            record,
                            null,
                            null,
                            true,
                            false);
                });
    }

    @Test
    void validatesJoinAndCalculatesAccessReviewRoutes() {
        EntityDataDTO request = joinRequest(
                "USER-NEW", "60");
        request.getData().put(
                "account_required_flag", true);
        request.getData().put(
                "environment_access_required_flag", true);
        request.getData().put(
                "environment_scope",
                List.of("TEST", "PROD_OPERATE"));

        when(entityDataService.findById(
                "project", "PRJ-1"))
                .thenReturn(project());
        when(entityDataService.findByCondition(
                "project_member",
                Map.of(
                        "project_id", "PRJ-1",
                        "user_id", "USER-NEW")))
                .thenReturn(List.of());
        when(entityDataService.findByCondition(
                "project_member",
                Map.of("user_id", "USER-NEW")))
                .thenReturn(List.of(member(
                        "MEM-OTHER",
                        "PRJ-OTHER",
                        "USER-NEW",
                        "ACTIVE",
                        "30")));

        Map<String, Object> result =
                service.validateChange(request);

        assertEquals(
                new BigDecimal("60"),
                result.get("requestedAllocation"));
        assertEquals(true,
                result.get("accessReviewRequired"));
        assertEquals(true,
                result.get("securityReviewRequired"));
        assertEquals(false,
                result.get("handoverRequired"));

        ArgumentCaptor<EntityMutationCommand> captor =
                ArgumentCaptor.forClass(
                        EntityMutationCommand.class);
        verify(entityMutationPort)
                .execute(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> values =
                (Map<String, Object>) captor.getValue()
                        .payload()
                        .get("data");
        assertEquals(true,
                values.get(
                        "access_review_required_flag"));
        assertEquals(true,
                values.get(
                        "security_review_required_flag"));
        assertTrue(String.valueOf(
                values.get("before_snapshot"))
                .startsWith("{"));
    }

    @Test
    void rejectsDuplicateActiveMember() {
        EntityDataDTO request =
                joinRequest("USER-1", "50");
        when(entityDataService.findById(
                "project", "PRJ-1"))
                .thenReturn(project());
        when(entityDataService.findByCondition(
                "project_member",
                Map.of(
                        "project_id", "PRJ-1",
                        "user_id", "USER-1")))
                .thenReturn(List.of(member(
                        "MEM-1",
                        "PRJ-1",
                        "USER-1",
                        "ACTIVE",
                        "50")));

        BusinessConflictException error =
                assertThrows(
                        BusinessConflictException.class,
                        () -> service.validateChange(
                                request));

        assertEquals(
                "PROJECT_MEMBER_DUPLICATE",
                error.getErrorCode());
        verify(entityMutationPort, never())
                .execute(any());
    }

    @Test
    void rejectsCrossProjectAllocationAboveOneHundred() {
        EntityDataDTO request =
                joinRequest("USER-2", "45");
        when(entityDataService.findById(
                "project", "PRJ-1"))
                .thenReturn(project());
        when(entityDataService.findByCondition(
                "project_member",
                Map.of(
                        "project_id", "PRJ-1",
                        "user_id", "USER-2")))
                .thenReturn(List.of());
        when(entityDataService.findByCondition(
                "project_member",
                Map.of("user_id", "USER-2")))
                .thenReturn(List.of(
                        member(
                                "MEM-A",
                                "PRJ-A",
                                "USER-2",
                                "ACTIVE",
                                "40"),
                        member(
                                "MEM-B",
                                "PRJ-B",
                                "USER-2",
                                "SUSPENDED",
                                "20")));

        BusinessConflictException error =
                assertThrows(
                        BusinessConflictException.class,
                        () -> service.validateChange(
                                request));

        assertEquals(
                "PROJECT_MEMBER_ALLOCATION_EXCEEDED",
                error.getErrorCode());
        assertTrue(error.getMessage()
                .contains("超过100%"));
    }

    @Test
    void rejectsLeaveWithActiveRoleWithoutHandover() {
        EntityDataDTO member = member(
                "MEM-1",
                "PRJ-1",
                "USER-1",
                "ACTIVE",
                "100");
        EntityDataDTO request =
                leaveRequest("MEM-1");
        when(entityDataService.findById(
                "project", "PRJ-1"))
                .thenReturn(project());
        when(entityDataService.findById(
                "project_member", "MEM-1"))
                .thenReturn(member);
        when(entityDataService.findByCondition(
                "project_role_assignment",
                Map.of(
                        "project_id", "PRJ-1",
                        "member_id", "MEM-1")))
                .thenReturn(List.of(role(
                        "ROLE-1",
                        "MEM-1",
                        "PROJECT_MANAGER",
                        true)));

        BusinessConflictException error =
                assertThrows(
                        BusinessConflictException.class,
                        () -> service.validateChange(
                                request));

        assertEquals(
                "PROJECT_MEMBER_HANDOVER_REQUIRED",
                error.getErrorCode());
    }

    @Test
    void appliesLeaveAndTransfersActiveRoles() {
        EntityDataDTO request =
                leaveRequest("MEM-1");
        request.getData().put(
                "handover_member_id", "MEM-2");
        request.getData().put(
                "handover_description",
                "项目管理职责、风险清单和待办事项已交接");
        request.getData().put(
                "permission_revoke_deadline",
                "2026-08-21");
        EntityDataDTO sourceMember = member(
                "MEM-1",
                "PRJ-1",
                "USER-1",
                "ACTIVE",
                "100");
        EntityDataDTO handoverMember = member(
                "MEM-2",
                "PRJ-1",
                "USER-2",
                "ACTIVE",
                "80");
        EntityDataDTO activeRole = role(
                "ROLE-1",
                "MEM-1",
                "PROJECT_MANAGER",
                true);

        when(entityDataService.findById(
                "project_member", "MEM-1"))
                .thenReturn(sourceMember);
        when(entityDataService.findById(
                "project_member", "MEM-2"))
                .thenReturn(handoverMember);
        when(entityDataService.findByCondition(
                "project_role_assignment",
                Map.of(
                        "project_id", "PRJ-1",
                        "member_id", "MEM-1")))
                .thenReturn(List.of(activeRole));

        Map<String, Object> result =
                service.applyChange(request);

        assertEquals("LEAVE",
                result.get("operation"));
        assertEquals(1,
                result.get("transferredRoleCount"));
        assertFalse((Boolean) result.get("reused"));

        ArgumentCaptor<EntityMutationCommand> captor =
                ArgumentCaptor.forClass(
                        EntityMutationCommand.class);
        verify(entityMutationPort, times(5))
                .execute(captor.capture());
        List<EntityMutationCommand> commands =
                captor.getAllValues();
        EntityMutationCommand sourceMemberUpdate =
                commands.stream()
                        .filter(command ->
                                "project_member".equals(
                                        command.entityCode())
                                && "MEM-1".equals(
                                        command.recordId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "LEFT",
                sourceMemberUpdate.payload()
                        .get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMemberData =
                (Map<String, Object>) sourceMemberUpdate
                        .payload()
                        .get("data");
        assertEquals(
                BigDecimal.ZERO,
                sourceMemberData.get(
                        "allocation_percentage"));
        EntityMutationCommand oldRoleUpdate =
                commands.stream()
                        .filter(command ->
                                "project_role_assignment"
                                        .equals(
                                                command.entityCode())
                                && "ROLE-1".equals(
                                        command.recordId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "REVOKED",
                oldRoleUpdate.payload()
                        .get("status"));
        EntityMutationCommand replacementCreate =
                commands.stream()
                        .filter(command ->
                                "project_role_assignment"
                                        .equals(
                                                command.entityCode())
                                && command.operationType()
                                == EntityMutationOperationType.CREATE)
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> replacementData =
                (Map<String, Object>) replacementCreate
                        .payload()
                        .get("data");
        assertEquals(
                "MEM-2",
                replacementData.get("member_id"));
        assertEquals(
                "ROLE-1",
                replacementData.get(
                        "predecessor_assignment_id"));
        EntityMutationCommand requestUpdate =
                commands.stream()
                        .filter(command ->
                                "project_member_change_request"
                                        .equals(
                                                command.entityCode()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "EFFECTIVE",
                requestUpdate.payload()
                        .get("status"));
    }

    @Test
    void reusesAlreadyEffectiveRequestWithoutMutation() {
        EntityDataDTO request =
                joinRequest("USER-3", "50");
        request.setStatus("EFFECTIVE");
        request.getData().put(
                "effective_member_id", "MEM-3");

        Map<String, Object> result =
                service.applyChange(request);

        assertEquals("MEM-3",
                result.get("memberId"));
        assertTrue((Boolean) result.get("reused"));
        verify(entityMutationPort, never())
                .execute(any());
    }

    private EntityDataDTO joinRequest(
            String userId,
            String allocation) {
        Map<String, Object> values =
                new LinkedHashMap<>();
        values.put("operation_type", "JOIN");
        values.put("project_id", "PRJ-1");
        values.put("target_user_id", userId);
        values.put("source_dept_id", "DEPT-1");
        values.put("employment_type", "INTERNAL");
        values.put("effective_date", "2026-08-10");
        values.put(
                "new_allocation_percentage",
                new BigDecimal(allocation));
        values.put(
                "change_reason",
                "补充客户平台开发资源");
        values.put(
                "account_required_flag", false);
        values.put(
                "environment_access_required_flag",
                false);
        values.put("sensitive_access_flag", false);
        return entity(
                "project_member_change_request",
                "PMCR-1",
                "PMCR202607290001",
                "DRAFT",
                values);
    }

    private EntityDataDTO leaveRequest(
            String memberId) {
        Map<String, Object> values =
                new LinkedHashMap<>();
        values.put("operation_type", "LEAVE");
        values.put("project_id", "PRJ-1");
        values.put("project_member_id", memberId);
        values.put("effective_date", "2026-08-20");
        values.put(
                "change_reason",
                "阶段任务完成后退出项目");
        return entity(
                "project_member_change_request",
                "PMCR-LEAVE",
                "PMCR202607290002",
                "APPROVED",
                values);
    }

    private EntityDataDTO project() {
        return entity(
                "project",
                "PRJ-1",
                "PRJ2026072800001",
                "ACTIVE",
                Map.of(
                        "planned_start_date",
                        "2026-08-01",
                        "planned_end_date",
                        "2026-12-31"));
    }

    private EntityDataDTO member(
            String id,
            String projectId,
            String userId,
            String status,
            String allocation) {
        return entity(
                "project_member",
                id,
                "MEMBER-" + id,
                status,
                Map.of(
                        "project_id", projectId,
                        "user_id", userId,
                        "join_date", "2026-08-01",
                        "allocation_percentage",
                        new BigDecimal(allocation),
                        "account_required_flag",
                        false,
                        "environment_access_required_flag",
                        false));
    }

    private EntityDataDTO role(
            String id,
            String memberId,
            String roleCode,
            boolean primary) {
        return entity(
                "project_role_assignment",
                id,
                "ROLE-" + id,
                "ACTIVE",
                Map.of(
                        "project_id", "PRJ-1",
                        "member_id", memberId,
                        "user_id",
                        "MEM-1".equals(memberId)
                                ? "USER-1" : "USER-2",
                        "role_code", roleCode,
                        "role_scope", "PROJECT",
                        "primary_flag", primary,
                        "effective_from",
                        "2026-08-01"));
    }

    private EntityDataDTO entity(
            String entityCode,
            String id,
            String code,
            String status,
            Map<String, Object> values) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode(entityCode);
        dto.setId(id);
        dto.setCode(code);
        dto.setName(code);
        dto.setStatus(status);
        dto.setSubmitterId("1");
        dto.setSubmitterName("超级管理员");
        dto.setData(new LinkedHashMap<>(values));
        return dto;
    }

    private String generatedId(
            String entityCode) {
        if ("project_role_assignment".equals(
                entityCode)) {
            return "ROLE-NEW-"
                    + roleIndex.incrementAndGet();
        }
        if ("project_member".equals(entityCode)) {
            return "MEM-NEW";
        }
        return "DATA-NEW";
    }
}
