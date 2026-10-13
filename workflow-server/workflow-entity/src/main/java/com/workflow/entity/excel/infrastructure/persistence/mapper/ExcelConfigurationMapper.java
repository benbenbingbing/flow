package com.workflow.entity.excel.infrastructure.persistence.mapper;

import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.SchemeRow;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.SettingsRow;
import lombok.Data;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Excel 配置专用 SQL；发布涉及的多条语句由应用服务持有实体锁并在同一事务中执行。 */
@Mapper
public interface ExcelConfigurationMapper {
    String SCHEME_SELECT = "SELECT s.*, p.document_json, p.content_hash, p.write_form_id "
            + "FROM entity_excel_scheme s LEFT JOIN entity_excel_scheme_published p ON p.scheme_id=s.id ";

    @Select(SCHEME_SELECT + "WHERE s.entity_id=#{entityId} ORDER BY s.scheme_code")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SchemeRow> schemes(@Param("entityId") String entityId);

    @Select(SCHEME_SELECT + "WHERE s.entity_id=#{entityId} AND s.scheme_code=#{code}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    SchemeRow scheme(@Param("entityId") String entityId, @Param("code") String code);

    @Select("SELECT * FROM entity_excel_settings WHERE entity_id=#{entityId}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    SettingsRow settings(@Param("entityId") String entityId);

    @Select("SELECT COUNT(*) FROM sys_dict WHERE dict_code=#{code} AND status='0' AND deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    long availableDictionaries(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM entity_excel_scheme_published p JOIN entity_excel_scheme s ON s.id=p.scheme_id "
            + "WHERE p.write_form_id=#{formId} AND s.status='ACTIVE'")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    long publishedSchemesUsingForm(@Param("formId") String formId);

    @Insert("INSERT INTO entity_excel_scheme(id,entity_id,scheme_code,name,draft_json,revision,status,created_at,updated_at) "
            + "VALUES(#{id},#{entityId},#{schemeCode},#{name},#{draftJson},1,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int insertScheme(SchemeRow row);

    @Update("UPDATE entity_excel_scheme SET name=#{name},draft_json=#{draftJson},revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateScheme(SchemeRow row);

    @Delete("DELETE FROM entity_excel_scheme_published WHERE scheme_id=#{id}")
    int deletePublished(@Param("id") String id);

    @Insert("INSERT INTO entity_excel_scheme_published(scheme_id,document_json,content_hash,write_form_id,published_by,published_at) "
            + "VALUES(#{id},#{document},#{hash},#{formId,jdbcType=VARCHAR},#{userId,jdbcType=VARCHAR},CURRENT_TIMESTAMP)")
    int insertPublished(@Param("id") String id, @Param("document") String document, @Param("hash") String hash,
            @Param("formId") String formId, @Param("userId") String userId);

    @Update("UPDATE entity_excel_scheme SET status=#{status},revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int status(@Param("id") String id, @Param("status") String status);

    @Update("UPDATE entity_excel_scheme_published SET write_form_id=#{formId,jdbcType=VARCHAR},content_hash=#{hash},"
            + "published_by=#{userId,jdbcType=VARCHAR},published_at=CURRENT_TIMESTAMP WHERE scheme_id=#{id}")
    int rebindPublishedForm(@Param("id") String id, @Param("formId") String formId, @Param("hash") String hash,
            @Param("userId") String userId);

    @Delete("DELETE FROM entity_excel_scheme WHERE id=#{id}")
    int deleteScheme(@Param("id") String id);

    @Update("UPDATE entity_excel_scheme SET revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int bump(@Param("id") String id);

    @Update("UPDATE entity_excel_settings SET draft_json=#{document},revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE entity_id=#{entityId}")
    int updateSettings(@Param("entityId") String entityId, @Param("document") String document);

    @Insert("INSERT INTO entity_excel_settings(entity_id,draft_json,revision,updated_at) VALUES(#{entityId},#{document},1,CURRENT_TIMESTAMP)")
    int insertSettings(@Param("entityId") String entityId, @Param("document") String document);

    @Update("UPDATE entity_excel_settings SET published_json=#{document},import_hash=#{importHash,jdbcType=VARCHAR},"
            + "export_hash=#{exportHash,jdbcType=VARCHAR},published_by=#{userId,jdbcType=VARCHAR},published_at=CURRENT_TIMESTAMP,"
            + "revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE entity_id=#{entityId}")
    int publishSettings(@Param("entityId") String entityId, @Param("document") String document,
            @Param("importHash") String importHash, @Param("exportHash") String exportHash, @Param("userId") String userId);

    /** 即使当前身份属于其他 Provider 也返回该行，防止错误回退到历史接口身份。 */
    @Select("SELECT provider_code,implementation_config_document AS document FROM ui_extension_definition "
            + "WHERE id=#{id} AND extension_type='INTERFACE' AND deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    InterfaceRow currentInterface(@Param("id") String id);

    @Select("SELECT implementation_config_document FROM ui_extension_definition WHERE legacy_service_id=#{id} "
            + "AND provider_operation_code=#{operation} AND provider_code='entity_excel_action' AND extension_type='INTERFACE' AND deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> legacyInterfaces(@Param("id") String id, @Param("operation") String operation);

    @Select("SELECT form_id AS owner_id,snapshot_document AS document FROM entity_form_published WHERE entity_id=#{entityId}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SnapshotRow> formSnapshots(@Param("entityId") String entityId);

    @Select("SELECT r.config_id AS owner_id,r.snapshot_document AS document FROM ui_config_release r "
            + "JOIN entity_list_config l ON l.id=r.config_id WHERE r.config_type='LIST' AND l.entity_id=#{entityId} AND l.deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<SnapshotRow> listSnapshots(@Param("entityId") String entityId);

    /** ENTITY 数组成员在 Java 中匹配，SQL 只预筛选实现与历史页面归属，保持数据库方言无关。 */
    @Select("SELECT id,implementation_config_document,scope_type,scope_id,scope_entity_ids_document FROM ui_extension_definition "
            + "WHERE extension_type='INTERFACE' AND provider_code='entity_excel_action' AND deleted=0 "
            + "AND (scope_type IN ('GLOBAL','ENTITY') "
            + "OR (scope_type='FORM' AND scope_id IN (SELECT id FROM entity_form WHERE entity_id=#{entityId} AND deleted=0)) "
            + "OR (scope_type='LIST' AND scope_id IN (SELECT id FROM entity_list_config WHERE entity_id=#{entityId} AND deleted=0)))")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<UiExtensionDefinition> interfaceSnapshots(@Param("entityId") String entityId);

    @Data class InterfaceRow { private String providerCode; private String document; }
    @Data class SnapshotRow { private String ownerId; private String document; }
}
