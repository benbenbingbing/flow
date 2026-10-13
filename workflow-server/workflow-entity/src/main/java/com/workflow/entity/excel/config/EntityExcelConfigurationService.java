package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.model.ExcelEventAction;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.SchemeRow;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.SettingsRow;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.contracts.migration.port.ExcelConfigurationPublicationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 实体 Excel 配置的唯一发布入口。草稿保存、默认指针切换和方案停用均锁同一实体行。 */
@Service
@RequiredArgsConstructor
public class EntityExcelConfigurationService {
    private final ExcelConfigurationRepository repository;
    private final EntityDefinitionMapper entityMapper;
    private final EntityPublishedSnapshotService snapshots;
    private final EntityFormMapper formMapper;
    private final EntityFormPublishedMapper publishedForms;
    private final CurrentUserRoleService roles;
    private final EntityUiConfigurationPolicy uiPolicy;
    private final SystemEntityFieldPolicy fieldPolicy;
    private final ExcelSchemeValidator validator;
    private final ObjectMapper json;
    @Autowired
    private ObjectProvider<UiEventBindingService> eventBindings;
    @Autowired
    private ObjectProvider<ExcelConfigurationPublicationPort> migrationPublications;

    @Transactional(readOnly = true)
    public List<SchemeView> list(String entityCode) {
        EntityDefinition entity = management(entityCode, false);
        SettingsRow settings = repository.settings(entity.getId());
        return repository.schemes(entity.getId()).stream().map(row -> view(row, settings)).toList();
    }

    @Transactional(readOnly = true)
    public SchemeView get(String entityCode, String schemeCode) {
        EntityDefinition entity = management(entityCode, false);
        return view(requireScheme(entity.getId(), schemeCode), repository.settings(entity.getId()));
    }

    /** 更新必须带修订号和稳定 ID；默认勾选与方案草稿同事务保存，发布前不会改变运行入口。 */
    @Transactional(rollbackFor = Exception.class)
    public SchemeView save(String entityCode, SaveScheme request) {
        EntityDefinition entity = management(entityCode, true);
        ExcelSchemeDocument doc = request.config();
        validator.validateDraft(doc);
        SettingsRow settings = repository.settings(entity.getId());
        Defaults nextDefaults = defaultsForSave(settings, doc, request);
        SchemeRow row = repository.scheme(entity.getId(), doc.schemeCode());
        if (row == null) {
            if (request.id() != null && !request.id().isBlank()) throw new IllegalArgumentException("方案不存在，不能修改已有方案编码");
            revision(request.expectedRevision(), 0);
            row = new SchemeRow();
            row.setId(UUID.randomUUID().toString());
            row.setEntityId(entity.getId());
            row.setSchemeCode(doc.schemeCode());
            row.setName(doc.name());
            row.setDraftJson(write(doc));
            repository.insertScheme(row);
        } else {
            if (!row.getId().equals(request.id())) throw conflict("方案编码已存在，更新时必须传入对应ID");
            revision(request.expectedRevision(), row.getRevision());
            row.setName(doc.name());
            row.setDraftJson(write(doc));
            repository.updateScheme(row);
        }
        if (nextDefaults != null && !nextDefaults.equals(draftDefaults(settings))) {
            repository.saveSettings(entity.getId(), write(nextDefaults), settings != null);
        }
        return view(requireScheme(entity.getId(), doc.schemeCode()), repository.settings(entity.getId()));
    }

    /** 兼容迁移及旧管理接口：未携带默认草稿修订号时只发布方案，不改变默认入口。 */
    @Transactional(rollbackFor = Exception.class)
    public SchemeView publish(String entityCode, String schemeCode, Integer expectedRevision) {
        return publish(entityCode, schemeCode, expectedRevision, null);
    }

