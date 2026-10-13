package com.workflow.entity.excel.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.contracts.migration.port.EntityExcelMigrationPort;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.config.ExcelSchemeValidator;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.model.ExcelEventAction;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 已发布 Excel 配置的跨环境适配器。所有写入复用配置服务的授权、锁、修订与发布校验。 */
@Component
@RequiredArgsConstructor
public class EntityExcelMigrationAdapter implements EntityExcelMigrationPort {
    private static final ThreadLocal<Boolean> APPLYING = new ThreadLocal<>();
    @Override public boolean isPublicationPending() { return Boolean.TRUE.equals(APPLYING.get()); }
    private static final Set<String> CODEC_KEYS = Set.of("type", "trim", "format", "zoneId", "readPatterns", "scale", "roundingMode", "writeAsText", "importMode", "exportMode", "emptyToNull", "allowThousands", "currencySymbol");
    private static final Set<String> RULE_KEYS = Set.of("required", "uniqueInFile", "min", "max", "minLength", "maxLength", "pattern");
    private final EntityDefinitionMapper entities;
    private final EntityPublishedSnapshotService snapshots;
    private final EntityFormMapper forms;
    private final EntityFormPublishedMapper publishedForms;
    private final EntityListConfigMapper lists;
    private final ExcelConfigurationRepository repository;
    private final EntityExcelConfigurationService configurations;
    private final ExcelSchemeValidator validator;
    private final CurrentUserRoleService roles;
    private final ObjectMapper json;

