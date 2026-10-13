package com.workflow.entity.excel.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.config.ExcelSchemeValidator;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 最终迁移计划只替代包覆盖的方案；普通发布和未覆盖方案仍保持完整保护。 */
class EntityExcelMigrationCompatibilityTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ExcelConfigurationRepository repository = mock(ExcelConfigurationRepository.class);
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityFormMapper forms = mock(EntityFormMapper.class);
    private final EntityUiConfigurationPolicy policy = mock(EntityUiConfigurationPolicy.class);
    private final EntityExcelConfigurationService service = new EntityExcelConfigurationService(repository, entities,
            mock(EntityPublishedSnapshotService.class), forms, mock(EntityFormPublishedMapper.class), mock(CurrentUserRoleService.class),
            policy, mock(SystemEntityFieldPolicy.class), new ExcelSchemeValidator(), json);
    @AfterEach void clearTransaction() { TransactionSynchronizationManager.clear(); }

    @Test void fieldsUseFinalCoveredSchemesAndStillRejectUncoveredPublishedScheme() throws Exception {
        entity();
        var old = row("main", "A", doc("main", "old", null));
        when(repository.schemes("e")).thenReturn(List.of(old));
        var field = new EntityField(); field.setFieldCode("new"); field.setFieldType(EntityField.FieldType.STRING);
        assertThrows(IllegalArgumentException.class, () -> service.requireCompatibleFields("employee", List.of(field)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var plan = new EntityExcelMigrationScope.Plan(Map.of("main", new EntityExcelMigrationScope.Scheme("ACTIVE", doc("main", "new", null))), false, null, null);
        try (var ignored = EntityExcelMigrationScope.open(Map.of("employee", plan))) {
            assertDoesNotThrow(() -> service.requireCompatibleFields("employee", List.of(field)));
            when(repository.schemes("e")).thenReturn(List.of(old, row("outside", "A", doc("outside", "old", null))));
            assertThrows(IllegalArgumentException.class, () -> service.requireCompatibleFields("employee", List.of(field)));
        }
    }

    @Test void replacingWriteFormDoesNotBypassUncoveredReferences() throws Exception {
        entity(); var form = new EntityForm(); form.setId("A"); form.setEntityId("e"); form.setFormKey("formA");
        when(forms.selectById("A")).thenReturn(form); when(repository.publishedSchemeUsesForm("A")).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.requireFormUnreferenced("A"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var planned = new EntityExcelMigrationScope.Plan(Map.of("main", new EntityExcelMigrationScope.Scheme("ACTIVE", doc("main", "name", "formB"))), false, null, null);
        when(repository.schemes("e")).thenReturn(List.of(row("main", "A", doc("main", "name", "formA"))));
        try (var ignored = EntityExcelMigrationScope.open(Map.of("employee", planned))) {
            assertDoesNotThrow(() -> service.requireFormUnreferenced("A"));
            when(repository.schemes("e")).thenReturn(List.of(row("outside", "A", doc("outside", "name", "formA"))));
            assertThrows(RuntimeException.class, () -> service.requireFormUnreferenced("A"));
        }
        assertThrows(RuntimeException.class, () -> service.requireFormUnreferenced("A"));
    }

    private void entity() {
        var entity = new EntityDefinition(); entity.setId("e"); entity.setEntityCode("employee"); entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entities.selectById("e")).thenReturn(entity); when(policy.requireConfigurableByCode("employee")).thenReturn(entity);
    }
    private ExcelConfigurationRepository.SchemeRow row(String code, String formId, ExcelSchemeDocument doc) throws Exception {
        var row = new ExcelConfigurationRepository.SchemeRow(); row.setSchemeCode(code); row.setStatus("ACTIVE"); row.setWriteFormId(formId); row.setDocumentJson(json.writeValueAsString(doc)); return row;
    }
    private ExcelSchemeDocument doc(String code, String field, String form) {
        return new ExcelSchemeDocument(1, code, "员工", null, new ExcelSchemeDocument.FileConfig("员工", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(form != null, "CREATE_ONLY", "ATOMIC_ALL", form, false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, "员工", "员工"),
                List.of(new ExcelSchemeDocument.Column("value", "值", List.of(), field, form != null, true, true, 20, Map.of("type", "STRING"), Map.of())));
    }
}
