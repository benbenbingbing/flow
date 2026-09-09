package com.workflow.entity.version.api.web;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void capabilitiesExposePublishedRuntimeContractAfterEntityViewCheck()
            throws Exception {
        when(configurationService.recordCapabilities("asset"))
                .thenReturn(new EntityRecordVersionCapabilities(true, true));

        mockMvc.perform(get(
                        "/api/entity-versions/records/asset/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeEnabled").value(true))
                .andExpect(jsonPath("$.data.manualCaptureEnabled")
                        .value(true));

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
}