    /** 白名单输出发布文档，故源端 ID、修订号、摘要、任务及签名均不会成为可迁移身份。 */
    @Override public Map<String, Object> exportPublished(String entityCode) {
        EntityDefinition entity = entities.findByEntityCode(entityCode).orElseThrow(() -> new IllegalArgumentException("迁移实体不存在"));
        List<Map<String, Object>> schemes = new ArrayList<>();
        for (var row : repository.schemes(entity.getId())) {
            if (row.getDocumentJson() == null) continue;
            ExcelSchemeDocument doc = read(row.getDocumentJson(), ExcelSchemeDocument.class);
            Map<String, Object> config = object(json.convertValue(doc, Map.class));
            List<Map<String, Object>> columns = objects(config.get("columns"));
            for (Map<String, Object> column : columns) {
                column.put("codec", whitelist(object(column.get("codec")), CODEC_KEYS));
                column.put("validation", whitelist(object(column.get("validation")), RULE_KEYS));
            }
            config.put("columns", columns);
            schemes.add(Map.of("schemeCode", doc.schemeCode(), "status", row.getStatus(), "config", config));
        }
        var stored = repository.settings(entity.getId());
        Defaults defaults = stored == null || stored.getPublishedJson() == null ? new Defaults(1, null, null) : read(stored.getPublishedJson(), Defaults.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("schemes", schemes);
        result.put("defaults", json.convertValue(defaults, Map.class));
        return result;
    }

    /** 预检仅检查包和现存目标配置。包内新表单/字段可作为计划依赖，最终仍须经过正常发布校验。 */
    @Override public void validate(String entityCode, Map<String, Object> snapshot) {
        if (!snapshot.containsKey(SECTION)) return;
        roles.requireAdministrator("只有管理员可以迁移 Excel 配置");
        EntityExcelMigrationScope.Plan plan = plan(snapshot);
        EntityDefinition existing = entities.findByEntityCode(entityCode).orElse(null);
        EntityDefinition entity = existing == null ? convert(snapshot.get("definition"), EntityDefinition.class) : existing;
        if (entity == null || entity.getStorageMode() == null || entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM)
            throw new IllegalArgumentException("Excel 配置迁移需要明确的普通实体存储模式");
        entity.setEntityCode(entityCode);
        List<EntityField> fields;
        if (snapshot.containsKey("fields")) fields = objects(snapshot.get("fields")).stream().map(value -> convert(value, EntityField.class)).toList();
        else {
            var published = snapshots.findLatestByEntityCode(entityCode);
            if (published == null) throw new IllegalArgumentException("Excel 配置迁移需要已发布实体或同包实体字段");
            fields = published.getFields();
        }
        for (var scheme : plan.schemes().values()) {
            // 停用历史方案可以包含已删除字段，恢复时不得短暂变成可执行方案。
            if (!"ACTIVE".equals(scheme.status())) continue;
            validator.validatePublished(scheme.config(), entity, fields);
            if (scheme.config().importConfig().enabled()) requireForm(entity, snapshot, scheme.config().importConfig().writeFormKey());
        }
        if (plan.includesDefaults()) {
            if (plan.importDefault() != null) requireReference(entityCode, plan, "IMPORT", plan.importDefault());
            if (plan.exportDefault() != null) requireReference(entityCode, plan, "EXPORT", plan.exportDefault());
        }
        for (ActionConfiguration action : actionConfigurations(snapshot)) {
            Map<String, Object> config = action.config();
            requireKeys(config, Set.of("entityCode", "operation", "schemeRef", "dataScope", "listKey", "selectionSource"), "Excel 按钮配置");
            if (config.get("entityCode") != null && !entityCode.equals(config.get("entityCode"))) throw new IllegalArgumentException("Excel 按钮不能引用其他实体的方案");
            Map<String, Object> ref = object(config.get("schemeRef"));
            requireKeys(ref, Set.of("mode", "schemeCode"), "Excel 方案引用");
            String operation = text(config.get("operation"));
            String mode = text(ref.get("mode"));
            String code = text(ref.get("schemeCode"));
            if ("DEFAULT".equals(mode)) {
                if (code != null) throw new IllegalArgumentException("DEFAULT 不能指定 schemeCode");
                code = "EXPORT".equals(operation) ? plan.exportDefault() : plan.importDefault();
                if (code == null) throw new IllegalArgumentException("Excel 按钮缺少对应默认方案");
            } else if (!"EXPLICIT".equals(mode)) throw new IllegalArgumentException("Excel 按钮引用模式不合法");
            requireReference(entityCode, plan, operation, code);
            if ("FORM".equals(action.ownerType()) && "EXPORT".equals(operation)
                    && Set.of("SELECTED", "FILTERED").contains(String.valueOf(config.get("dataScope")))) {
                Map<String, Object> source = object(config.get("selectionSource"));
                requireKeys(source, Set.of("type"), "Excel 选择来源");
                if (!"LIST_DIALOG".equals(source.get("type"))) throw new IllegalArgumentException("表单批量导出缺少列表选择来源");
                requireList(entity, snapshot, text(config.get("listKey")));
            }
        }
    }

    @Override public PublicationScope openPublicationScope(Map<String, Map<String, Object>> entitySnapshots) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Excel 迁移发布必须位于实际事务中");
        roles.requireAdministrator("只有管理员可以迁移 Excel 配置");
        Map<String, EntityExcelMigrationScope.Plan> plans = new LinkedHashMap<>();
        entitySnapshots.forEach((code, snapshot) -> {
            if (snapshot.containsKey(SECTION)) { validate(code, snapshot); plans.put(code, plan(snapshot)); }
        });
        if (plans.isEmpty()) return PublicationScope.NONE;
        // 后续流程/实体锁之前先取得与配置服务一致的表单发布锁，保持全局锁序。
        publishedForms.lockPublication();
        return EntityExcelMigrationScope.open(plans);
    }

