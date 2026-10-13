package com.workflow.entity.excel.persistence;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Excel 配置持久化。写操作由应用服务在实体行锁内调用，所有参数均使用绑定变量。 */
@Repository
@RequiredArgsConstructor
public class ExcelConfigurationRepository {
    private final JdbcTemplate jdbc;

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

    private static final String SCHEME_SELECT = "SELECT s.*, p.document_json, p.content_hash, p.write_form_id "
            + "FROM entity_excel_scheme s LEFT JOIN entity_excel_scheme_published p ON p.scheme_id=s.id ";

    public List<SchemeRow> schemes(String entityId) {
        return jdbc.query(SCHEME_SELECT + "WHERE s.entity_id=? ORDER BY s.scheme_code",
                BeanPropertyRowMapper.newInstance(SchemeRow.class), entityId);
    }
    public SchemeRow scheme(String entityId, String code) {
        return jdbc.query(SCHEME_SELECT + "WHERE s.entity_id=? AND s.scheme_code=?",
                BeanPropertyRowMapper.newInstance(SchemeRow.class), entityId, code).stream().findFirst().orElse(null);
    }
    public SettingsRow settings(String entityId) {
        return jdbc.query("SELECT * FROM entity_excel_settings WHERE entity_id=?",
                BeanPropertyRowMapper.newInstance(SettingsRow.class), entityId).stream().findFirst().orElse(null);
    }
    public boolean dictionaryAvailable(String code) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict WHERE dict_code=? AND status='0' AND deleted=0", Long.class, code);
        return count != null && count > 0;
    }
    public boolean publishedSchemeUsesForm(String formId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_scheme_published p JOIN entity_excel_scheme s ON s.id=p.scheme_id "
                + "WHERE p.write_form_id=? AND s.status='ACTIVE'", Long.class, formId);
        return count != null && count > 0;
    }
    public void insertScheme(SchemeRow row) {
        jdbc.update("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,revision,status,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,1,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                row.id, row.entityId, row.schemeCode, row.name, row.draftJson);
    }
    public void updateScheme(SchemeRow row) {
        jdbc.update("UPDATE entity_excel_scheme SET name=?,draft_json=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                row.name, row.draftJson, row.id);
    }
    public void publish(SchemeRow row, String document, String hash, String formId, String userId) {
        // 删除再插入位于同一事务和实体锁中，读者不会看到中间状态，避免依赖特定数据库的 upsert 语法。
        jdbc.update("DELETE FROM entity_excel_scheme_published WHERE scheme_id=?", row.id);
        jdbc.update("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,write_form_id,published_by,published_at) "
                + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)", row.id, document, hash, formId, userId);
        bump(row.id);
    }
    public void status(String id, String status) {
        jdbc.update("UPDATE entity_excel_scheme SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
    }

    /** 启用停用方案时恢复可执行表单身份；调用方持发布锁与实体锁，revision由同事务的status更新一次。 */
    public void rebindPublishedForm(String id, String formId, String hash, String userId) {
        int updated = jdbc.update("UPDATE entity_excel_scheme_published SET write_form_id=?,content_hash=?,published_by=?,published_at=CURRENT_TIMESTAMP WHERE scheme_id=?",
                formId, hash, userId, id);
        if (updated != 1) throw new IllegalStateException("Excel方案发布文档不存在，不能恢复写入表单");
    }
    public void delete(String id) {
        jdbc.update("DELETE FROM entity_excel_scheme_published WHERE scheme_id=?", id);
        jdbc.update("DELETE FROM entity_excel_scheme WHERE id=?", id);
    }
    private void bump(String id) {
        jdbc.update("UPDATE entity_excel_scheme SET revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }
    public void saveSettings(String entityId, String document, boolean exists) {
        if (exists) {
            jdbc.update("UPDATE entity_excel_settings SET draft_json=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE entity_id=?", document, entityId);
        } else {
            jdbc.update("INSERT INTO entity_excel_settings(entity_id,draft_json,revision,updated_at) VALUES(?,?,1,CURRENT_TIMESTAMP)", entityId, document);
        }
    }
    public void publishSettings(String entityId, String document, String importHash, String exportHash, String userId) {
        jdbc.update("UPDATE entity_excel_settings SET published_json=?,import_hash=?,export_hash=?,published_by=?,published_at=CURRENT_TIMESTAMP,"
                        + "revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE entity_id=?",
                document, importHash, exportHash, userId, entityId);
    }

    /**
     * LIST 事件仍按当前接口定义执行；从服务端步骤身份解析 Excel 配置用于发布校验和引用保护。
     * 这里只读取对应 Provider，不要求配置管理员权限；调用方已限定所属实体的可信页面快照。
     */
    public String excelInterfaceConfiguration(String extensionId, String legacyOperationCode) {
        if (extensionId == null || extensionId.isBlank()) return null;
        List<String> current = jdbc.query("SELECT provider_code,implementation_config_document FROM ui_extension_definition "
                        + "WHERE id=? AND extension_type='INTERFACE' AND deleted=0",
                (rs, index) -> "entity_excel_action".equals(rs.getString(1)) ? rs.getString(2) : null, extensionId);
        if (!current.isEmpty()) return current.get(0);
        if (legacyOperationCode == null || legacyOperationCode.isBlank()) return null;
        List<String> legacy = jdbc.query("SELECT implementation_config_document FROM ui_extension_definition "
                        + "WHERE legacy_service_id=? AND provider_operation_code=? AND provider_code='entity_excel_action' "
                        + "AND extension_type='INTERFACE' AND deleted=0", (rs, index) -> rs.getString(1), extensionId, legacyOperationCode);
        if (legacy.size() > 1) throw new IllegalArgumentException("Excel历史接口引用不唯一");
        return legacy.isEmpty() ? null : legacy.get(0);
    }

    /** 只读取当前可执行发布；历史 LIST 仍可被固定引用，因此保留其引用保护。 */
    public List<Snapshot> snapshots(String entityId) {
        List<Snapshot> result = new java.util.ArrayList<>(jdbc.query(
                "SELECT form_id,snapshot_document FROM entity_form_published WHERE entity_id=?",
                (rs, i) -> new Snapshot("FORM", rs.getString(1), rs.getString(2), true), entityId));
        result.addAll(jdbc.query("SELECT r.config_id,r.snapshot_document FROM ui_config_release r "
                        + "JOIN entity_list_config l ON l.id=r.config_id WHERE r.config_type='LIST' AND l.entity_id=? AND l.deleted=0",
                (rs, i) -> new Snapshot("LIST", rs.getString(1), rs.getString(2), true), entityId));
        // 草稿接口配置只用于提示，不能使未发布的引用阻止停用线上方案。
        result.addAll(jdbc.query("SELECT id,implementation_config_document FROM ui_extension_definition WHERE provider_code='entity_excel_action' AND deleted=0 "
                        + "AND ((scope_type='ENTITY' AND scope_id=?) OR (scope_type='FORM' AND scope_id IN (SELECT id FROM entity_form WHERE entity_id=? AND deleted=0)) "
                        + "OR (scope_type='LIST' AND scope_id IN (SELECT id FROM entity_list_config WHERE entity_id=? AND deleted=0)))",
                (rs, i) -> new Snapshot("INTERFACE", rs.getString(1), rs.getString(2), false), entityId, entityId, entityId));
        return result;
    }
}
