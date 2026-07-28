package com.workflow.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectGovernanceServiceTest {

    private EntityDataDynamicService entityDataService;
    private ProjectGovernanceService service;

    @BeforeEach
    void setUp() {
        entityDataService = mock(EntityDataDynamicService.class);
        service = new ProjectGovernanceService(
                entityDataService,
                new ObjectMapper().findAndRegisterModules());
        when(entityDataService.findByCondition(anyString(), anyMap()))
                .thenReturn(List.of());
    }

    @Test
    void validatesProjectInitiationAcrossRequirementAndSystemEntities() {
        EntityDataDTO project = projectWithInitialScope();
        EntityDataDTO requirement = entity(
                "requirement",
                "REQ-1",
                "REQ2026072800005",
                "BACKLOG",
                Map.of());
        EntityDataDTO system = entity(
                "system_asset",
                "SYS-1",
                "SYS2026072800001",
                "PROPOSED",
                Map.of());
        EntityDataDTO allocation = entity(
                "requirement_project_link",
                "RPL-1",
                "REQPRJ1",
                "PROPOSED",
                Map.of("allocation_percentage", new BigDecimal("60")));

        when(entityDataService.findById("requirement", "REQ-1"))
                .thenReturn(requirement);
        when(entityDataService.findById("system_asset", "SYS-1"))
                .thenReturn(system);
        when(entityDataService.findByCondition(
                "requirement_project_link",
                Map.of("requirement_id", "REQ-1")))
                .thenReturn(List.of(allocation));

        Map<String, Object> result = service.validateProjectInitiation(project);

        assertEquals(1, result.get("requirementCount"));
        assertEquals(1, result.get("systemCount"));
        assertEquals("NEW_SYSTEM", result.get("projectType"));
    }

    @Test
    void rejectsProjectWhenRequirementAllocationExceedsOneHundredPercent() {
        EntityDataDTO project = projectWithInitialScope();
        when(entityDataService.findById("requirement", "REQ-1"))
                .thenReturn(entity(
                        "requirement",
                        "REQ-1",
                        "REQ1",
                        "BACKLOG",
                        Map.of()));
        when(entityDataService.findByCondition(
                "requirement_project_link",
                Map.of("requirement_id", "REQ-1")))
                .thenReturn(List.of(
                        entity(
                                "requirement_project_link",
                                "RPL-1",
                                "RPL1",
                                "APPROVED",
                                Map.of("allocation_percentage", 70)),
                        entity(
                                "requirement_project_link",
                                "RPL-2",
                                "RPL2",
                                "PROPOSED",
                                Map.of("allocation_percentage", 40))));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateProjectInitiation(project));

        assertEquals("REQUIREMENT_ALLOCATION_EXCEEDED", error.getErrorCode());
        assertTrue(error.getMessage().contains("超过100%"));
    }

    @Test
    void projectApprovalCreatesMembersRolesAndActivatesInitialLinks() {
        EntityDataDTO project = projectWithInitialScope();
        EntityDataDTO requirementLink = entity(
                "requirement_project_link",
                "RPL-1",
                "RPL1",
                "PROPOSED",
                new LinkedHashMap<>(Map.of(
                        "project_id", "PRJ-1",
                        "requirement_id", "REQ-1")));
        EntityDataDTO systemLink = entity(
                "project_system_link",
                "PSL-1",
                "PSL1",
                "PROPOSED",
                new LinkedHashMap<>(Map.of(
                        "project_id", "PRJ-1",
                        "system_id", "SYS-1")));

        when(entityDataService.findByCondition(
                "requirement_project_link",
                Map.of("project_id", "PRJ-1")))
                .thenReturn(List.of(requirementLink));
        when(entityDataService.findByCondition(
                "project_system_link",
                Map.of("project_id", "PRJ-1")))
                .thenReturn(List.of(systemLink));

        AtomicInteger memberIndex = new AtomicInteger();
        AtomicInteger catalogIndex = new AtomicInteger();
        AtomicInteger assignmentIndex = new AtomicInteger();
        when(entityDataService.save(any(EntityDataDTO.class)))
                .thenAnswer(invocation -> {
                    EntityDataDTO dto = invocation.getArgument(0);
                    String id = switch (dto.getEntityCode()) {
                        case "project_member" -> "MEM-" + memberIndex.incrementAndGet();
                        case "project_role_catalog" -> "CAT-" + catalogIndex.incrementAndGet();
                        case "project_role_assignment" -> "ASSIGN-" + assignmentIndex.incrementAndGet();
                        default -> "DATA-1";
                    };
                    dto.setId(id);
                    return dto;
                });

        Map<String, Object> result = service.applyProjectInitiation(project);

        assertEquals(1, result.get("requirementLinkCount"));
        assertEquals(1, result.get("systemLinkCount"));
        assertEquals(3, memberIndex.get());
        assertEquals(3, catalogIndex.get());
        assertEquals(3, assignmentIndex.get());
        assertFalse((Boolean) result.get("reused"));
        verify(entityDataService, times(9)).save(any(EntityDataDTO.class));

        ArgumentCaptor<Map<String, Object>> updateCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(entityDataService).update(
                org.mockito.ArgumentMatchers.eq("project"),
                org.mockito.ArgumentMatchers.eq("PRJ-1"),
                updateCaptor.capture());
        assertEquals("APPROVED", updateCaptor.getValue().get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> projectUpdate =
                (Map<String, Object>) updateCaptor.getValue().get("data");
        assertEquals(true, projectUpdate.get("initialization_completed_flag"));
    }

    @Test
    void rejectsRemovalWhenSystemScopedProjectRoleIsStillActive() {
        EntityDataDTO request = projectSystemChange("REMOVE");
        when(entityDataService.findById("project", "PRJ-1"))
                .thenReturn(entity("project", "PRJ-1", "PRJ1", "ACTIVE", Map.of()));
        when(entityDataService.findById("system_asset", "SYS-1"))
                .thenReturn(entity("system_asset", "SYS-1", "SYS1", "ACTIVE", Map.of()));
        when(entityDataService.findById("project_system_link", "PSL-1"))
                .thenReturn(entity(
                        "project_system_link",
                        "PSL-1",
                        "PSL1",
                        "ACTIVE",
                        Map.of("project_id", "PRJ-1", "system_id", "SYS-1")));
        when(entityDataService.findByCondition(
                "project_role_assignment",
                Map.of("project_id", "PRJ-1", "system_id", "SYS-1")))
                .thenReturn(List.of(entity(
                        "project_role_assignment",
                        "ROLE-1",
                        "ROLE1",
                        "ACTIVE",
                        Map.of())));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service.validateProjectSystemChange(request));

        assertEquals("PROJECT_SYSTEM_REMOVAL_BLOCKED", error.getErrorCode());
        assertTrue(error.getMessage().contains("有效系统级项目角色"));
    }

    @Test
    void appliesApprovedAddAndMarksRequestEffective() {
        EntityDataDTO request = projectSystemChange("ADD");
        request.getData().remove("project_system_link_id");
        request.getData().put("construction_mode", "ENHANCEMENT");
        request.getData().put("relation_reason", "新增客户画像能力改造范围");
        request.getData().put("affected_modules", "客户画像标签服务");
        request.getData().put("new_project_system_lead_id", "MEM-1");
        request.getData().put("new_technical_lead_id", "MEM-2");
        request.getData().put("planned_start_date", "2026-08-01");
        request.getData().put("planned_end_date", "2026-10-31");

        when(entityDataService.save(any(EntityDataDTO.class)))
                .thenAnswer(invocation -> {
                    EntityDataDTO dto = invocation.getArgument(0);
                    dto.setId("PSL-NEW");
                    dto.setCode("PRJSYS2026072800002");
                    return dto;
                });

        Map<String, Object> result = service.applyProjectSystemChange(request);

        assertEquals("ADD", result.get("operation"));
        assertEquals("PSL-NEW", result.get("projectSystemLinkId"));
        assertFalse((Boolean) result.get("reused"));

        ArgumentCaptor<Map<String, Object>> linkUpdate =
                ArgumentCaptor.forClass(Map.class);
        verify(entityDataService).update(
                org.mockito.ArgumentMatchers.eq("project_system_link"),
                org.mockito.ArgumentMatchers.eq("PSL-NEW"),
                linkUpdate.capture());
        assertEquals("ACTIVE", linkUpdate.getValue().get("status"));

        ArgumentCaptor<Map<String, Object>> requestUpdate =
                ArgumentCaptor.forClass(Map.class);
        verify(entityDataService).update(
                org.mockito.ArgumentMatchers.eq("project_system_change_request"),
                org.mockito.ArgumentMatchers.eq("PSC-1"),
                requestUpdate.capture());
        assertEquals("EFFECTIVE", requestUpdate.getValue().get("status"));
    }

    private EntityDataDTO projectWithInitialScope() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_type", "NEW_SYSTEM");
        data.put("project_manager_id", "1");
        data.put("product_owner_id", "2");
        data.put("business_owner_id", "3");
        data.put("project_sponsor_id", "4");
        data.put("sponsor_dept_id", "100");
        data.put("applicant_dept_id", "100");
        data.put("planned_start_date", "2026-08-01");
        data.put("planned_end_date", "2026-12-31");
        data.put("initialization_completed_flag", false);
        data.put("initial_requirement_links", List.of(new LinkedHashMap<>(Map.of(
                "requirement_id", "REQ-1",
                "allocation_percentage", new BigDecimal("60"),
                "planned_start_date", "2026-08-01",
                "planned_end_date", "2026-10-31"))));
        data.put("initial_system_links", List.of(new LinkedHashMap<>(Map.of(
                "system_id", "SYS-1",
                "planned_start_date", "2026-08-01",
                "planned_end_date", "2026-12-15"))));
        EntityDataDTO project = entity(
                "project",
                "PRJ-1",
                "PRJ2026072800001",
                "APPROVED",
                data);
        project.setName("统一客户运营平台建设");
        project.setSubmitterId("1");
        project.setSubmitterName("超级管理员");
        return project;
    }

    private EntityDataDTO projectSystemChange(String operation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation_type", operation);
        data.put("project_id", "PRJ-1");
        data.put("system_id", "SYS-1");
        data.put("project_system_link_id", "PSL-1");
        data.put("risk_level", "MEDIUM");
        data.put("planned_effective_date", LocalDate.now().plusDays(1).toString());
        data.put("rollback_plan", "恢复原项目系统关系");
        EntityDataDTO request = entity(
                "project_system_change_request",
                "PSC-1",
                "PRJSC2026072800001",
                "APPROVED",
                data);
        request.setName("项目系统关系变更");
        request.setSubmitterId("1");
        request.setSubmitterName("超级管理员");
        return request;
    }

    private EntityDataDTO entity(
            String entityCode,
            String id,
            String code,
            String status,
            Map<String, Object> data) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode(entityCode);
        dto.setId(id);
        dto.setCode(code);
        dto.setStatus(status);
        dto.setData(new LinkedHashMap<>(data));
        return dto;
    }
}