    /**
     * 发布当前方案及其默认勾选，所有校验和写入共用实体锁与事务。
     * 每方向最多一个默认；其他方案尚未发布的默认草稿不会随本次发布一起生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public SchemeView publish(String entityCode, String schemeCode, Integer expectedRevision, Integer expectedSettingsRevision) {
        EntityDefinition entity = management(entityCode, true);
        SchemeRow row = requireScheme(entity.getId(), schemeCode);
        revision(expectedRevision, row.getRevision());
        SettingsRow settings = repository.settings(entity.getId());
        if (expectedSettingsRevision != null) revision(expectedSettingsRevision, settings == null ? 0 : settings.getRevision());
        if (!"ACTIVE".equals(row.getStatus())) throw conflict("请先启用方案再发布");
        ExcelSchemeDocument doc = read(row.getDraftJson(), ExcelSchemeDocument.class);
        String formId = validatePublished(entity, doc);
        Defaults currentDefaults = publishedDefaults(settings);
        Defaults nextDefaults = expectedSettingsRevision == null ? currentDefaults : defaultsForPublish(schemeCode, doc, settings);
        for (Reference reference : referencesInternal(entity.getId(), schemeCode)) {
            // 跟随默认的按钮在取消默认后按运行可用性禁用；显式绑定仍必须保护原方向能力。
            boolean stillDefault = schemeCode.equals("EXPORT".equals(reference.operation())
                    ? nextDefaults.defaultExportSchemeCode() : nextDefaults.defaultImportSchemeCode());
            if (reference.published() && (!"DEFAULT".equals(reference.mode()) || stillDefault)
                    && !supports(doc, reference.operation())) throw conflict("方案被 " + reference.ownerType() + " 引用，不能关闭对应能力");
        }
        String document = write(doc);
        repository.publish(row, document, hash(document + "\n" + Objects.toString(formId, "")), formId, UserContext.getUserId());
        if (!nextDefaults.equals(currentDefaults)) {
            repository.publishSettings(entity.getId(), write(nextDefaults),
                    directionHash(entity.getId(), "IMPORT", nextDefaults.defaultImportSchemeCode()),
                    directionHash(entity.getId(), "EXPORT", nextDefaults.defaultExportSchemeCode()), UserContext.getUserId());
        }
        recordMigrationPublication(entity.getId());
        return view(requireScheme(entity.getId(), schemeCode), repository.settings(entity.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public SchemeView status(String entityCode, String schemeCode, StatusRequest request) {
        EntityDefinition entity = management(entityCode, true);
        SchemeRow row = requireScheme(entity.getId(), schemeCode);
        revision(request.expectedRevision(), row.getRevision());
        if (request.status() == null || !Set.of("ACTIVE", "DISABLED").contains(request.status())) throw new IllegalArgumentException("状态只能为 ACTIVE 或 DISABLED");
        if ("DISABLED".equals(request.status())) requireUnreferenced(entity.getId(), schemeCode);
        else if (row.getDocumentJson() != null) {
            String formId = validatePublished(entity, read(row.getDocumentJson(), ExcelSchemeDocument.class));
            // 停用方案可随迁移保留历史文档，此时写入表单可能尚不存在或已按同一formKey重建。
            // 启用在完整发布校验通过后重绑当前表单及摘要；只恢复原已发布文档，不顺带发布草稿。
            if (!Objects.equals(formId, row.getWriteFormId())) {
                repository.rebindPublishedForm(row.getId(), formId,
                        hash(row.getDocumentJson() + "\n" + Objects.toString(formId, "")), UserContext.getUserId());
            }
        }
        repository.status(row.getId(), request.status());
        recordMigrationPublication(entity.getId());
        return view(requireScheme(entity.getId(), schemeCode), repository.settings(entity.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String entityCode, String schemeCode, Integer expectedRevision) {
        EntityDefinition entity = management(entityCode, true);
        SchemeRow row = requireScheme(entity.getId(), schemeCode);
        revision(expectedRevision, row.getRevision());
        requireUnreferenced(entity.getId(), schemeCode);
        repository.delete(row.getId());
        recordMigrationPublication(entity.getId());
    }

    @Transactional(readOnly = true)
    public SettingsView settings(String entityCode) {
        return settingsView(repository.settings(management(entityCode, false).getId()));
    }

    /** 兼容独立默认设置接口：草稿允许指向未发布方案，运行入口在发布设置或对应方案时切换。 */
    @Transactional(rollbackFor = Exception.class)
    public SettingsView saveSettings(String entityCode, SaveSettings request) {
        EntityDefinition entity = management(entityCode, true);
        SettingsRow row = repository.settings(entity.getId());
        revision(request.expectedRevision(), row == null ? 0 : row.getRevision());
        Defaults defaults = new Defaults(1, normalized(request.defaultImportSchemeCode()), normalized(request.defaultExportSchemeCode()));
        for (String code : new String[]{defaults.defaultImportSchemeCode(), defaults.defaultExportSchemeCode()}) {
            if (code != null) requireScheme(entity.getId(), code);
        }
        repository.saveSettings(entity.getId(), write(defaults), row != null);
        return settingsView(repository.settings(entity.getId()));
    }

