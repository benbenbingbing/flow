package com.workflow.entity.version.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher;
import com.workflow.entity.version.application.EntityVersionScopePreviewService;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EntityVersionConfigurationControllerTest {

    @Mock
    private EntityVersionConfigurationService service;
    @Mock
    private EntityVersionPolicyMatcher matcher;
    @Mock
    private EntityVersionScopePreviewService previewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        EntityVersionConfigurationController controller =
                new EntityVersionConfigurationController(
                        service,
                        matcher,
                        previewService,
                        objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void currentGetReturnsTheSingleCurrentConfiguration() throws Exception {
        EntityVersionConfiguration configuration = configuration(7);
        when(service.get("asset")).thenReturn(configuration);

        mockMvc.perform(get(
                        "/api/entity-versions/configs/asset/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.entityCode")
                        .value("asset"))
                .andExpect(jsonPath("$.data.revision").value(7));

        verify(service).get("asset");
    }

    @Test
    void currentPutAndRootAliasParseIfMatchForCasSave() throws Exception {
        when(service.save(
                eq("asset"),
                any(EntityVersionConfiguration.class),
                eq(7)))
                .thenReturn(configuration(8));

        mockMvc.perform(put(
                        "/api/entity-versions/configs/asset/current")
                        .header("If-Match", "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(8));

        mockMvc.perform(put(
                        "/api/entity-versions/configs/asset")
                        .header("If-Match", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"enabled":true}
                                """))
                .andExpect(status().isOk());

        verify(service, org.mockito.Mockito.times(2)).save(
                eq("asset"),
                any(EntityVersionConfiguration.class),
                eq(7));
    }

    @Test
    void putWithoutIfMatchIsRejectedByTheHttpContract()
            throws Exception {
        mockMvc.perform(put(
                        "/api/entity-versions/configs/asset/current")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"enabled":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deprecatedDraftRoutesPreserveLegacyDraftSemantics()
            throws Exception {
        String root = "/api/entity-versions/configs/asset";
        when(service.legacyDraft("asset"))
                .thenReturn(Map.of(
                        "entityCode", "asset",
                        "revision", 7,
                        "status", "DRAFT"));
        when(service.saveLegacyDraft(
                eq("asset"),
                any(EntityVersionConfiguration.class),
                eq(7)))
                .thenReturn(Map.of(
                        "entityCode", "asset",
                        "revision", 8,
                        "status", "DRAFT"));

        mockMvc.perform(get(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(get(root + "/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(post(root + "/draft")
                        .header("If-Match", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"enabled":true,
                                 "revision":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(8));
        mockMvc.perform(post(root + "/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"enabled":true,
                                 "revision":7}
                                """))
                .andExpect(status().isOk());

        verify(service, org.mockito.Mockito.times(2)).legacyDraft("asset");
        verify(service, org.mockito.Mockito.times(2)).saveLegacyDraft(
                eq("asset"),
                any(EntityVersionConfiguration.class),
                eq(7));
    }

    @Test
    void deprecatedPublishAndReleaseRoutesAreRevisionCheckedAndIdempotent()
            throws Exception {
        String root = "/api/entity-versions/configs/asset";
        when(service.publishLegacyDraft("asset", 8))
                .thenReturn(configuration(8));
        when(service.legacyReleasePage("asset", 1, 20))
                .thenReturn(new PageResult<>(
                        List.of(Map.of("version", 8)), 1, 1, 20));

        mockMvc.perform(post(root + "/publish")
                        .header("If-Match", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(8));
        mockMvc.perform(post(root + "/releases")
                        .header("If-Match", "8"))
                .andExpect(status().isOk());
        mockMvc.perform(get(root + "/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].version")
                        .value(8));

        verify(service, org.mockito.Mockito.times(2))
                .publishLegacyDraft("asset", 8);
        verify(service).legacyReleasePage("asset", 1, 20);
    }

    private EntityVersionConfiguration configuration(int revision) {
        EntityVersionConfiguration result =
                new EntityVersionConfiguration();
        result.setEntityCode("asset");
        result.setEnabled(true);
        result.setRevision(revision);
        return result;
    }
}
