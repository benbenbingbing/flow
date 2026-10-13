package com.workflow.entity.excel.persistence;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.workflow.entity.excel.infrastructure.persistence.mapper.ExcelConfigurationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Excel 配置持久化。写操作由应用服务在实体行锁内调用，所有参数均使用绑定变量。 */
@Repository
@RequiredArgsConstructor
public class ExcelConfigurationRepository {
    private final ExcelConfigurationMapper mapper;

    @Data
    public static class SchemeRow {
        private String id;
        private String entityId;
        private String schemeCode;
        private String name;
        private String draftJson;
        private Integer revision;
        private String status;
        private String documentJson;
        private String contentHash;
        private String writeFormId;
    }
    @Data
    public static class SettingsRow {
        private String entityId;
        private String draftJson;
        private String publishedJson;
        private Integer revision;
        private String importHash;
        private String exportHash;
    }
    public record Snapshot(String ownerType, String ownerId, String document, boolean published) {}

    public List<SchemeRow> schemes(String entityId) { return mapper.schemes(entityId); }
    public SchemeRow scheme(String entityId, String code) { return mapper.scheme(entityId, code); }
    public SettingsRow settings(String entityId) { return mapper.settings(entityId); }
    public boolean dictionaryAvailable(String code) { return mapper.availableDictionaries(code) > 0; }
    public boolean publishedSchemeUsesForm(String formId) { return mapper.publishedSchemesUsingForm(formId) > 0; }
    public void insertScheme(SchemeRow row) { mapper.insertScheme(row); }
    public void updateScheme(SchemeRow row) { mapper.updateScheme(row); }

    /** 调用方持实体锁并开启事务；替换发布内容和递增修订号必须同时提交。 */
    public void publish(SchemeRow row, String document, String hash, String formId, String userId) {
        mapper.deletePublished(row.id);
        mapper.insertPublished(row.id, document, hash, formId, userId);
        mapper.bump(row.id);
    }
    public void status(String id, String status) { mapper.status(id, status); }

    /** 启用停用方案时恢复可执行表单身份；调用方持发布锁与实体锁，revision由同事务的status更新一次。 */
    public void rebindPublishedForm(String id, String formId, String hash, String userId) {
        if (mapper.rebindPublishedForm(id, formId, hash, userId) != 1)
            throw new IllegalStateException("Excel方案发布文档不存在，不能恢复写入表单");
    }
    public void delete(String id) {
        mapper.deletePublished(id);
        mapper.deleteScheme(id);
    }
    public void saveSettings(String entityId, String document, boolean exists) {
        if (exists) mapper.updateSettings(entityId, document);
        else mapper.insertSettings(entityId, document);
    }
    public void publishSettings(String entityId, String document, String importHash, String exportHash, String userId) {
        mapper.publishSettings(entityId, document, importHash, exportHash, userId);
    }

    /**
     * LIST 事件仍按当前接口定义执行；从服务端步骤身份解析 Excel 配置用于发布校验和引用保护。
     * 这里只读取对应 Provider，不要求配置管理员权限；调用方已限定所属实体的可信页面快照。
     */
    public String excelInterfaceConfiguration(String extensionId, String legacyOperationCode) {
        if (extensionId == null || extensionId.isBlank()) return null;
        var current = mapper.currentInterface(extensionId);
        if (current != null) return "entity_excel_action".equals(current.getProviderCode()) ? current.getDocument() : null;
        if (legacyOperationCode == null || legacyOperationCode.isBlank()) return null;
        var legacy = mapper.legacyInterfaces(extensionId, legacyOperationCode);
        if (legacy.size() > 1) throw new IllegalArgumentException("Excel历史接口引用不唯一");
        return legacy.isEmpty() ? null : legacy.get(0);
    }

    /** 只读取当前可执行发布；历史 LIST 仍可被固定引用，因此保留其引用保护。 */
    public List<Snapshot> snapshots(String entityId) {
        List<Snapshot> result = new java.util.ArrayList<>();
        mapper.formSnapshots(entityId).forEach(row -> result.add(new Snapshot("FORM", row.getOwnerId(), row.getDocument(), true)));
        mapper.listSnapshots(entityId).forEach(row -> result.add(new Snapshot("LIST", row.getOwnerId(), row.getDocument(), true)));
        // 草稿接口配置只用于提示，不能使未发布的引用阻止停用线上方案。
        // GLOBAL 对所有实体提示；ENTITY 按完整集合匹配，旧 FORM/LIST 已在 SQL 中按所属实体筛选。
        mapper.interfaceSnapshots(entityId).stream()
                .filter(row -> !"ENTITY".equals(row.getScopeType()) || row.matchesScope(entityId, null, null))
                .forEach(row -> result.add(new Snapshot("INTERFACE", row.getId(), row.getImplementationConfigDocument(), false)));
        return result;
    }
}
