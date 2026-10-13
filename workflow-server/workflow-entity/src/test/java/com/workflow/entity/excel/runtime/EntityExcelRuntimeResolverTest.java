package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.model.EntityExportBatch;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.*;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityExcelRuntimeResolverTest {
    private final EntityExcelConfigurationService configurations = mock(EntityExcelConfigurationService.class);
    private final ExcelActionTokenService tokens = new ExcelActionTokenService(new ObjectMapper(), "runtime-test-secret");
    private final EntityActionCapabilityService capabilities = mock(EntityActionCapabilityService.class);
    private final EntityListActionConfigService buttons = mock(EntityListActionConfigService.class);
    private final EntityDataListConfigService lists = mock(EntityDataListConfigService.class);
    private final EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
    private final EntityDataDynamicService data = mock(EntityDataDynamicService.class);
    private final EntityPublishedSnapshotService entities = mock(EntityPublishedSnapshotService.class);
    private final UiEventRuntimeService events = mock(UiEventRuntimeService.class);
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final EntityListConfig list = new EntityListConfig();
    private ExcelSchemeDocument.ImportConfig importOptions = new ExcelSchemeDocument.ImportConfig(
            true, "CREATE_ONLY", "BATCH", "create", false, 1000);
    private final EntityExcelRuntimeResolver resolver = new EntityExcelRuntimeResolver(configurations, tokens, capabilities,
            buttons, lists, listMapper, data, entities, provider(events), provider(bindings), provider(releases), provider(interfaces), new ObjectMapper());

    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
    @BeforeEach void setup() {
        UserContext.setCurrentUser("alice", "alice");
        list.setId("list1"); list.setEntityCode("employee"); list.setEntityId("entity1"); list.setListKey("employees");
        list.setActiveReleaseId("list-release"); list.setPublishedVersion(1);
        when(listMapper.selectById("list1")).thenReturn(list);
        when(lists.findListConfig("employee", "employees")).thenReturn(list);
        when(lists.findListConfig(eq("employee"), eq("employees"), any(), any())).thenReturn(list);
        when(buttons.resolveToolbarButtons(list, "employee")).thenReturn(List.of(
                Map.of("key", "import", "type", "built-in", "enabled", true),
                Map.of("key", "exportSelected", "type", "built-in", "enabled", true),
                Map.of("key", "exportAll", "type", "built-in", "enabled", true)));
        when(data.findAccessibleById("employee", "record1", "employees")).thenReturn(new EntityDataDTO());
        when(entities.getLatestPinnedByEntityId("entity1")).thenReturn(new EntityPublishedSnapshotService.PinnedEntitySnapshot(new EntityPublishedSnapshot(), "entityHash"));
        when(configurations.resolvePublished(eq("employee"), anyString(), eq("DEFAULT"), isNull())).thenAnswer(call -> scheme("default", "defaultHash", "schemeHash", !"EXPORT".equals(call.getArgument(1))));
        when(configurations.resolvePublished(eq("employee"), anyString(), eq("EXPLICIT"), eq("specific"))).thenAnswer(call -> scheme("specific", "", "schemeHash", !"EXPORT".equals(call.getArgument(1))));
        when(releases.resolveRuntimeFormRelease("write-form")).thenReturn(writeForm("formHash"));
        when(bindings.resolvePublished(any())).thenReturn(chain(List.of()));
        when(interfaces.freezeOperation("excel-extension", null)).thenReturn(new UiInterfaceExtensionService.PublishedOperationSnapshot("excel-extension", "excel", 1, null, "{}", "extensionHash"));
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    private ExcelResolvedScheme scheme(String code, String defaultHash, String hash, boolean input) {
        return new ExcelResolvedScheme("entity1", "employee", code,
                new ExcelSchemeDocument(1, code, "员工", null,
                        new ExcelSchemeDocument.FileConfig("数据", 1, "REJECT"), importOptions,
                        new ExcelSchemeDocument.ExportConfig(true, "员工", "数据"), List.of()),
                hash, defaultHash, input ? "write-form" : null);
    }
    private ResolvedEntityFormRelease writeForm(String hash) {
        return new ResolvedEntityFormRelease(new EntityForm(), "formRelease", 1, false, "formRelease", hash, null, UiRuntimePurpose.STANDALONE);
    }
    private ExcelPrepareRequest request(String action) {
        return new ExcelPrepareRequest("LIST", "list1", action, "employee", "employees", null, Map.of(), null, List.of("record1"), Map.of());
    }
    private String prepare(String action) { return String.valueOf(resolver.prepare(request(action)).get("actionContextToken")); }
    private UiEventBindingService.ResolvedEventChain chain(List<Map<String, Object>> steps) {
        return new UiEventBindingService.ResolvedEventChain(steps, "release", 1, "entity1", "employee", "employees", Map.of("list", Map.of("id", "list1")));
    }
    private String custom(String operation, String mode, String scope, String eventCode) {
        var origin = new UiEventExecuteRequest(); origin.setConfigType("LIST"); origin.setConfigId("list1"); origin.setEntityCode("employee");
        origin.setListKey("employees"); origin.setTargetType("BUTTON"); origin.setTargetKey("custom-excel"); origin.setEventCode(eventCode);
        origin.setSelectedIds(List.of("record1")); origin.setRecordId("record1");
        origin.setInput(Map.of("filters", Map.of("department", "finance"), "form", Map.of("unsaved", "secret")));
        var chain = chain(List.of(Map.of("extensionId", "excel-extension", "strategy", "REPLACE")));
        when(events.authorizeExcelOrigin(any())).thenReturn(chain);
        Map<String, Object> ref = "EXPLICIT".equals(mode) ? Map.of("mode", mode, "schemeCode", "specific") : Map.of("mode", mode);
        try (var ignored = UiEventOriginContext.open(origin, chain)) {
            return String.valueOf(resolver.prepareCustom("excel-extension", Map.of("operation", operation, "schemeRef", ref, "dataScope", scope)).get("actionContextToken"));
        }
    }
    private Map<String, Object> builtinStep(String mode) {
        Map<String, Object> reference = "DEFAULT".equals(mode) ? Map.of("mode", mode)
                : Map.of("mode", mode, "schemeCode", "specific");
        return Map.of("actionType", "ENTITY_EXCEL", "strategy", "REPLACE", "actionConfig",
                Map.of("operation", "EXPORT", "dataScope", "SELECTED", "schemeRef", reference));
    }
    private UiEventExecuteRequest builtinOrigin() {
        var origin = new UiEventExecuteRequest(); origin.setConfigType("LIST"); origin.setConfigId("list1");
        origin.setEntityCode("employee"); origin.setListKey("employees"); origin.setTargetType("BUTTON");
        origin.setTargetKey("custom-excel"); origin.setEventCode("TOOLBAR_BUTTON_CLICK");
        origin.setSelectedIds(List.of("record1"));
        return origin;
    }
    private String builtinToken(String mode) {
        var step = builtinStep(mode); var chain = chain(List.of(step));
        when(events.authorizeExcelOrigin(any())).thenReturn(chain);
        try (var ignored = UiEventOriginContext.open(builtinOrigin(), chain)) {
            return String.valueOf(resolver.prepareBoundAction(step).get("actionContextToken"));
        }
    }
    @Test void builtinUsesPublishedStaticConfigurationAndNeedsNoInterfaceRecord() {
        var origin = builtinOrigin();
        origin.setInput(Map.of("operation", "IMPORT", "schemeRef", Map.of("mode", "DEFAULT"), "dataScope", "FILTERED"));
        var step = builtinStep("EXPLICIT"); var chain = chain(List.of(step));
        when(events.authorizeExcelOrigin(any())).thenReturn(chain);
        String token;
        try (var ignored = UiEventOriginContext.open(origin, chain)) {
            token = String.valueOf(resolver.prepareBoundAction(step).get("actionContextToken"));
        }
        var plan = resolver.resolve(token, "EXPORT");
        assertThat(plan.scheme().schemeCode()).isEqualTo("specific");
        assertThat(plan.claims().dataScope()).isEqualTo("SELECTED");
        assertThat(plan.claims().origin().getInput()).containsOnlyKeys("filters");
        verifyNoInteractions(interfaces);
        verify(capabilities, times(2)).requireStandardPermission("employee", EntityPermissionAction.EXPORT);
    }
    @Test void builtinRejectsMissingContextOrForgedActionBeforeReadingConfiguration() {
        assertThatThrownBy(() -> resolver.prepareBoundAction(builtinStep("DEFAULT"))).isInstanceOf(ForbiddenException.class);
        try (var ignored = UiEventOriginContext.open(builtinOrigin(), chain(List.of(builtinStep("EXPLICIT"))))) {
            assertThatThrownBy(() -> resolver.prepareBoundAction(builtinStep("DEFAULT"))).isInstanceOf(ForbiddenException.class);
        }
        verifyNoInteractions(configurations, interfaces);
    }
    @Test void builtinRepublishInvalidatesTokenEvenWithoutExtensionChanges() {
        String token = builtinToken("EXPLICIT");
        when(events.authorizeExcelOrigin(any())).thenReturn(chain(List.of(builtinStep("DEFAULT"))));
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("配置已变化");
        verifyNoInteractions(interfaces);
    }
    @Test void builtinDefaultAndRevokedPermissionsAreRecheckedAtDownload() {
        String token = builtinToken("DEFAULT");
        assertThat(resolver.resolve(token, "EXPORT").scheme().schemeCode()).isEqualTo("default");
        doThrow(new ForbiddenException("导出权限已撤销")).when(capabilities)
                .requireStandardPermission("employee", EntityPermissionAction.EXPORT);
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).hasMessageContaining("权限已撤销");
        verifyNoInteractions(interfaces);
    }
    @Test void defaultPreparationAndExecutionResolveCurrentDefaultWithoutExplicitCode() {
        String token = prepare("exportSelected");
        var plan = resolver.resolve(token, "EXPORT");
        assertThat(plan.scheme().schemeCode()).isEqualTo("default");
        assertThat(plan.claims().schemeCode()).isEqualTo("default");
        verify(configurations, times(2)).resolvePublished("employee", "EXPORT", "DEFAULT", null);
        verify(capabilities, times(2)).requireStandardPermission("employee", EntityPermissionAction.EXPORT);
    }
    @Test void explicitCustomButtonDoesNotAcquireDefaultAndDropsUnsavedInput() {
        String token = custom("EXPORT", "EXPLICIT", "SELECTED", "TOOLBAR_BUTTON_CLICK");
        var claims = tokens.verify(token, "EXPORT");
        assertThat(claims.origin().getInput()).containsOnlyKeys("filters");
        assertThat(resolver.resolve(token, "EXPORT").scheme().schemeCode()).isEqualTo("specific");
        verify(configurations, times(2)).resolvePublished("employee", "EXPORT", "EXPLICIT", "specific");
        verify(configurations, never()).resolvePublished(any(), any(), eq("DEFAULT"), any());
        verify(events).authorizeExcelOrigin(any());
    }
    @Test void defaultCustomReferenceResolvesDefaultWithoutInventingAnExplicitCode() {
        String token = custom("EXPORT", "DEFAULT", "SELECTED", "TOOLBAR_BUTTON_CLICK");
        assertThat(resolver.resolve(token, "EXPORT").scheme().schemeCode()).isEqualTo("default");
        verify(configurations, times(2)).resolvePublished("employee", "EXPORT", "DEFAULT", null);
    }
    @Test void changingCustomInterfaceDefinitionInvalidatesAlreadyPreparedToken() {
        String token = custom("EXPORT", "EXPLICIT", "SELECTED", "TOOLBAR_BUTTON_CLICK");
        when(interfaces.freezeOperation("excel-extension", null)).thenReturn(new UiInterfaceExtensionService.PublishedOperationSnapshot(
                "excel-extension", "excel", 2, null, "{}", "updated-extensionHash"));
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("配置已变化");
    }
    @Test void switchedDefaultOrChangedSchemeRejectsStaleToken() {
        String token = prepare("exportSelected");
        when(configurations.resolvePublished("employee", "EXPORT", "DEFAULT", null)).thenReturn(scheme("newDefault", "newDefaultHash", "newHash", false));
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }
    @Test void entityOrWritingFormRepublishInvalidatesAcceptedPageToken() {
        String token = prepare("import");
        when(releases.resolveRuntimeFormRelease("write-form")).thenReturn(writeForm("changedForm"));
        assertThatThrownBy(() -> resolver.resolve(token, "IMPORT")).isInstanceOf(ResponseStatusException.class);
        when(releases.resolveRuntimeFormRelease("write-form")).thenReturn(writeForm("formHash"));
        when(entities.getLatestPinnedByEntityId("entity1")).thenReturn(new EntityPublishedSnapshotService.PinnedEntitySnapshot(new EntityPublishedSnapshot(), "newEntityHash"));
        assertThatThrownBy(() -> resolver.resolve(token, "IMPORT")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void permissionRevokedAfterPreparationIsCheckedAgain() {
        String token = prepare("exportSelected");
        doThrow(new ForbiddenException("已撤销导出权限")).when(capabilities).requireStandardPermission("employee", EntityPermissionAction.EXPORT);
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ForbiddenException.class).hasMessageContaining("撤销");
    }
    @Test void defaultButtonDisappearingRejectsExecution() {
        String token = prepare("exportSelected");
        when(buttons.resolveToolbarButtons(list, "employee")).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ForbiddenException.class).hasMessageContaining("不存在");
    }
    @Test void forgedListOwnerOrWrongEntityIsRejected() {
        list.setEntityCode("another");
        assertThatThrownBy(() -> prepare("exportSelected")).isInstanceOf(ForbiddenException.class).hasMessageContaining("来源不匹配");
        verifyNoInteractions(configurations);
    }
    @Test void templateOnlyActionCannotExecuteImport() {
        String token = custom("TEMPLATE", "EXPLICIT", "SELECTED", "TOOLBAR_BUTTON_CLICK");
        assertThat(resolver.resolve(token, "TEMPLATE").scheme().schemeCode()).isEqualTo("specific");
        assertThatThrownBy(() -> resolver.resolve(token, "IMPORT")).isInstanceOf(ForbiddenException.class);
    }
    @Test void prepareCustomRequiresRealEventAndUnconditionalMainStep() {
        assertThatThrownBy(() -> resolver.prepareCustom("excel-extension", Map.of())).isInstanceOf(ForbiddenException.class);
        var origin = new UiEventExecuteRequest(); origin.setEventCode("TOOLBAR_BUTTON_CLICK");
        try (var ignored = UiEventOriginContext.open(origin, chain(List.of(Map.of("extensionId", "excel-extension", "strategy", "BEFORE"))))) {
            assertThatThrownBy(() -> resolver.prepareCustom("excel-extension", Map.of())).isInstanceOf(ForbiddenException.class);
        }
    }
    @Test void rowButtonsCannotBatchImportOrExportArbitrarySelections() {
        assertThatThrownBy(() -> custom("IMPORT", "EXPLICIT", "SELECTED", "ROW_BUTTON_CLICK")).hasMessageContaining("行按钮不支持");
        assertThatThrownBy(() -> custom("EXPORT", "EXPLICIT", "SELECTED", "ROW_BUTTON_CLICK")).hasMessageContaining("当前记录");
    }
    @Test void filteredExportRequiresControlledDatabaseQuery() {
        list.setQueryProviderCode("remote-custom");
        assertThatThrownBy(() -> prepare("exportAll")).hasMessageContaining("受控Excel导出适配器");
        verify(capabilities).requireStandardPermission("employee", EntityPermissionAction.EXPORT_ALL);
    }
    @Test void importChecksRequireBothCreateAndImportPermissions() {
        String token = prepare("import"); resolver.resolve(token, "CHECK");
        verify(capabilities, times(2)).requireStandardPermission("employee", EntityPermissionAction.IMPORT);
        verify(capabilities, times(2)).requireStandardPermission("employee", EntityPermissionAction.CREATE);
    }

    @Test void importPreparationExposesPublishedReadingAndSubmissionOptions() {
        importOptions = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "create", false,
                1000, "DIRECT", false, false, 37, false, false, "CONTINUE", 25);

        var prepared = resolver.prepare(request("import"));

        assertThat(prepared).containsEntry("validateHeader", false).containsEntry("stopAtEmptyRow", false)
                .containsEntry("batchSize", 37).containsEntry("returnUnimported", false)
                .containsEntry("precheckEnabled", false).containsEntry("failurePolicy", "CONTINUE")
                .containsEntry("transactionPolicy", "BATCH").containsEntry("maxFileSizeMb", 25);
    }

    @Test void legacyImportPreparationUsesTenMbDefault() {
        assertThat(resolver.prepare(request("import"))).containsEntry("maxFileSizeMb", 10);
    }

    @Test void enabledPrecheckIsIncludedInSignedOperationsAndCanResolve() {
        String token = prepare("import");

        assertThat(tokens.verify(token, "CHECK").allowedOperations()).containsExactly("IMPORT", "CHECK", "TEMPLATE");
        assertThat(resolver.resolve(token, "CHECK").scheme().document().importConfig().precheckEnabled()).isTrue();
    }

    @Test void disabledPrecheckCannotBeRequestedWithBuiltInOrCustomImportToken() {
        importOptions = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "create", false,
                1000, "DIRECT", true, true, 100, true, false, "STOP");

        for (String token : List.of(prepare("import"), custom("IMPORT", "EXPLICIT", "SELECTED", "TOOLBAR_BUTTON_CLICK"))) {
            assertThat(tokens.verify(token, "IMPORT").allowedOperations()).containsExactly("IMPORT", "TEMPLATE");
            assertThatThrownBy(() -> tokens.verify(token, "CHECK")).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> resolver.resolve(token, "CHECK")).isInstanceOf(ForbiddenException.class);
            assertThat(resolver.resolve(token, "IMPORT").scheme()).isNotNull();
            assertThat(resolver.resolve(token, "TEMPLATE").scheme()).isNotNull();
        }
    }

    @Test void previouslyIssuedCheckPermissionCannotBypassCurrentDisabledConfiguration() {
        String token = prepare("import");
        assertThat(tokens.verify(token, "CHECK").allowedOperations()).contains("CHECK");
        // 签名证明旧入口曾经获准，运行时仍必须重新读取现在的开关。
        importOptions = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "create", false,
                1000, "DIRECT", true, true, 100, true, false, "STOP");

        assertThatThrownBy(() -> resolver.resolve(token, "CHECK")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未启用预校验");
    }

    @Test void listStandardAndSpecificAccessPermissionsAreRecheckedForEveryEntry() {
        doThrow(new ForbiddenException("无列表权限")).when(capabilities).requireStandardPermission("employee", EntityPermissionAction.LIST);
        assertThatThrownBy(() -> prepare("exportSelected")).isInstanceOf(ForbiddenException.class).hasMessageContaining("列表权限");
        assertThatThrownBy(() -> custom("EXPORT", "EXPLICIT", "SELECTED", "TOOLBAR_BUTTON_CLICK")).isInstanceOf(ForbiddenException.class);
        reset(capabilities);
        list.setAccessPermissionCode("employee:finance:list");
        Object original = ReflectionTestUtils.getField(PermissionUtil.class, "staticMenuMapper");
        var permissions = mock(SysMenuMapper.class);
        ReflectionTestUtils.setField(PermissionUtil.class, "staticMenuMapper", permissions);
        try {
            when(permissions.selectPermsByUserId("alice")).thenReturn(java.util.Set.of("employee:finance:list"));
            String token = prepare("exportSelected");
            when(permissions.selectPermsByUserId("alice")).thenReturn(java.util.Set.of());
            assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ForbiddenException.class).hasMessageContaining("来源列表");
        } finally { ReflectionTestUtils.setField(PermissionUtil.class, "staticMenuMapper", original); }
    }

    @Test void fixedEqualityOverridesClientOperatorAndTightensRangeBounds() {
        list.setFixedFilterConfig("{\"department\":\"finance\",\"amount_start\":100}");
        var request = new ExcelPrepareRequest("LIST", "list1", "exportAll", "employee", "employees", null, Map.of(), null, List.of(), Map.of("department", "engineering", "department_op", "NE", "amount_start", 200));
        String token = String.valueOf(resolver.prepare(request).get("actionContextToken"));
        var filters = resolver.filters(resolver.resolve(token, "EXPORT"));
        assertThat(filters).containsEntry("department", "finance").containsEntry("department_op", "EQ").containsEntry("amount_start", 200);
        var conflicting = resolver.resolve(token, "EXPORT");
        conflicting.claims().origin().setInput(Map.of("filters", Map.of("department", "engineering", "department_op", "EQ")));
        assertThatThrownBy(() -> resolver.filters(conflicting)).hasMessageContaining("固定条件冲突");
    }

    @Test void defaultNonSelectionButtonsCannotUseCheckedRowsToAlterTheirConditions() {
        String token = prepare("import");
        assertThat(tokens.verify(token, "IMPORT").origin().getSelectedIds()).isEmpty();
        verify(data, never()).findAccessibleById(anyString(), anyString(), anyString());
        verify(capabilities).requirePublishedListButton(eq("employee"), eq("import"), anyMap(), eq(List.of()));
    }

    @Test void selectionBoundAndEmptyIdsAreRejectedBeforeLoadingAnyRows() {
        var request = new ExcelPrepareRequest("LIST", "list1", "exportSelected", "employee", "employees", null, Map.of(), null, java.util.Collections.nCopies(5001, "record1"), Map.of());
        assertThatThrownBy(() -> resolver.prepare(request)).hasMessageContaining("5000");
        var blank = new ExcelPrepareRequest("LIST", "list1", "exportSelected", "employee", "employees", null, Map.of(), null, List.of(" "), Map.of());
        assertThatThrownBy(() -> resolver.prepare(blank)).hasMessageContaining("ID不能为空");
        verifyNoInteractions(data, configurations);
    }

    @Test void currentRecordRequiresViewPermissionAndMembershipInPublishedListFilters() {
        list.setFixedFilterConfig("{\"department\":\"finance\"}");
        var row = new EntityDataDTO(); row.setId("record1");
        when(lists.findExportBatchWithResolvedConfig(eq("employee"), eq("employees"), same(list), anyMap(), eq(List.of("record1")), isNull()))
                .thenReturn(new EntityExportBatch(List.of(row), null));
        String token = custom("EXPORT", "EXPLICIT", "CURRENT_RECORD", "ROW_BUTTON_CLICK");
        resolver.resolve(token, "EXPORT");
        verify(capabilities, times(2)).requireStandardPermission("employee", EntityPermissionAction.VIEW);
        verify(lists, times(2)).findExportBatchWithResolvedConfig(eq("employee"), eq("employees"), same(list),
                argThat(filters -> "finance".equals(filters.get("department")) && "EQ".equals(filters.get("department_op"))), eq(List.of("record1")), isNull());
        when(lists.findExportBatchWithResolvedConfig(any(), any(), any(), any(), any(), any())).thenReturn(new EntityExportBatch(List.of(), null));
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ForbiddenException.class).hasMessageContaining("筛选范围");
    }

    private String dialog(String scope) {
        var origin = new UiEventExecuteRequest(); origin.setConfigType("FORM"); origin.setConfigId("form1"); origin.setEntityCode("employee");
        origin.setTargetType("BUTTON"); origin.setTargetKey("custom-excel"); origin.setEventCode("FORM_BUTTON_CLICK");
        var chain = chain(List.of(Map.of("extensionId", "excel-extension", "strategy", "REPLACE")));
        when(events.authorizeExcelOrigin(any())).thenReturn(chain);
        try (var ignored = UiEventOriginContext.open(origin, chain)) {
            return String.valueOf(resolver.prepareCustom("excel-extension", Map.of("operation", "EXPORT",
                    "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "specific"), "dataScope", scope,
                    "listKey", "employees", "selectionSource", Map.of("type", "LIST_DIALOG"))).get("actionContextToken"));
        }
    }

    @Test void formDialogBindsPublishedListAndAcceptsOnlyAuthorizedSelection() {
        list.setFixedFilterConfig("{\"department\":\"finance\"}");
        list.setConfigHash("list-config-hash");
        String token = dialog("SELECTED");
        var plan = resolver.resolve(token, "EXPORT");
        assertThat(plan.list()).isSameAs(list);
        assertThat(plan.claims().selectionSource()).containsEntry("listId", "list1").containsEntry("configHash", "list-config-hash");
        assertThatThrownBy(() -> resolver.withExportSelection(plan, List.of(), Map.of())).hasMessageContaining("请选择");
        var selected = resolver.withExportSelection(plan, List.of("record1"), Map.of("department_op", "NE"));
        assertThat(selected.claims().origin().getSelectedIds()).containsExactly("record1");
        assertThat(resolver.filters(selected)).containsEntry("department", "finance").containsEntry("department_op", "EQ");
        doThrow(new ForbiddenException("记录不可访问")).when(data).findAccessibleById("employee", "other", "employees");
        assertThatThrownBy(() -> resolver.withExportSelection(plan, List.of("other"), Map.of())).isInstanceOf(ForbiddenException.class);
        list.setFixedFilterConfig("{\"department\":\"engineering\"}");
        assertThatThrownBy(() -> resolver.resolve(token, "EXPORT")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("列表配置已变化");
    }

    @Test void formDialogFilteredExportRejectsLegacyAndEventProviderSources() {
        list.setQueryInterfaceExtensionId("legacy-provider");
        assertThatThrownBy(() -> dialog("FILTERED")).hasMessageContaining("受控Excel导出适配器");
        list.setQueryInterfaceExtensionId(null);
        when(bindings.resolvePublished(any())).thenReturn(chain(List.of(Map.of("strategy", "REPLACE", "extensionId", "query-provider"))));
        assertThatThrownBy(() -> dialog("FILTERED")).hasMessageContaining("受控Excel导出适配器");
        // 选中范围仍要求真实实体记录存在且可访问，不借 Provider 任意记录作为导出输入。
        list.setQueryProviderCode("custom-provider");
        var plan = resolver.resolve(dialog("SELECTED"), "EXPORT");
        doThrow(new ForbiddenException("非实体记录")).when(data).findAccessibleById("employee", "virtual-row", "employees");
        assertThatThrownBy(() -> resolver.withExportSelection(plan, List.of("virtual-row"), Map.of())).isInstanceOf(ForbiddenException.class);
    }

    @Test void explicitFieldUnreadabilityPreventsIssuingExportOrTemplateToken() {
        EntityField field = new EntityField(); field.setFieldCode("privateValue"); field.setRuntimeReadable(false);
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(field));
        when(entities.getLatestPinnedByEntityId("entity1")).thenReturn(new EntityPublishedSnapshotService.PinnedEntitySnapshot(snapshot, "entityHash"));
        var column = new ExcelSchemeDocument.Column("value", "私有值", List.of(), "privateValue", true, true, true, 20, Map.of(), Map.of());
        var scheme = new ExcelResolvedScheme("entity1", "employee", "default", new ExcelSchemeDocument(1, "default", "方案", null,
                null, importOptions, null, List.of(column)), "schemeHash", "defaultHash", "write-form");
        when(configurations.resolvePublished(eq("employee"), anyString(), eq("DEFAULT"), isNull())).thenReturn(scheme);
        assertThatThrownBy(() -> prepare("exportSelected")).isInstanceOf(ForbiddenException.class).hasMessageContaining("不可读取字段");
        assertThatThrownBy(() -> prepare("import")).isInstanceOf(ForbiddenException.class).hasMessageContaining("不可读取字段");
    }
}