    /** 此阶段不得处于延期引用作用域；逐项重建目标身份，保存/发布均使用目标最新 revision。 */
    @Override public void applyPublished(String entityCode, Map<String, Object> snapshot) {
        if (!snapshot.containsKey(SECTION)) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Excel 迁移发布必须位于实际事务中");
        EntityExcelMigrationScope.requireClosed();
        EntityExcelMigrationScope.Plan plan = plan(snapshot);
        roles.requireAdministrator("只有管理员可以迁移 Excel 配置");
        if (isPublicationPending()) throw new IllegalStateException("Excel 迁移应用不能嵌套");
        APPLYING.set(true);
        try {
        // 先通过正常设置接口解除旧默认的能力依赖；这是事务内中间态，最终默认和引用复核成功才可提交。
        // 此阶段暂停迁移资产回调，避免将中间的空默认设置记录成可导出的历史资产。
        if (plan.includesDefaults()) {
            SettingsView current = configurations.settings(entityCode);
            SettingsView cleared = configurations.saveSettings(entityCode, new SaveSettings(current.revision(), null, null));
            configurations.publishSettings(entityCode, cleared.revision());
        }
        Map<String, SchemeView> savedSchemes = new LinkedHashMap<>();
        for (var entry : plan.schemes().entrySet()) {
            String code = entry.getKey();
            SchemeView current = configurations.list(entityCode).stream().filter(value -> value.schemeCode().equals(code)).findFirst().orElse(null);
            SchemeView saved = configurations.save(entityCode, new SaveScheme(current == null ? null : current.id(), current == null ? 0 : current.revision(), code, entry.getValue().config().name(), entry.getValue().config()));
            if ("ACTIVE".equals(entry.getValue().status())) {
                if ("DISABLED".equals(saved.status())) {
                    // 原停用文档可能已经不兼容当前字段；先恢复新的停用文档，再由正常启用/发布做完整检查。
                    saved = restoreDisabledDocument(entityCode, saved, entry.getValue().config());
                    saved = configurations.status(entityCode, code, new StatusRequest(saved.revision(), "ACTIVE"));
                }
                saved = configurations.publish(entityCode, code, saved.revision());
            }
            savedSchemes.put(code, saved);
        }
        if (plan.includesDefaults()) {
            SettingsView current = configurations.settings(entityCode);
            SettingsView saved = configurations.saveSettings(entityCode, new SaveSettings(current.revision(), plan.importDefault(), plan.exportDefault()));
            configurations.publishSettings(entityCode, saved.revision());
        }
        for (var entry : plan.schemes().entrySet()) if ("DISABLED".equals(entry.getValue().status())) {
            SchemeView saved = savedSchemes.get(entry.getKey());
            if (!"DISABLED".equals(saved.status())) saved = configurations.status(entityCode, entry.getKey(), new StatusRequest(saved.revision(), "DISABLED"));
            restoreDisabledDocument(entityCode, saved, entry.getValue().config());
        }
        // 旧接口身份由原迁移链重绑，新内置动作随页面绑定保存；两种配置都复核稳定方案引用。
        for (ActionConfiguration action : actionConfigurations(snapshot)) {
            Map<String, Object> config = action.config();
            Map<String, Object> ref = object(config.get("schemeRef"));
            configurations.resolvePublished(entityCode, text(config.get("operation")), text(ref.get("mode")), text(ref.get("schemeCode")));
        }
        verifyPublishedButtons(entityCode);
        } finally { APPLYING.remove(); }
    }

    /**
     * 专用于可信迁移的停用历史文档恢复：语法/白名单已预检，管理保存已取得授权及实体锁。
     * 不激活方案，不提供运行能力；日后启用仍需正常服务验证当前字段和已发布写入表单。
     */
    private SchemeView restoreDisabledDocument(String entityCode, SchemeView saved, ExcelSchemeDocument doc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !isPublicationPending())
            throw new IllegalStateException("停用方案恢复仅允许迁移事务内部调用");
        roles.requireAdministrator("只有管理员可以恢复停用 Excel 方案");
        validator.validateDraft(doc);
        var entity = entities.findByEntityCode(entityCode).orElseThrow();
        var row = repository.scheme(entity.getId(), saved.schemeCode());
        if (row == null || !row.getId().equals(saved.id()) || !"DISABLED".equals(row.getStatus())
                || !java.util.Objects.equals(row.getRevision(), saved.revision()))
            throw new IllegalStateException("恢复的 Excel 方案必须保持当前停用修订");
        var form = doc.importConfig() == null || doc.importConfig().writeFormKey() == null ? null
                : forms.selectByEntityIdAndFormKey(entity.getId(), doc.importConfig().writeFormKey());
        String formId = form == null ? null : form.getId();
        try {
            String document = json.writeValueAsString(doc);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((document + "\n" + java.util.Objects.toString(formId, "")).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            repository.publish(row, document, hash, formId, com.workflow.admin.security.context.UserContext.getUserId());
            return new SchemeView(saved.id(), doc.schemeCode(), doc.name(), saved.revision() + 1, "DISABLED", doc, doc, hash, false, false);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("停用 Excel 方案无法恢复", error); }
    }

    /** 复核目标环境真正生效的页面事件，覆盖已固定快照和列表动态接口引用。 */
    private void verifyPublishedButtons(String entityCode) {
        EntityDefinition entity = entities.findByEntityCode(entityCode).orElseThrow();
        for (var snapshot : repository.snapshots(entity.getId())) {
            if (!snapshot.published() || !Set.of("FORM", "LIST").contains(snapshot.ownerType())) continue;
            Map<String, Object> document = document(snapshot.document());
            String ownerKey = snapshot.ownerType().toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> owner = object(document.get(ownerKey));
            owner.put("id", snapshot.ownerId());
            document.put(ownerKey, owner);
            configurations.validateButtonReferences(entityCode, document);
        }
    }