    /** 方向摘要仅包含实体、操作方向和稳定方案指针，切换默认导入不会使导出页面过期。 */
    @Transactional(rollbackFor = Exception.class)
    public SettingsView publishSettings(String entityCode, Integer expectedRevision) {
        EntityDefinition entity = management(entityCode, true);
        SettingsRow row = repository.settings(entity.getId());
        if (row == null) throw new IllegalArgumentException("请先保存默认设置草稿");
        revision(expectedRevision, row.getRevision());
        Defaults defaults = read(row.getDraftJson(), Defaults.class);
        if (defaults.defaultImportSchemeCode() != null) published(entity, defaults.defaultImportSchemeCode(), "IMPORT");
        if (defaults.defaultExportSchemeCode() != null) published(entity, defaults.defaultExportSchemeCode(), "EXPORT");
        repository.publishSettings(entity.getId(), write(defaults),
                directionHash(entity.getId(), "IMPORT", defaults.defaultImportSchemeCode()),
                directionHash(entity.getId(), "EXPORT", defaults.defaultExportSchemeCode()), UserContext.getUserId());
        recordMigrationPublication(entity.getId());
        return settingsView(repository.settings(entity.getId()));
    }

    /**
     * 运行态统一解析入口，不要求管理权限；调用方必须在准备及执行阶段验证真实按钮与数据权限。
     * EXPLICIT 失效绝不回退默认，TEMPLATE 与 IMPORT 始终使用同一默认导入指针。
     */
    @Transactional(readOnly = true)
    public ExcelResolvedScheme resolvePublished(String entityCode, String operation, String mode, String schemeCode) {
        if (operation == null || !Set.of("IMPORT", "EXPORT", "TEMPLATE").contains(operation)) throw new IllegalArgumentException("Excel 操作不合法");
        EntityDefinition entity = uiPolicy.requireConfigurableByCode(entityCode);
        String defaultHash = "";
        if ("DEFAULT".equals(mode)) {
            if (normalized(schemeCode) != null) throw new IllegalArgumentException("DEFAULT 引用不能包含方案编码");
            SettingsRow settings = repository.settings(entity.getId());
            Defaults defaults = publishedDefaults(settings);
            boolean export = "EXPORT".equals(operation);
            schemeCode = export ? defaults.defaultExportSchemeCode() : defaults.defaultImportSchemeCode();
            if (schemeCode == null) throw conflict("请先配置默认" + (export ? "导出" : "导入") + "方案");
            defaultHash = export ? settings.getExportHash() : settings.getImportHash();
        } else if (!"EXPLICIT".equals(mode) || normalized(schemeCode) == null) {
            throw new IllegalArgumentException("方案引用必须为 DEFAULT 或包含编码的 EXPLICIT");
        }
        SchemeRow row = published(entity, schemeCode, operation);
        ExcelSchemeDocument doc = read(row.getDocumentJson(), ExcelSchemeDocument.class);
        return new ExcelResolvedScheme(entity.getId(), entityCode, row.getSchemeCode(), doc, row.getContentHash(), defaultHash, row.getWriteFormId());
    }

    @Transactional(readOnly = true)
    public List<Reference> references(String entityCode, String schemeCode) {
        EntityDefinition entity = management(entityCode, false);
        requireScheme(entity.getId(), schemeCode);
        return referencesInternal(entity.getId(), schemeCode);
    }

