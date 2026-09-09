package com.workflow.entity.version.api.web;

import com.workflow.core.result.PageResult;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import com.workflow.entity.version.application.model.EntityRecordVersionCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityRecordVersionControllerTest {

    @Mock
    private EntityRecordVersionService versionService;
    @Mock
    private EntityActionCapabilityService actionCapabilityService;
    @Mock
    private EntityVersionConfigurationService configurationService;
    @Mock
    private EntityRecordVersionComparisonService comparisonService;
    @Mock
    private EntityVersionRestorePlanService restorePlanService;
    @Mock
    private EntityDataDynamicService dataService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EntityRecordVersionController controller =
                new EntityRecordVersionController(
                        versionService,
                        actionCapabilityService,
                        configurationService,
                        comparisonService,
                        restorePlanService,
                        dataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void capabilitiesExposeCurrentRuntimeContractAfterEntityViewCheck()
            throws Exception {
        when(configurationService.recordCapabilities("asset"))
                .thenReturn(new EntityRecordVersionCapabilities(
                        true, true, false));

        mockMvc.perform(get(
                        "/api/entity-versions/records/asset/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeEnabled").value(true))
                .andExpect(jsonPath("$.data.manualCaptureEnabled")
                        .value(true))
                .andExpect(jsonPath("$.data.historyReadable")
                        .value(false));

        InOrder authorizationBeforeRead = inOrder(
                actionCapabilityService, configurationService);
        authorizationBeforeRead.verify(actionCapabilityService)
                .requireStandardPermission(
                        "asset", EntityPermissionAction.VIEW);
        authorizationBeforeRead.verify(configurationService)
                .recordCapabilities("asset");
        verifyNoInteractions(
                versionService,
                comparisonService,
                restorePlanService,
                dataService);
    }

    @Test
    void manualCaptureAuthorizesEntityAndCurrentRecordBeforeService()
            throws Exception {
        mockMvc.perform(post(
                        "/api/entity-versions/records/asset/record-1/captures")
                        .header("Idempotency-Key", "capture-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        InOrder authorizationBeforeCapture = inOrder(
                actionCapabilityService, dataService, versionService);
        authorizationBeforeCapture.verify(actionCapabilityService)
                .requireStandardPermission(
                        "asset", EntityPermissionAction.VIEW);
        authorizationBeforeCapture.verify(dataService)
                .findAccessibleById("asset", "record-1", null);
        authorizationBeforeCapture.verify(versionService)
                .captureManual(
                        org.mockito.ArgumentMatchers.eq("asset"),
                        org.mockito.ArgumentMatchers.eq("record-1"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("capture-1"));
        verifyNoInteractions(
                configurationService,
                comparisonService,
                restorePlanService);
    }

    @Test
    void historyListAuthorizesIncludingDeletedRecordBeforeRead()
            throws Exception {
        when(versionService.listPage("asset", "record-1", 2, 5))
                .thenReturn(new PageResult<>(java.util.List.of(), 0, 2, 5));

        mockMvc.perform(get(
                        "/api/entity-versions/records/asset/record-1")
                        .param("pageNum", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNum").value(2));

        InOrder authorizationBeforeHistory = inOrder(
                actionCapabilityService, dataService, versionService);
        authorizationBeforeHistory.verify(actionCapabilityService)
                .requireStandardPermission(
                        "asset", EntityPermissionAction.VIEW);
        authorizationBeforeHistory.verify(dataService)
                .findAccessibleIncludingDeletedById(
                        "asset", "record-1", null);
        authorizationBeforeHistory.verify(versionService)
                .listPage("asset", "record-1", 2, 5);
        verifyNoInteractions(
                configurationService,
                comparisonService,
                restorePlanService);
    }
}