    @Override public void removeAddedSchemes(String entityCode, Map<String, Object> previous, Map<String, Object> imported) {
        if (!imported.containsKey(SECTION)) return;
        var old = plan(previous).schemes().keySet();
        for (var current : configurations.list(entityCode)) {
            if (plan(imported).schemes().containsKey(current.schemeCode()) && !old.contains(current.schemeCode()))
                configurations.delete(entityCode, current.schemeCode(), current.revision());
        }
    }

    private void requireReference(String entityCode, EntityExcelMigrationScope.Plan plan, String operation, String code) {
        if (code == null) throw new IllegalArgumentException("Excel 方案编码不能为空");
        var scheme = plan.schemes().get(code);
        if (scheme != null) EntityExcelMigrationScope.requireSupported(scheme, operation);
        else configurations.resolvePublished(entityCode, operation, "EXPLICIT", code);
    }

    /** 汇总旧 Provider 与内置按钮参数，避免无接口记录的新绑定绕过迁移预检和最终复核。 */
    private List<ActionConfiguration> actionConfigurations(Map<String, Object> snapshot) {
        List<ActionConfiguration> result = new ArrayList<>();
        for (Map<String, Object> extension : objects(snapshot.get("interfaceExtensions"))) {
            if ("entity_excel_action".equals(extension.get("providerCode")))
                result.add(new ActionConfiguration(text(extension.get("scopeType")), document(extension.get("implementationConfigDocument"))));
        }
        collectBuiltinConfigurations(snapshot, result);
        return result;
    }