    /**
     * 表单/列表发布前调用，校验内置 Excel 动作及历史接口引用。
     * 必须参与外层发布事务，实体锁持续到外层提交以防并发停用方案产生悬空引用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void validateButtonReferences(String entityCode, Object snapshot) {
        EntityDefinition entity = entityMapper.findByEntityCodeForUpdate(entityCode).orElseThrow(() -> new IllegalArgumentException("实体不存在"));
        JsonNode document = json.valueToTree(snapshot);
        validateReferenceNode(entityCode, document, 0, !document.has("list"));
        String type = document.has("list") ? "LIST" : "FORM";
        String id = document.path(type.toLowerCase(java.util.Locale.ROOT)).path("id").asText(null);
        for (JsonNode configuration : effectiveListExcelConfigurations(entity.getId(), type, id, document)) {
            validateReferenceNode(entityCode, configuration, 0, true);
        }
    }

    /** 实体结构发布前检查现行方案，阻止字段删除/改型造成线上映射失效。 */
    @Transactional(readOnly = true)
    public void requireCompatibleFields(String entityCode, List<EntityField> fields) {
        EntityDefinition entity = uiPolicy.requireConfigurableByCode(entityCode);
        var previousSnapshot = snapshots.findLatestByEntityCode(entityCode);
        var planned = com.workflow.entity.excel.migration.EntityExcelMigrationScope.plannedSchemesIfActive(entityCode);
        // 迁移的最终方案已预检且将在同一配置事务内落地；未纳入包的现行方案仍使用原保护。
        for (var replacement : planned.values()) if ("ACTIVE".equals(replacement.status()))
            validator.validatePublished(replacement.config(), entity, fields);
        for (SchemeRow row : repository.schemes(entity.getId())) {
            if (planned.containsKey(row.getSchemeCode())) continue;
            if (row.getDocumentJson() != null && "ACTIVE".equals(row.getStatus())) {
                var document = read(row.getDocumentJson(), ExcelSchemeDocument.class);
                validator.validatePublished(document, entity, fields);
                if (previousSnapshot != null) {
                    for (var column : document.columns()) {
                        EntityField previous = previousSnapshot.getFields().stream().filter(f -> f.getFieldCode().equals(column.fieldCode())).findFirst().orElse(null);
                        EntityField next = fields.stream().filter(f -> f.getFieldCode().equals(column.fieldCode())).findFirst().orElse(null);
                        if (previous != null && next != null && previous.getFieldType() != next.getFieldType()) {
                            throw conflict("字段类型变化会影响已发布 Excel 方案，请先停用方案: " + row.getSchemeCode() + "/" + column.fieldCode());
                        }
                    }
                }
            }
        }
    }

    /** 写入表单删除或停用前调用；已发布方案必须先取消导入能力或删除。 */
    @Transactional(readOnly = true)
    public void requireFormUnreferenced(String formId) {
        var form = formMapper.selectById(formId);
        var entity = form == null ? null : entityMapper.selectById(form.getEntityId());
        var planned = entity == null ? Map.<String, com.workflow.entity.excel.migration.EntityExcelMigrationScope.Scheme>of()
                : com.workflow.entity.excel.migration.EntityExcelMigrationScope.plannedSchemesIfActive(entity.getEntityCode());
        if (planned.isEmpty()) {
            if (repository.publishedSchemeUsesForm(formId)) throw conflict("该表单仍被已发布 Excel 导入方案使用");
            return;
        }
        // 只替换计划覆盖的引用。新增最终方案、未覆盖的原 ACTIVE 方案仍可阻止停用写入表单。
        for (var scheme : planned.values()) if ("ACTIVE".equals(scheme.status())
                && scheme.config().importConfig() != null && scheme.config().importConfig().enabled()
                && Objects.equals(form.getFormKey(), scheme.config().importConfig().writeFormKey()))
            throw conflict("该表单仍被迁移后的 Excel 导入方案使用");
        for (var scheme : repository.schemes(entity.getId())) if (!planned.containsKey(scheme.getSchemeCode())
                && "ACTIVE".equals(scheme.getStatus()) && Objects.equals(formId, scheme.getWriteFormId()))
            throw conflict("该表单仍被已发布 Excel 导入方案使用");
    }

    /** 实体删除前必须清理其配置，避免保留不再有归属实体的方案和默认指针。 */
    public void requireEntityUnreferenced(String entityCode) {
        EntityDefinition entity = uiPolicy.requireConfigurableByCode(entityCode);
        if (!repository.schemes(entity.getId()).isEmpty()) throw conflict("实体仍有 Excel 方案，请先删除方案再删除实体");
    }

