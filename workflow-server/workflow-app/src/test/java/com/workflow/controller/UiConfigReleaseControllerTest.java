package com.workflow.controller;

import com.workflow.entity.ui.api.web.UiConfigReleaseController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigDraftDiscardResultDTO;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.request.UiConfigDraftDiscardRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiConfigReleaseControllerTest {

    private UiConfigReleaseService releaseService;
    private UiConfigurationAccessService accessService;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        releaseService = mock(UiConfigReleaseService.class);
        accessService = mock(UiConfigurationAccessService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UiConfigReleaseController(
                                releaseService,
                                accessService))
                .build();
    }

    @Test
    void formDiscardDraftMapsConcurrencyContract() throws Exception {
        when(releaseService.discardDraft(
                eq(UiConfigReleaseService.FORM),
                eq("form-discard"),
                any(UiConfigDraftDiscardRequest.class)))
                .thenReturn(discardResult(
                        UiConfigReleaseService.FORM,
                        "form-discard"));

        mockMvc.perform(post(
                        "/api/entity-forms/{id}/discard-draft",
                        "form-discard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discardRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configType").value("FORM"))
                .andExpect(jsonPath("$.data.revision").value(10))
                .andExpect(jsonPath("$.data.draftHash")
                        .value("published-hash"));

        ArgumentCaptor<UiConfigDraftDiscardRequest> captor =
                ArgumentCaptor.forClass(
                        UiConfigDraftDiscardRequest.class);
        verify(accessService).requireFormAccess("form-discard");
        verify(releaseService).discardDraft(
                eq(UiConfigReleaseService.FORM),
                eq("form-discard"),
                captor.capture());
        assertDiscardRequest(captor.getValue());
    }

    @Test
    void listDiscardDraftMapsConcurrencyContract() throws Exception {
        when(releaseService.discardDraft(
                eq(UiConfigReleaseService.LIST),
                eq("list-discard"),
                any(UiConfigDraftDiscardRequest.class)))
                .thenReturn(discardResult(
                        UiConfigReleaseService.LIST,
                        "list-discard"));

        mockMvc.perform(post(
                        "/api/entity-list-config/{id}/discard-draft",
                        "list-discard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discardRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configType").value("LIST"))
                .andExpect(jsonPath("$.data.activeReleaseId")
                        .value("active-release"));

        ArgumentCaptor<UiConfigDraftDiscardRequest> captor =
                ArgumentCaptor.forClass(
                        UiConfigDraftDiscardRequest.class);
        verify(accessService).requireListAccess("list-discard");
        verify(releaseService).discardDraft(
                eq(UiConfigReleaseService.LIST),
                eq("list-discard"),
                captor.capture());
        assertDiscardRequest(captor.getValue());
    }

    @Test
    void formPublishPreviewMapsHotfixRequest() throws Exception {
        when(releaseService.publishPreview(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                any(UiConfigPublishRequest.class)))
                .thenReturn(preview(
                        UiConfigReleaseService.FORM,
                        "form-1"));

        mockMvc.perform(post(
                        "/api/entity-forms/{id}/publish-preview",
                        "form-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotfixRequestJson("表单热修复预检")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configType")
                        .value(UiConfigReleaseService.FORM))
                .andExpect(jsonPath("$.data.impactToken")
                        .value("impact-token"));

        ArgumentCaptor<UiConfigPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(UiConfigPublishRequest.class);
        verify(accessService).requireFormAccess("form-1");
        verify(releaseService).publishPreview(
                eq(UiConfigReleaseService.FORM),
                eq("form-1"),
                requestCaptor.capture());
        assertHotfixRequest(
                requestCaptor.getValue(),
                "表单热修复预检");
    }

    @Test
    void listPublishPreviewMapsHotfixRequest() throws Exception {
        when(releaseService.publishPreview(
                eq(UiConfigReleaseService.LIST),
                eq("list-1"),
                any(UiConfigPublishRequest.class)))
                .thenReturn(preview(
                        UiConfigReleaseService.LIST,
                        "list-1"));

        mockMvc.perform(post(
                        "/api/entity-list-config/{id}/publish-preview",
                        "list-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotfixRequestJson("列表热修复预检")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configType")
                        .value(UiConfigReleaseService.LIST))
                .andExpect(jsonPath("$.data.configId")
                        .value("list-1"));

        ArgumentCaptor<UiConfigPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(UiConfigPublishRequest.class);
        verify(accessService).requireListAccess("list-1");
        verify(releaseService).publishPreview(
                eq(UiConfigReleaseService.LIST),
                eq("list-1"),
                requestCaptor.capture());
        assertHotfixRequest(
                requestCaptor.getValue(),
                "列表热修复预检");
    }

    @Test
    void formPublishMapsHotfixRequest() throws Exception {
        when(releaseService.publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-2"),
                any(UiConfigPublishRequest.class)))
                .thenReturn(release(
                        "form-release-2",
                        UiConfigReleaseService.FORM,
                        "form-2"));

        mockMvc.perform(post(
                        "/api/entity-forms/{id}/publish",
                        "form-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotfixRequestJson("表单热修复发布")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id")
                        .value("form-release-2"))
                .andExpect(jsonPath("$.data.releaseMode")
                        .value("HOTFIX"));

        ArgumentCaptor<UiConfigPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(UiConfigPublishRequest.class);
        verify(accessService).requireFormAccess("form-2");
        verify(releaseService).publish(
                eq(UiConfigReleaseService.FORM),
                eq("form-2"),
                requestCaptor.capture());
        assertHotfixRequest(
                requestCaptor.getValue(),
                "表单热修复发布");
    }

    @Test
    void listPublishMapsHotfixRequest() throws Exception {
        when(releaseService.publish(
                eq(UiConfigReleaseService.LIST),
                eq("list-2"),
                any(UiConfigPublishRequest.class)))
                .thenReturn(release(
                        "list-release-2",
                        UiConfigReleaseService.LIST,
                        "list-2"));

        mockMvc.perform(post(
                        "/api/entity-list-config/{id}/publish",
                        "list-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotfixRequestJson("列表热修复发布")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configType")
                        .value(UiConfigReleaseService.LIST))
                .andExpect(jsonPath("$.data.releaseMode")
                        .value("HOTFIX"));

        ArgumentCaptor<UiConfigPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(UiConfigPublishRequest.class);
        verify(accessService).requireListAccess("list-2");
        verify(releaseService).publish(
                eq(UiConfigReleaseService.LIST),
                eq("list-2"),
                requestCaptor.capture());
        assertHotfixRequest(
                requestCaptor.getValue(),
                "列表热修复发布");
    }

    @Test
    void formRollbackMapsReleaseAndReason() throws Exception {
        when(releaseService.rollbackHotfix(
                UiConfigReleaseService.FORM,
                "form-3",
                "release-3",
                "回滚表单热修复"))
                .thenReturn(release(
                        "release-3",
                        UiConfigReleaseService.FORM,
                        "form-3"));

        mockMvc.perform(post(
                        "/api/entity-forms/{id}/releases/{releaseId}"
                                + "/rollback-hotfix",
                        "form-3",
                        "release-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"回滚表单热修复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("release-3"));

        verify(accessService).requireFormAccess("form-3");
        verify(releaseService).rollbackHotfix(
                UiConfigReleaseService.FORM,
                "form-3",
                "release-3",
                "回滚表单热修复");
    }

    @Test
    void listRollbackMapsReleaseAndReason() throws Exception {
        when(releaseService.rollbackHotfix(
                UiConfigReleaseService.LIST,
                "list-3",
                "release-4",
                "回滚列表热修复"))
                .thenReturn(release(
                        "release-4",
                        UiConfigReleaseService.LIST,
                        "list-3"));

        mockMvc.perform(post(
                        "/api/entity-list-config/{id}/releases/{releaseId}"
                                + "/rollback-hotfix",
                        "list-3",
                        "release-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"回滚列表热修复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("release-4"));

        verify(accessService).requireListAccess("list-3");
        verify(releaseService).rollbackHotfix(
                UiConfigReleaseService.LIST,
                "list-3",
                "release-4",
                "回滚列表热修复");
    }

    @Test
    void legacyPublishRequestsRemainAccepted() throws Exception {
        mockMvc.perform(post(
                        "/api/entity-forms/{id}/publish",
                        "legacy-form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"旧客户端表单发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post(
                        "/api/entity-list-config/{id}/publish",
                        "legacy-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(accessService).requireFormAccess("legacy-form");
        verify(accessService).requireListAccess("legacy-list");
        verify(releaseService).publish(
                eq(UiConfigReleaseService.FORM),
                eq("legacy-form"),
                argThat((UiConfigPublishRequest request) ->
                        request != null
                                && "旧客户端表单发布".equals(
                                        request.getDescription())
                                && request.getReleaseMode() == null
                                && request.getImpactToken() == null));
        verify(releaseService).publish(
                eq(UiConfigReleaseService.LIST),
                eq("legacy-list"),
                isNull(UiConfigPublishRequest.class));
    }

    private String hotfixRequestJson(String description)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("description", description);
        request.put("releaseMode", "HOTFIX");
        request.put("rolloutScope", "ACTIVE_AND_FUTURE");
        request.put("expectedActiveReleaseId", "active-release");
        request.put("expectedDraftHash", "draft-hash");
        request.put("impactToken", "impact-token");
        return objectMapper.writeValueAsString(request);
    }

    private String discardRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "expectedRevision", 9,
                "expectedDraftHash", "draft-hash",
                "expectedActiveReleaseId", "active-release",
                "reason", "放弃本次调整"));
    }

    private void assertDiscardRequest(
            UiConfigDraftDiscardRequest request) {
        assertAll(
                () -> assertEquals(9, request.getExpectedRevision()),
                () -> assertEquals(
                        "draft-hash",
                        request.getExpectedDraftHash()),
                () -> assertEquals(
                        "active-release",
                        request.getExpectedActiveReleaseId()),
                () -> assertEquals(
                        "放弃本次调整",
                        request.getReason()));
    }

    private UiConfigDraftDiscardResultDTO discardResult(
            String configType,
            String configId) {
        return UiConfigDraftDiscardResultDTO.builder()
                .configType(configType)
                .configId(configId)
                .discardedDraftHash("draft-hash")
                .draftHash("published-hash")
                .publishedHash("published-hash")
                .activeReleaseId("active-release")
                .activeVersion(3)
                .previousRevision(9)
                .revision(10)
                .build();
    }

    private void assertHotfixRequest(
            UiConfigPublishRequest request,
            String description) {
        assertAll(
                () -> assertEquals(
                        description,
                        request.getDescription()),
                () -> assertEquals(
                        "HOTFIX",
                        request.getReleaseMode()),
                () -> assertEquals(
                        "ACTIVE_AND_FUTURE",
                        request.getRolloutScope()),
                () -> assertEquals(
                        "active-release",
                        request.getExpectedActiveReleaseId()),
                () -> assertEquals(
                        "draft-hash",
                        request.getExpectedDraftHash()),
                () -> assertEquals(
                        "impact-token",
                        request.getImpactToken()),
                () -> assertNull(request.getOverrideRisk()),
                () -> assertNull(request.getOverrideReason()));
    }

    private UiConfigPublishPreviewDTO preview(
            String configType,
            String configId) {
        return UiConfigPublishPreviewDTO.builder()
                .configType(configType)
                .configId(configId)
                .releaseMode("HOTFIX")
                .rolloutScope("ACTIVE_AND_FUTURE")
                .draftHash("draft-hash")
                .activeReleaseId("active-release")
                .activeVersion(3)
                .targetHash("target-hash")
                .impactToken("impact-token")
                .riskLevel("REVIEW")
                .changed(true)
                .requiresOverride(false)
                .canPublish(true)
                .processVersionCount(2)
                .activeInstanceCount(4)
                .skippedHistoricalInstanceCount(6)
                .changedItems(List.of())
                .riskItems(List.of())
                .targets(List.of())
                .blockers(List.of())
                .build();
    }

    private UiConfigRelease release(
            String id,
            String configType,
            String configId) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(id);
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(4);
        release.setReleaseMode("HOTFIX");
        release.setStatus("ACTIVE");
        return release;
    }
}
