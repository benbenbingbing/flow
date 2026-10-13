package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.Snapshot;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用生产配置仓库和真实 Spring 事务验证方案与默认指针同时提交/回滚，避免只验证方法调用。 */
class EntityExcelInlineDefaultsTransactionTest {
    private final ObjectMapper json = new ObjectMapper();
    private ExcelConfigurationRepository repository;
    private EntityExcelConfigurationService service;
    private EntityFormPublishedMapper publishedForms;

    @BeforeEach
    void setup() {
        var database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:excel_defaults_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        var migration = Path.of("../workflow-db-migrator/src/main/resources/db/migration/V115__entity_excel_configuration_and_jobs.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(database);
        repository = spy(new ExcelConfigurationRepository(new JdbcTemplate(database)));
        // 本测试聚焦默认发布事务；已有的表单/列表引用快照在需要的用例中单独提供。
        doReturn(List.of()).when(repository).snapshots("entity1");

        var entity = ExcelSchemeValidatorTest.entity();
        var policy = mock(EntityUiConfigurationPolicy.class);
        when(policy.requireConfigurableByCode("employee")).thenReturn(entity);
        var entities = mock(EntityDefinitionMapper.class);
        when(entities.findByEntityCodeForUpdate("employee")).thenReturn(Optional.of(entity));
        var snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of(ExcelSchemeValidatorTest.field("name", EntityField.FieldType.STRING)));
        var snapshots = mock(EntityPublishedSnapshotService.class);
        when(snapshots.findLatestByEntityCode("employee")).thenReturn(snapshot);
        var forms = mock(EntityFormMapper.class);
        var form = new EntityForm(); form.setId("form1"); form.setEntityId("entity1"); form.setStatus(1);
        when(forms.selectByEntityIdAndFormKey("entity1", "write")).thenReturn(form);
        publishedForms = mock(EntityFormPublishedMapper.class);
        var published = new EntityFormPublished(); published.setFormId("form1"); published.setEntityId("entity1");
        published.setSnapshotDocument("{\"form\":{\"status\":1}}");
        when(publishedForms.selectById("form1")).thenReturn(published);

        var target = new EntityExcelConfigurationService(repository, entities, snapshots, forms, publishedForms,
                mock(CurrentUserRoleService.class), policy, new SystemEntityFieldPolicy(), new ExcelSchemeValidator(), json);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource()));
        service = (EntityExcelConfigurationService) factory.getProxy();
    }

    @Test
    void defaultSelectionIsVisibleInDraftButOnlyTakesEffectWithSchemePublication() {
        var draft = save("first", false, true);
        assertThat(draft.draftDefaultExport()).isTrue();
        assertThat(draft.defaultExport()).isFalse();
        assertThatThrownBy(() -> service.resolvePublished("employee", "EXPORT", "DEFAULT", null)).hasMessageContaining("默认导出");

        var published = publish(draft);
        assertThat(published.defaultExport()).isTrue();
        assertThat(service.resolvePublished("employee", "EXPORT", "DEFAULT", null).schemeCode()).isEqualTo("first");
        assertThat(service.list("employee")).singleElement().satisfies(row -> {
            assertThat(row.defaultExport()).isTrue();
            assertThat(row.draftDefaultExport()).isTrue();
            assertThat(row.settingsRevision()).isEqualTo(published.settingsRevision());
        });
    }

    @Test
    void publishingReplacementSwitchesOnlyThatDirectionAndKeepsExactlyOneDefault() {
        publish(save("both", true, true));
        var replacement = save("outgoing", false, true);
        assertThat(service.get("employee", "both").draftDefaultExport()).isFalse();
        assertThat(service.resolvePublished("employee", "EXPORT", "DEFAULT", null).schemeCode()).isEqualTo("both");
        publish(replacement);
        assertThat(service.resolvePublished("employee", "IMPORT", "DEFAULT", null).schemeCode()).isEqualTo("both");
        assertThat(service.resolvePublished("employee", "TEMPLATE", "DEFAULT", null).schemeCode()).isEqualTo("both");
        assertThat(service.resolvePublished("employee", "EXPORT", "DEFAULT", null).schemeCode()).isEqualTo("outgoing");
        assertThat(service.list("employee").stream().filter(SchemeView::defaultExport)).hasSize(1);
    }

    @Test
    void cancellingDefaultKeepsPublishedDefaultUntilTheSchemeIsPublished() {
        publish(save("basic", true, true));
        String importHash = service.settings("employee").importHash();
        var edited = save("basic", true, false);
        assertThat(edited.draftDefaultExport()).isFalse();
        assertThat(edited.defaultExport()).isTrue();
        publish(edited);
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, "basic", null));
        assertThat(service.settings("employee").importHash()).isEqualTo(importHash);
        assertThatThrownBy(() -> service.resolvePublished("employee", "EXPORT", "DEFAULT", null)).hasMessageContaining("默认导出");
    }

    @Test
    void unselectedOtherSchemeCannotClearExistingDefaults() {
        publish(save("existing", true, true));
        publish(save("other", false, false));
        assertThat(service.settings("employee").draftConfig()).isEqualTo(new Defaults(1, "existing", "existing"));
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, "existing", "existing"));
    }

    @Test
    void publicationDoesNotPublishAnotherSchemesPendingDefault() {
        publish(save("current", true, true));
        save("pending", true, false);
        // 导入默认草稿已属于 pending，但其内容尚未发布；发布 current 只能取消自身导入默认。
        publish(service.get("employee", "current"));
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, null, "current"));
        assertThat(service.settings("employee").draftConfig()).isEqualTo(new Defaults(1, "pending", "current"));
        assertThat(service.get("employee", "pending").publishedConfig()).isNull();
    }

    @Test
    void oldSaveAndPublishRequestsDoNotChangeDefaultsOrPendingSelections() {
        publish(save("current", false, true));
        save("pending", false, true);
        var current = service.get("employee", "current");
        var saved = service.save("employee", new SaveScheme(current.id(), current.revision(), current.schemeCode(), current.name(), current.draftConfig()));
        service.publish("employee", "current", saved.revision());
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, null, "current"));
        assertThat(service.settings("employee").draftConfig()).isEqualTo(new Defaults(1, null, "pending"));
    }

    @Test
    void legacyPublishedDefaultAppearsCheckedInTheNewSchemeEditor() {
        var doc = ExcelSchemeValidatorTest.valid("legacy", false);
        var saved = service.save("employee", new SaveScheme(null, 0, "legacy", "legacy", doc));
        service.publish("employee", "legacy", saved.revision());
        var defaults = service.saveSettings("employee", new SaveSettings(0, null, "legacy"));
        service.publishSettings("employee", defaults.revision());

        var editable = service.get("employee", "legacy");
        assertThat(editable.defaultExport()).isTrue();
        assertThat(editable.draftDefaultExport()).isTrue();
        assertThat(editable.draftDefaultImport()).isFalse();
        assertThat(editable.settingsRevision()).isEqualTo(service.settings("employee").revision());
    }

    @Test
    void staleSettingsRevisionRejectsSaveAndPublishWithoutPartialChanges() {
        var stale = publish(save("current", false, true));
        save("replacement", false, true);
        var before = service.get("employee", "current");
        assertThatThrownBy(() -> service.save("employee", new SaveScheme(stale.id(), stale.revision(), "current", "current",
                stale.draftConfig(), false, true, stale.settingsRevision()))).isInstanceOf(RevisionConflictException.class);
        assertThatThrownBy(() -> publish(stale)).isInstanceOf(RevisionConflictException.class);
        assertThat(service.get("employee", "current")).isEqualTo(before);
        assertThat(service.settings("employee").draftConfig().defaultExportSchemeCode()).isEqualTo("replacement");
    }

    @Test
    void settingDisabledDirectionAsDefaultRejectsBeforeSavingScheme() {
        var doc = ExcelSchemeValidatorTest.valid("invalid", false);
        assertThatThrownBy(() -> service.save("employee", new SaveScheme(null, 0, "invalid", "invalid", doc, true, false, 0)))
                .hasMessageContaining("请先启用导入");
        assertThat(repository.scheme("entity1", "invalid")).isNull();
        assertThat(repository.settings("entity1")).isNull();
    }

    @Test
    void cancellingDefaultAllowsDisablingDirectionButExplicitReferencesStillPreventIt() throws Exception {
        publish(save("basic", true, true));
        var previous = service.get("employee", "basic");
        var disabledImport = ExcelSchemeValidatorTest.valid("basic", false);
        var saved = service.save("employee", new SaveScheme(previous.id(), previous.revision(), "basic", "basic",
                disabledImport, false, true, previous.settingsRevision()));
        String explicit = "{\"operation\":\"IMPORT\",\"schemeRef\":{\"mode\":\"EXPLICIT\",\"schemeCode\":\"basic\"}}";
        doReturn(List.of(new Snapshot("FORM", "form1", explicit, true))).when(repository).snapshots("entity1");
        assertThatThrownBy(() -> publish(saved)).hasMessageContaining("不能关闭对应能力");
        assertThat(service.resolvePublished("employee", "IMPORT", "DEFAULT", null).schemeCode()).isEqualTo("basic");

        String followDefault = "{\"operation\":\"IMPORT\",\"schemeRef\":{\"mode\":\"DEFAULT\"}}";
        doReturn(List.of(new Snapshot("FORM", "form1", followDefault, true))).when(repository).snapshots("entity1");
        var published = publish(saved);
        assertThat(published.publishedConfig().importConfig().enabled()).isFalse();
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, null, "basic"));
    }

    @Test
    void invalidWriteFormPreventsBothSchemeAndDefaultPublication() {
        publish(save("existing", false, true));
        var pending = save("invalid", true, true);
        when(publishedForms.selectById("form1")).thenReturn(null);
        assertThatThrownBy(() -> publish(pending)).hasMessageContaining("写入表单尚未发布");
        assertThat(service.get("employee", "invalid").publishedConfig()).isNull();
        assertThat(service.settings("employee").publishedConfig()).isEqualTo(new Defaults(1, null, "existing"));
    }

    @Test
    void databaseFailureAfterDefaultPublicationRollsBackSchemeContentAndBothPointers() {
        publish(save("existing", false, true));
        var pending = save("replacement", true, true);
        var before = service.settings("employee");
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("simulate settings write failure");
        }).when(repository).publishSettings(anyString(), anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> publish(pending)).hasMessage("simulate settings write failure");
        assertThat(service.settings("employee")).isEqualTo(before);
        assertThat(service.get("employee", "replacement")).isEqualTo(pending);
    }

    @Test
    void databaseFailureSavingDefaultRollsBackNewSchemeAndDefaultDraft() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("simulate draft write failure");
        }).when(repository).saveSettings(anyString(), anyString(), anyBoolean());
        assertThatThrownBy(() -> save("new", false, true)).hasMessage("simulate draft write failure");
        assertThat(repository.scheme("entity1", "new")).isNull();
        assertThat(repository.settings("entity1")).isNull();
    }

    /** 每次以最新实体默认修订号模拟设计器打开后保存；方向未勾选不意味着禁用该能力。 */
    private SchemeView save(String code, boolean defaultImport, boolean defaultExport) {
        var existing = repository.scheme("entity1", code);
        var doc = ExcelSchemeValidatorTest.valid(code, true);
        return service.save("employee", new SaveScheme(existing == null ? null : existing.getId(),
                existing == null ? 0 : existing.getRevision(), code, code, doc,
                defaultImport, defaultExport, service.settings("employee").revision()));
    }

    private SchemeView publish(SchemeView draft) {
        return service.publish("employee", draft.schemeCode(), draft.revision(), draft.settingsRevision());
    }
}