    private void validateReferenceNode(String entityCode, JsonNode node, int depth, boolean includeBuiltinActions) {
        if (depth > 64) throw conflict("Excel 引用配置嵌套过深");
        // LIST 内置参数只检查真实可执行链，不能把被覆盖/停用的按钮草稿当作线上引用。
        if (!includeBuiltinActions && ExcelEventAction.TYPE.equals(node.path("actionType").asText())) return;
        if (node.isObject() && node.has("schemeRef") && node.has("operation")) {
            var ref = node.path("schemeRef");
            String operation = node.path("operation").asText(), mode = ref.path("mode").asText(), schemeCode = ref.path("schemeCode").asText(null);
            if (!com.workflow.entity.excel.migration.EntityExcelMigrationScope.validateIfActive(entityCode, operation, mode, schemeCode)) {
                resolvePublished(entityCode, operation, mode, schemeCode);
            }
        }
        if (node.isContainerNode()) node.forEach(child -> validateReferenceNode(entityCode, child, depth + 1, includeBuiltinActions));
        else if (node.isTextual()) {
            String text = node.asText().strip();
            if ((text.startsWith("{") || text.startsWith("[")) && text.contains("schemeRef")) validateReferenceNode(entityCode, read(text, JsonNode.class), depth + 1, includeBuiltinActions);
        }
    }

    /** 显式生成未保存草稿。默认只生成导出能力，管理员选择写入表单后再开启导入并发布。 */
    @Transactional(readOnly = true)
    public ExcelSchemeDocument generate(String entityCode, GenerateRequest request) {
        EntityDefinition entity = management(entityCode, false);
        List<EntityField> fields = snapshots.getLatestByEntityId(entity.getId()).getFields();
        Set<EntityField.FieldType> simple = Set.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT,
                EntityField.FieldType.INTEGER, EntityField.FieldType.LONG, EntityField.FieldType.DECIMAL,
                EntityField.FieldType.DATE, EntityField.FieldType.DATETIME, EntityField.FieldType.BOOLEAN,
                EntityField.FieldType.SELECT, EntityField.FieldType.RADIO);
        List<ExcelSchemeDocument.Column> columns = fields.stream().filter(f -> simple.contains(f.getFieldType()))
                .filter(f -> fieldPolicy.isRuntimeReadable(entity, f)).map(f -> {
                    String codec = switch (f.getFieldType()) {
                        case DATE -> "DATE"; case DATETIME -> "DATETIME"; case DECIMAL -> "AMOUNT";
                        case INTEGER, LONG -> "NUMBER"; case BOOLEAN -> "BOOLEAN"; default -> "STRING";
                    };
                    if (normalized(f.getDictType()) != null) codec = "DICT";
                    // 系统状态没有字典绑定，生成方案时直接使用实体状态名称，普通状态字段仍遵循其代码表。
                    if ("status".equals(f.getFieldCode()) && Boolean.TRUE.equals(f.getIsSystem())) codec = "ENTITY_STATUS";
                    boolean writable = entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM
                            && !Boolean.FALSE.equals(f.getEditable())
                            && (!Boolean.TRUE.equals(f.getIsSystem()) || Set.of("name", "code").contains(f.getFieldCode()));
                    return new ExcelSchemeDocument.Column(f.getFieldCode(), f.getFieldName(), List.of(), f.getFieldCode(),
                            writable, true, Boolean.TRUE.equals(f.getIsRequired()), 20, Map.of("type", codec),
                            Map.of("required", Boolean.TRUE.equals(f.getIsRequired()), "uniqueInFile", Boolean.TRUE.equals(f.getIsUnique())));
                }).toList();
        ExcelSchemeDocument doc = new ExcelSchemeDocument(1, request.schemeCode(), request.name(), null,
                new ExcelSchemeDocument.FileConfig("数据", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(false, "CREATE_ONLY", "ATOMIC_ALL", null, false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, request.name() + "_{yyyyMMddHHmmss}", "数据"), columns);
        validator.validateDraft(doc);
        return doc;
    }

