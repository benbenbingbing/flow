package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.*;
import com.workflow.entity.form.application.validation.PublishedFormCrossFieldMutationValidator;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.mutation.application.*;
import com.workflow.entity.permission.application.*;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.*;
import com.workflow.entity.version.application.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实代理及数据库事务覆盖导入行→标准变更管道，验证终检失败不会留下前一行。 */
class ExcelRowMutationPipelineTest {
    private final EntityActionCapabilityService permissions = mock(EntityActionCapabilityService.class);
    private final PublishedFormSubmissionService submissions = mock(PublishedFormSubmissionService.class);
    private final UiEventRuntimeService events = mock(UiEventRuntimeService.class);
    private final EntityFormActionService formActions = mock(EntityFormActionService.class);
    private final EntityListPublishedRuntimeService lists = mock(EntityListPublishedRuntimeService.class);
    private final EntityFormUniqueClaimService unique = mock(EntityFormUniqueClaimService.class);
    private final EntityAggregateWriter writer = mock(EntityAggregateWriter.class);
    private final PublishedFormCrossFieldMutationValidator crossFields = mock(PublishedFormCrossFieldMutationValidator.class);
    private EntityDataActionService service;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:row_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE records(id VARCHAR(100) PRIMARY KEY, name VARCHAR(100))");
        var manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        var definitions = mock(EntityDefinitionMapper.class);
        var definition = new EntityDefinition(); definition.setId("entity"); definition.setEntityCode("employee");
        when(definitions.findByEntityCode("employee")).thenReturn(Optional.of(definition));
        when(definitions.selectById("entity")).thenReturn(definition);
        var forms = mock(EntityFormMapper.class);
        var form = new EntityForm(); form.setId("write-form"); form.setEntityId("entity");
        when(forms.selectById("write-form")).thenReturn(form);
        when(submissions.applyAuthorizedFormWithRelease(eq("write-form"), eq("hash"), eq(Map.of()), eq("employee"), isNull(), eq("create"), anyMap(), any())).thenAnswer(call -> {
            assertThat(ExcelAtomicExecutionContext.active()).isTrue();
            return new PublishedFormSubmissionService.AuthorizedFormApplication(Map.of("name", "表单默认姓名", "amount", 7),
                    "hash", 1, "hash", "hash", null);
        });
        when(events.execute(any(), any())).thenAnswer(call -> executeDefault(call.getArgument(0), call.getArgument(1)));
        var dynamic = mock(EntityDataDynamicService.class);
        doAnswer(call -> {
            EntityMutationCommand command = call.getArgument(0);
            String id = command.context().businessTraceKey();
            jdbc.update("INSERT INTO records VALUES (?,?)", id, command.payload().get("name"));
            return new EntityAggregateWriter.WriteResult(id, null);
        }).when(writer).apply(any(), any());
        when(dynamic.findById(eq("employee"), anyString())).thenAnswer(call -> {
            String id = call.getArgument(1);
            var dto = new EntityDataDTO(); dto.setId(id); dto.setEntityCode("employee");
            dto.setName(jdbc.queryForObject("SELECT name FROM records WHERE id=?", String.class, id));
            return dto;
        });
        when(unique.prepare(any(), anyMap())).thenReturn(mock(EntityFormUniqueClaimService.PreparedUniqueClaims.class));
        var related = mock(EntityRelatedVersionCaptureService.class);
        when(related.lockRelatedRoots(any(), anyMap())).thenReturn(Set.of());
        var mapper = new ObjectMapper().findAndRegisterModules();
        var executor = new EntityMutationTransactionExecutor(writer, dynamic, mock(EntityVersionPolicyMatcher.class),
                mock(EntityRecordVersionService.class), related, mock(EntityMutationReceiptService.class), unique, crossFields, mapper);
        var pipeline = new EntityMutationPipeline(proxy(executor, manager), mock(SystemAuditPort.class));
        service = proxy(new EntityDataActionService(mock(UiConfigReleaseService.class), dynamic, pipeline,
                mock(EntityListActionConfigService.class), lists, permissions, mock(EntityListScopeAuditService.class),
                formActions, submissions, mock(FormSubmissionTraceService.class), events, mock(SystemEntityReadService.class),
                definitions, forms, mapper, mock(UiViewCompositionActionService.class)), manager);
    }

    @Test void cannotStartAnIndependentRowTransaction() {
        assertThatThrownBy(() -> service.createExcelRow(dto(), "excel_1")).isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(permissions, writer);
    }

    @Test void usesPublishedFormDefaultsAndStandardUniquenessWithoutListCreateButton() {
        var saved = transaction.execute(status -> service.createExcelRow(dto(), "excel_1"));
        assertThat(saved.getName()).isEqualTo("表单默认姓名");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM records", Integer.class)).isEqualTo(1);
        verify(permissions).requireStandardPermission("employee", EntityPermissionAction.IMPORT);
        verify(permissions).requireStandardPermission("employee", EntityPermissionAction.CREATE);
        verify(permissions, never()).requireToolbarActionForConfig(any(), any(), any());
        verifyNoInteractions(lists);
        verify(formActions, times(2)).requireBuiltInMutationAction(any(), eq("save"));
        verify(unique).verifyPrepared(any(), anyMap(), any());
        verify(unique).reconcile(any(), anyMap(), any());
        verify(crossFields).validate(any(), anyMap());
        assertThat(ExcelAtomicExecutionContext.active()).isFalse();
    }

    @Test void secondRowUniqueFailureRollsBackFirstRowThroughActualMutationExecutor() {
        doNothing().doThrow(new IllegalArgumentException("唯一性冲突")).when(unique).verifyPrepared(any(), anyMap(), any());
        assertThatThrownBy(() -> transaction.execute(status -> {
            service.createExcelRow(dto(), "excel_1");
            return service.createExcelRow(dto(), "excel_2");
        })).hasMessageContaining("唯一性");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM records", Integer.class)).isZero();
        assertThat(ExcelAtomicExecutionContext.active()).isFalse();
    }

    /** 写入表单外的普通字段必须随 data 到达统一存储边界，不能在 DTO 或事件转换中被丢弃。 */
    @Test void preservesDirectColumnThroughEventFormAndMutationCommand() {
        usePassThroughForm();
        var request = dto();
        request.setData(Map.of("name", "张三", "serial", "EXCEL-001"));

        transaction.execute(status -> service.createExcelRow(request, "excel_direct"));

        var command = ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(writer).apply(command.capture(), any());
        assertThat(((Map<?, ?>) command.getValue().payload().get("data")).get("serial"))
                .isEqualTo("EXCEL-001");
        verify(unique).verifyPrepared(any(), anyMap(), any());
        verify(crossFields).validate(any(), anyMap());
    }

    /** DIRECT 不扩大为绕过实体约束；以真实实体校验器守住存储边界，第二行冲突仍回滚第一行。 */
    @Test void directColumnStillObeysEntityRequiredAndUniqueConstraints() {
        usePassThroughForm();
        var field = new EntityField();
        field.setFieldCode("serial"); field.setFieldName("流水号");
        field.setFieldType(EntityField.FieldType.STRING);
        field.setIsRequired(true); field.setIsUnique(true);
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(field));
        var snapshots = mock(EntityPublishedSnapshotService.class);
        when(snapshots.getLatestByEntityCode("employee")).thenReturn(snapshot);
        var tables = mock(DynamicTableService.class);
        when(tables.getTableName("employee")).thenReturn("employee");
        var records = mock(EntityDataDynamicMapper.class);
        when(records.countByCondition(eq("employee"), anyMap())).thenAnswer(call ->
                "DUPLICATE".equals(((Map<?, ?>) call.getArgument(1)).get("serial")) ? 1L : 0L);
        var json = new ObjectMapper().findAndRegisterModules();
        var validator = new EntityDataMutationValidator(records, tables, snapshots,
                new EntityRuntimeRecordMapper(json), new EntityFieldValidationRuleService(json), json);
        // SQL 写入是夹具，字段校验和包住整批的标准 mutation 事务使用真实实现。
        doAnswer(call -> {
            EntityMutationCommand command = call.getArgument(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) command.payload().get("data");
            validator.validatePublishedFields("employee", data, null);
            String id = command.context().businessTraceKey();
            jdbc.update("INSERT INTO records VALUES (?,?)", id, command.payload().get("name"));
            return new EntityAggregateWriter.WriteResult(id, null);
        }).when(writer).apply(any(), any());
        var missing = dto(); missing.setData(Map.of("name", "张三"));
        assertThatThrownBy(() -> transaction.execute(status -> service.createExcelRow(missing, "excel_missing")))
                .hasMessageContaining("字段必填").hasMessageContaining("流水号");

        assertThatThrownBy(() -> transaction.execute(status -> {
            var valid = dto(); valid.setData(Map.of("name", "张三", "serial", "EXCEL-001"));
            service.createExcelRow(valid, "excel_valid");
            var duplicate = dto(); duplicate.setData(Map.of("name", "李四", "serial", "DUPLICATE"));
            return service.createExcelRow(duplicate, "excel_duplicate");
        })).hasMessageContaining("字段值已存在").hasMessageContaining("流水号");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM records", Integer.class)).isZero();
        assertThat(ExcelAtomicExecutionContext.active()).isFalse();
    }

    @Test void rejectsMissingFixedFormAndSuppliedPrimaryKey() {
        var missing = dto(); missing.setFormConfigHash(null);
        assertThatThrownBy(() -> transaction.execute(s -> service.createExcelRow(missing, "excel_1"))).hasMessageContaining("表单上下文");
        var keyed = dto(); keyed.setId("existing");
        assertThatThrownBy(() -> transaction.execute(s -> service.createExcelRow(keyed, "excel_2"))).hasMessageContaining("仅允许新增");
        verifyNoInteractions(writer);
    }

    @Test void eventCannotStartProcessOrChangeRecordIdentity() {
        for (Map<String, Object> patch : List.of(Map.<String, Object>of("startProcess", true), Map.<String, Object>of("entityCode", "other"),
                Map.<String, Object>of("id", "existing"), Map.<String, Object>of("data", Map.of("id", "existing")))) {
            doAnswer(call -> {
                UiEventExecuteRequest request = call.getArgument(0);
                var input = new LinkedHashMap<>(request.getInput()); input.putAll(patch);
                Function<Map<String, Object>, Object> handler = call.getArgument(1);
                handler.apply(input); return null;
            }).when(events).execute(any(), any());
            assertThatThrownBy(() -> transaction.execute(s -> service.createExcelRow(dto(), "excel_1"))).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(writer);
    }

    @Test void replacementWithoutPlatformSaveCannotClaimSuccess() {
        doAnswer(call -> {
            var result = new UiEventExecutionResult(); result.setReplaced(true); result.setData(Map.of("id", "fake")); return result;
        }).when(events).execute(any(), any());
        assertThatThrownBy(() -> transaction.execute(s -> service.createExcelRow(dto(), "excel_1"))).hasMessageContaining("真实保存结果");
        verifyNoInteractions(writer);
    }

    @Test void afterReplacementRollsBackAlreadyWrittenRow() {
        doAnswer(call -> {
            var result = executeDefault(call.getArgument(0), call.getArgument(1));
            result.setReplaced(true); result.setData(Map.of("id", "fake", "entityCode", "other")); return result;
        }).when(events).execute(any(), any());
        assertThatThrownBy(() -> transaction.execute(s -> service.createExcelRow(dto(), "excel_1"))).hasMessageContaining("真实保存结果");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM records", Integer.class)).isZero();
    }

    private UiEventExecutionResult executeDefault(UiEventExecuteRequest request, Function<Map<String, Object>, Object> handler) {
        var result = new UiEventExecutionResult(); result.setData(handler.apply(request.getInput())); result.setDefaultExecuted(true); return result;
    }

    private EntityDataDTO dto() {
        var dto = new EntityDataDTO(); dto.setEntityCode("employee"); dto.setFormId("write-form"); dto.setFormConfigHash("hash"); dto.setData(Map.of("amount", 1));
        dto.setListKey("disabled-create-list"); return dto;
    }

    private void usePassThroughForm() {
        doAnswer(call ->
                new PublishedFormSubmissionService.AuthorizedFormApplication(call.getArgument(6),
                        "hash", 1, "hash", "hash", null))
                .when(submissions).applyAuthorizedFormWithRelease(eq("write-form"), eq("hash"), eq(Map.of()),
                        eq("employee"), isNull(), eq("create"), anyMap(), any());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, DataSourceTransactionManager manager) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