    /** 表单内嵌、列表及实体包顶层事件均可携带绑定；只读取事件步骤中的静态动作参数。 */
    private void collectBuiltinConfigurations(Object value, List<ActionConfiguration> result) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> node = object(raw);
            Object steps = node.getOrDefault("steps", node.get("stepsDocument"));
            if (steps instanceof String text && !text.isBlank()) steps = read(text, List.class);
            for (Map<String, Object> step : objects(steps)) {
                if (!ExcelEventAction.isExcelStep(step)) continue;
                String ownerType = text(node.get("ownerType"));
                ExcelEventAction.validateStep(step, ownerType, text(node.get("targetType")), text(node.get("eventCode")));
                result.add(new ActionConfiguration(ownerType, ExcelEventAction.configuration(step)));
            }
            for (Object child : node.values()) collectBuiltinConfigurations(child, result);
        } else if (value instanceof List<?> list) {
            for (Object child : list) collectBuiltinConfigurations(child, result);
        }
    }

    private record ActionConfiguration(String ownerType, Map<String, Object> config) { }

    private void requireForm(EntityDefinition entity, Map<String, Object> snapshot, String key) {
        for (Map<String, Object> form : objects(snapshot.get("forms"))) if (key.equals(form.get("formKey"))) {
            if ("0".equals(String.valueOf(form.get("status")))) throw new IllegalArgumentException("迁移写入表单已停用: " + key);
            return;
        }
        var form = entity.getId() == null ? null : forms.selectByEntityIdAndFormKey(entity.getId(), key);
        var published = form == null ? null : publishedForms.selectById(form.getId());
        if (published == null || !entity.getId().equals(published.getEntityId())
                || "0".equals(String.valueOf(object(document(published.getSnapshotDocument()).get("form")).get("status"))))
            throw new IllegalArgumentException("迁移写入表单必须在包内或目标已发布: " + key);
    }

    private void requireList(EntityDefinition entity, Map<String, Object> snapshot, String key) {
        if (key == null) throw new IllegalArgumentException("表单 Excel 批量导出必须指定 listKey");
        if (objects(snapshot.get("lists")).stream().anyMatch(value -> key.equals(value.get("listKey")))) return;
        var list = entity.getId() == null ? null : lists.findByEntityIdAndListKey(entity.getId(), key);
        if (list == null || list.getActiveReleaseId() == null) throw new IllegalArgumentException("Excel 来源列表必须在包内或目标已发布: " + key);
    }

    private EntityExcelMigrationScope.Plan plan(Map<String, Object> snapshot) {
        Map<String, Object> section = object(snapshot.get(SECTION));
        if (section.isEmpty()) return new EntityExcelMigrationScope.Plan(Map.of(), false, null, null);
        requireKeys(section, Set.of("schemaVersion", "schemes", "defaults"), "Excel 迁移配置");
        if (!"1".equals(String.valueOf(section.get("schemaVersion")))) throw new IllegalArgumentException("Excel 迁移配置版本不支持");
        Map<String, EntityExcelMigrationScope.Scheme> schemes = new LinkedHashMap<>();
        for (Map<String, Object> item : objects(section.get("schemes"))) {
            requireKeys(item, Set.of("schemeCode", "status", "config"), "Excel 迁移方案");
            Map<String, Object> config = object(item.get("config"));
            requireKeys(config, Set.of("schemaVersion", "schemeCode", "name", "description", "file", "importConfig", "exportConfig", "columns"), "Excel 方案文档");
            requireKeys(object(config.get("file")), Set.of("sheetName", "headRowNumber", "unknownColumnPolicy"), "Excel 文件设置");
            // 表单外映射字段策略随方案迁移；保留 file 同名字段的文件表头语义，旧包缺省由文档模型兼容。
            requireKeys(object(config.get("importConfig")), Set.of("enabled", "writePolicy", "transactionPolicy", "writeFormKey", "startProcess", "maxRows", "unknownColumnPolicy", "validateHeader", "stopAtEmptyRow", "batchSize", "returnUnimported", "precheckEnabled", "failurePolicy", "maxFileSizeMb"), "Excel 导入设置");
            requireKeys(object(config.get("exportConfig")), Set.of("enabled", "fileNamePattern", "sheetName"), "Excel 导出设置");
            for (var column : objects(config.get("columns"))) requireKeys(column,
                    Set.of("key", "header", "headerAliases", "fieldCode", "importEnabled", "exportEnabled", "requiredHeader", "width", "codec", "validation"), "Excel 列设置");
            ExcelSchemeDocument doc = convert(config, ExcelSchemeDocument.class);
            validator.validateDraft(doc);
            if (!doc.schemeCode().equals(item.get("schemeCode")) || schemes.containsKey(doc.schemeCode())) throw new IllegalArgumentException("Excel 迁移方案编码不一致或重复");
            String status = text(item.get("status"));
            if (status == null || !Set.of("ACTIVE", "DISABLED").contains(status)) throw new IllegalArgumentException("Excel 迁移方案状态不合法");
            for (var column : doc.columns()) { requireKeys(column.codec(), CODEC_KEYS, "Excel 转换规则"); requireKeys(column.validation(), RULE_KEYS, "Excel 校验规则"); }
            schemes.put(doc.schemeCode(), new EntityExcelMigrationScope.Scheme(status, doc));
        }
        Map<String, Object> defaults = object(section.get("defaults"));
        requireKeys(defaults, Set.of("schemaVersion", "defaultImportSchemeCode", "defaultExportSchemeCode"), "Excel 默认设置");
        if (section.containsKey("defaults") && !"1".equals(String.valueOf(defaults.get("schemaVersion"))))
            throw new IllegalArgumentException("Excel 默认设置版本不支持");
        return new EntityExcelMigrationScope.Plan(Map.copyOf(schemes), section.containsKey("defaults"), text(defaults.get("defaultImportSchemeCode")), text(defaults.get("defaultExportSchemeCode")));
    }

    private static Map<String, Object> whitelist(Map<String, Object> values, Set<String> keys) { values.keySet().removeIf(key -> !keys.contains(key)); return values; }
    private static void requireKeys(Map<String, Object> values, Set<String> keys, String label) {
        if (!keys.containsAll(values.keySet())) throw new IllegalArgumentException(label + "含不支持的字段，禁止迁移本地ID、文件或运行令牌");
    }
    private Map<String, Object> document(Object value) { return value instanceof String text ? read(text, Map.class) : object(value); }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception error) { throw new IllegalArgumentException("Excel 迁移文档损坏", error); } }
    private <T> T convert(Object value, Class<T> type) {
        try { return json.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue((com.fasterxml.jackson.databind.JsonNode) json.valueToTree(value)); }
        catch (Exception error) { throw new IllegalArgumentException("Excel 迁移配置格式错误", error); }
    }
    private static String text(Object value) { return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value); }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(); }
    private static List<Map<String, Object>> objects(Object value) { return value instanceof List<?> items ? items.stream().map(EntityExcelMigrationAdapter::object).toList() : List.of(); }
}