    private EntityDefinition management(String code, boolean lock) {
        roles.requireAdministrator("只有管理员可以查看或维护实体 Excel 配置");
        EntityDefinition entity = uiPolicy.requireConfigurableByCode(code);
        // 与实体结构发布复用同一锁，默认切换/删除不可能在各自校验后交叉提交。
        // 先取得表单发布锁，与写入表单删除/停用保持相同锁序，防止刚发布的方案引用已删除表单。
        if (lock) publishedForms.lockPublication();
        return lock ? entityMapper.findByEntityCodeForUpdate(code).orElseThrow(() -> new IllegalArgumentException("实体不存在")) : entity;
    }
    private SchemeRow requireScheme(String entityId, String code) {
        SchemeRow row = repository.scheme(entityId, code);
        if (row == null) throw new IllegalArgumentException("Excel 方案不存在: " + code);
        return row;
    }
    private SchemeRow published(EntityDefinition entity, String code, String operation) {
        SchemeRow row = requireScheme(entity.getId(), code);
        if (!"ACTIVE".equals(row.getStatus()) || row.getDocumentJson() == null) throw conflict("Excel 方案未发布或已停用: " + code);
        ExcelSchemeDocument doc = read(row.getDocumentJson(), ExcelSchemeDocument.class);
        if (!supports(doc, operation)) throw conflict("Excel 方案未启用此操作: " + operation);
        if (!Objects.equals(row.getContentHash(), hash(row.getDocumentJson() + "\n" + Objects.toString(row.getWriteFormId(), "")))) throw conflict("Excel 方案发布内容校验失败");
        String formId = validatePublished(entity, doc);
        if (!Objects.equals(formId, row.getWriteFormId())) throw conflict("Excel 方案写入表单已变更，请重新发布");
        return row;
    }
    private String validatePublished(EntityDefinition entity, ExcelSchemeDocument doc) {
        var entitySnapshot = snapshots.findLatestByEntityCode(entity.getEntityCode());
        if (entitySnapshot == null) throw conflict("实体尚未发布，Excel 方案暂不可用");
        List<EntityField> fields = entitySnapshot.getFields();
        validator.validatePublished(doc, entity, fields);
        for (var column : doc.columns()) {
            EntityField field = fields.stream().filter(f -> f.getFieldCode().equals(column.fieldCode())).findFirst().orElseThrow();
            if (!fieldPolicy.isRuntimeReadable(entity, field)) throw new IllegalArgumentException("字段不允许导出: " + column.fieldCode());
            if ("DICT".equals(column.codec().get("type")) && !repository.dictionaryAvailable(field.getDictType())) {
                throw new IllegalArgumentException("字段代码表不存在或已停用: " + column.fieldCode());
            }
        }
        if (!doc.importConfig().enabled()) return null;
        var form = formMapper.selectByEntityIdAndFormKey(entity.getId(), doc.importConfig().writeFormKey());
        if (form == null) throw new IllegalArgumentException("写入表单不存在");
        var published = publishedForms.selectById(form.getId());
        if (published == null || !entity.getId().equals(published.getEntityId())) throw new IllegalArgumentException("写入表单尚未发布或归属不匹配");
        // 表单草稿的启停编辑不能影响线上导入；运行可用性以唯一当前发布内容为准。
        JsonNode publishedDocument = read(published.getSnapshotDocument(), JsonNode.class);
        if (publishedDocument.path("form").path("status").asInt(1) != 1) throw new IllegalArgumentException("写入表单已停用");
        return form.getId();
    }
    private boolean supports(ExcelSchemeDocument doc, String operation) {
        return "EXPORT".equals(operation) ? doc.exportConfig() != null && doc.exportConfig().enabled()
                : doc.importConfig() != null && doc.importConfig().enabled();
    }
    /** 保存默认勾选只触及当前方案，取消未选中的方案不能误清另一方案的默认草稿。 */
    private Defaults defaultsForSave(SettingsRow settings, ExcelSchemeDocument doc, SaveScheme request) {
        if (request.defaultImport() == null && request.defaultExport() == null) return null;
        revision(request.expectedSettingsRevision(), settings == null ? 0 : settings.getRevision());
        if (Boolean.TRUE.equals(request.defaultImport()) && !supports(doc, "IMPORT")) throw conflict("请先启用导入再设为默认");
        if (Boolean.TRUE.equals(request.defaultExport()) && !supports(doc, "EXPORT")) throw conflict("请先启用导出再设为默认");
        Defaults draft = draftDefaults(settings);
        return new Defaults(1,
                selectedDefault(doc.schemeCode(), request.defaultImport(), draft.defaultImportSchemeCode()),
                selectedDefault(doc.schemeCode(), request.defaultExport(), draft.defaultExportSchemeCode()));
    }

    private String selectedDefault(String code, Boolean selected, String current) {
        if (selected == null) return current;
        return selected ? code : code.equals(current) ? null : current;
    }

    /** 仅应用当前方案的勾选结果；其他方案的草稿指针既不抢先发布，也不覆盖另一方向的现行默认。 */
    private Defaults defaultsForPublish(String code, ExcelSchemeDocument doc, SettingsRow settings) {
        Defaults draft = draftDefaults(settings);
        Defaults published = publishedDefaults(settings);
        if (code.equals(draft.defaultImportSchemeCode()) && !supports(doc, "IMPORT")) throw conflict("默认导入方案必须启用导入");
        if (code.equals(draft.defaultExportSchemeCode()) && !supports(doc, "EXPORT")) throw conflict("默认导出方案必须启用导出");
        return new Defaults(1,
                publishedDefaultForScheme(code, draft.defaultImportSchemeCode(), published.defaultImportSchemeCode()),
                publishedDefaultForScheme(code, draft.defaultExportSchemeCode(), published.defaultExportSchemeCode()));
    }

    private String publishedDefaultForScheme(String code, String draft, String published) {
        return code.equals(draft) ? code : code.equals(published) ? null : published;
    }

    private SchemeView view(SchemeRow row, SettingsRow settings) {
        Defaults defaults = publishedDefaults(settings);
        Defaults draft = draftDefaults(settings);
        return new SchemeView(row.getId(), row.getSchemeCode(), row.getName(), row.getRevision(), row.getStatus(),
                read(row.getDraftJson(), ExcelSchemeDocument.class), read(row.getDocumentJson(), ExcelSchemeDocument.class), row.getContentHash(),
                row.getSchemeCode().equals(defaults.defaultImportSchemeCode()), row.getSchemeCode().equals(defaults.defaultExportSchemeCode()),
                row.getSchemeCode().equals(draft.defaultImportSchemeCode()), row.getSchemeCode().equals(draft.defaultExportSchemeCode()),
                settings == null ? 0 : settings.getRevision());
    }
    private SettingsView settingsView(SettingsRow row) {
        return new SettingsView(row == null ? 0 : row.getRevision(), draftDefaults(row),
                publishedDefaults(row), row == null ? null : row.getImportHash(), row == null ? null : row.getExportHash());
    }
    private Defaults draftDefaults(SettingsRow row) { return row == null || row.getDraftJson() == null ? publishedDefaults(row) : read(row.getDraftJson(), Defaults.class); }
    private Defaults publishedDefaults(SettingsRow row) { return row == null || row.getPublishedJson() == null ? emptyDefaults() : read(row.getPublishedJson(), Defaults.class); }
    private Defaults emptyDefaults() { return new Defaults(1, null, null); }
    private void requireUnreferenced(String entityId, String code) {
        if (referencesInternal(entityId, code).stream().anyMatch(Reference::published)) throw conflict("方案仍被默认设置或已发布按钮引用，请先解除引用");
    }

    /** 检查内置动作参数及历史接口配置（含嵌套 JSON），而非仅检查按钮外观配置。 */
    private List<Reference> referencesInternal(String entityId, String code) {
        Set<Reference> found = new LinkedHashSet<>();
        Defaults defaults = publishedDefaults(repository.settings(entityId));
        if (code.equals(defaults.defaultImportSchemeCode())) found.add(new Reference("DEFAULT_SETTINGS", entityId, "IMPORT", "DEFAULT", true));
        if (code.equals(defaults.defaultExportSchemeCode())) found.add(new Reference("DEFAULT_SETTINGS", entityId, "EXPORT", "DEFAULT", true));
        for (var snapshot : repository.snapshots(entityId)) {
            if (snapshot.document() == null) continue;
            JsonNode document = read(snapshot.document(), JsonNode.class);
            scan(document, snapshot.ownerType(), snapshot.ownerId(), snapshot.published(), code, defaults, found, 0, !"LIST".equals(snapshot.ownerType()));
            for (JsonNode configuration : effectiveListExcelConfigurations(entityId, snapshot.ownerType(), snapshot.ownerId(), document)) {
                scan(configuration, snapshot.ownerType(), snapshot.ownerId(), snapshot.published(), code, defaults, found, 0, true);
            }
        }
        return List.copyOf(found);
    }

    /**
     * LIST 内置动作的参数随事件发布；历史接口仍须跟随有效继承链读取当前定义。
     * 已删/停用按钮及被 DISABLE/REPLACE 覆盖的继承步骤不形成可执行引用。
     */
    private List<JsonNode> effectiveListExcelConfigurations(String entityId, String ownerType, String ownerId, JsonNode document) {
        if (!"LIST".equals(ownerType) || ownerId == null || !document.has("eventBindings")) return List.of();
        if (eventBindings == null) throw conflict("Excel事件引用解析服务不可用");
        List<JsonNode> result = new ArrayList<>();
        Map<String, Object> snapshot = json.convertValue(document, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        for (String position : List.of("toolbarConfig", "rowActionConfig")) {
            JsonNode buttons = document.path("list").path(position);
            if (buttons.isTextual()) buttons = read(buttons.asText(), JsonNode.class);
            if (buttons == null || !buttons.isArray()) continue;
            String event = "toolbarConfig".equals(position) ? "TOOLBAR_BUTTON_CLICK" : "ROW_BUTTON_CLICK";
            for (JsonNode button : buttons) {
                if (!"custom".equals(button.path("type").asText()) || !button.path("enabled").asBoolean(true)) continue;
                String key = button.path("key").asText(null);
                if (key == null || key.isBlank()) continue;
                for (Map<String, Object> step : eventBindings.getObject().resolveSnapshotButtonSteps("LIST", ownerId, entityId, event, key, snapshot)) {
                    if (!"REPLACE".equals(step.get("strategy")) || step.get("condition") != null
                            && (!(step.get("condition") instanceof Map<?, ?> condition) || !condition.isEmpty())) continue;
                    if (ExcelEventAction.isExcelStep(step)) {
                        ExcelEventAction.validateStep(step, "LIST", "BUTTON", event);
                        result.add(json.valueToTree(ExcelEventAction.configuration(step)));
                        continue;
                    }
                    // LIST 运行时执行当前接口；即使历史步骤附过快照，也不能误用过期配置检查引用。
                    Object id = step.get("extensionId");
                    if (id == null || String.valueOf(id).isBlank()) id = step.get("serviceId");
                    if (id == null) continue;
                    String operation = step.get("operationCode") == null ? null : String.valueOf(step.get("operationCode"));
                    String configuration = repository.excelInterfaceConfiguration(String.valueOf(id), operation);
                    if (configuration != null) result.add(read(configuration, JsonNode.class));
                }
            }
        }
        return result;
    }
    private void scan(JsonNode node, String ownerType, String ownerId, boolean published, String code,
                      Defaults defaults, Set<Reference> found, int depth, boolean includeBuiltinActions) {
        if (depth > 64) throw conflict("Excel 引用快照嵌套过深，无法可靠检查");
        if (!includeBuiltinActions && ExcelEventAction.TYPE.equals(node.path("actionType").asText())) return;
        if (node.isObject() && node.has("schemeRef")) {
            JsonNode ref = node.path("schemeRef");
            String operation = node.path("operation").asText(), mode = ref.path("mode").asText();
            String resolved = "DEFAULT".equals(mode) ? ("EXPORT".equals(operation) ? defaults.defaultExportSchemeCode() : defaults.defaultImportSchemeCode()) : ref.path("schemeCode").asText();
            if (Set.of("IMPORT", "EXPORT", "TEMPLATE").contains(operation) && code.equals(resolved)) found.add(new Reference(ownerType, ownerId, operation, mode, published));
        }
        if (node.isContainerNode()) node.forEach(child -> scan(child, ownerType, ownerId, published, code, defaults, found, depth + 1, includeBuiltinActions));
        else if (node.isTextual()) {
            String text = node.asText().strip();
            if ((text.startsWith("{") || text.startsWith("[")) && text.contains("schemeRef")) scan(read(text, JsonNode.class), ownerType, ownerId, published, code, defaults, found, depth + 1, includeBuiltinActions);
        }
    }
    private void revision(Integer expected, int actual) {
        if (expected == null || expected != actual) throw new RevisionConflictException("Excel 配置已变更，请刷新后重试", Map.of("revision", actual));
    }
    private String normalized(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    /** 与配置发布同事务登记实体迁移资产；实现端负责迁移应用期间的去重，草稿保存不会登记。 */
    private void recordMigrationPublication(String entityId) {
        if (migrationPublications == null) return;
        ExcelConfigurationPublicationPort publication = migrationPublications.getIfAvailable();
        if (publication != null) publication.recordPublished(entityId);
    }
    private String directionHash(String entityId, String direction, String code) { return hash(entityId + "\n" + direction + "\n" + Objects.toString(code, "")); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Excel 配置序列化失败", e); }
    }
    private <T> T read(String value, Class<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Excel 配置文档损坏", e); }
    }
    private BusinessConflictException conflict(String message) { return new BusinessConflictException("EXCEL_CONFIGURATION_CHANGED", message); }
}
