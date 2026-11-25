package com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper;

import com.workflow.port.system.model.identity.*;
import org.apache.ibatis.annotations.*;
import java.util.Map;
import java.util.List;

/** 身份基础写入与来源映射统一使用 Mapper，动态表仅由经过校验的扩展注册清单提供。 */
@Mapper
public interface IdentityPersistenceMapper {
    /** 事务锁与基础/扩展更新共用业务连接，事务提交后才释放。 */
    @Select("SELECT lock_key FROM sys_identity_write_lock WHERE lock_key = 'directory' FOR UPDATE")
    String lockDirectory();

    @Select("SELECT * FROM sys_identity_source_binding WHERE source_key = #{key}")
    Map<String, Object> selectBinding(String key);

    default Map<String, Object> findBinding(String key) { return row(selectBinding(key)); }

    /** 批量投影活动映射，不读取来源账号或原始资料。 */
    @SelectProvider(type = IdentitySqlProvider.class, method = "findSourceIds")
    List<SourceId> findSourceIds(@Param("type") IdentityObjectType type, @Param("keys") List<String> keys);

    record SourceId(String sourceKey, String localId) { }

    @Select("SELECT * FROM sys_identity_source_binding WHERE object_type = #{type} AND local_id = #{id}")
    Map<String, Object> selectLocal(@Param("type") IdentityObjectType type, @Param("id") String id);

    default Map<String, Object> findLocal(IdentityObjectType type, String id) { return row(selectLocal(type, id)); }

    @SelectProvider(type = IdentitySqlProvider.class, method = "findObject")
    Map<String, Object> selectObject(@Param("type") IdentityObjectType type, @Param("id") String id);

    default Map<String, Object> findObject(IdentityObjectType type, String id) { return row(selectObject(type, id)); }

    /** 按全局唯一编码查找写入目标，包含逻辑删除的记录以区分真正的新建和已有编码冲突。 */
    @SelectProvider(type = IdentitySqlProvider.class, method = "selectObjectByCode")
    Map<String, Object> selectObjectByCode(@Param("type") IdentityObjectType type, @Param("code") String code);

    default Map<String, Object> findObjectByCode(IdentityObjectType type, String code) {
        return row(selectObjectByCode(type, code));
    }

    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{id} AND status = '0' AND deleted = 0")
    int activeUser(String id);

    @Update("UPDATE sys_identity_source_binding SET local_allowed = #{allowed}, updated_at = CURRENT_TIMESTAMP WHERE source_key = #{key}")
    int localStatus(@Param("key") String key, @Param("allowed") boolean allowed);

    @Update("UPDATE sys_identity_source_binding SET binding_status = 'RETIRED', updated_at = CURRENT_TIMESTAMP WHERE object_type = #{type} AND local_id = #{id}")
    int retire(@Param("type") IdentityObjectType type, @Param("id") String id);

    /** 新建外部账号使用无密码认证模式，来源默认角色由应用服务在同一事务中单独分配。 */
    @Insert("INSERT INTO sys_user (id, username, nickname, password, authentication_mode, email, phone, avatar, status, org_id, dept_id, password_reset_required, token_version, deleted, create_time, update_time) VALUES (#{id}, #{username}, #{base.nickname}, NULL, 'EXTERNAL_ONLY', #{base.email}, #{base.phone}, #{base.avatar}, #{status}, #{org}, #{dept}, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertUser(@Param("id") String id, @Param("username") String username, @Param("base") UserBaseData base,
            @Param("status") String status, @Param("org") String org, @Param("dept") String dept);

    /** 完整基础快照的 null 需要实际写入，不使用 ORM 忽略 null 的更新策略。 */
    @Update("UPDATE sys_user SET nickname = #{base.nickname}, email = #{base.email}, phone = #{base.phone}, avatar = #{base.avatar}, status = #{status}, org_id = #{org}, dept_id = #{dept}, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int updateUser(@Param("id") String id, @Param("base") UserBaseData base,
            @Param("status") String status, @Param("org") String org, @Param("dept") String dept);

    @Update("UPDATE sys_organization SET phone = #{base.phone}, email = #{base.email}, address = #{base.address}, description = #{base.description} WHERE id = #{id}")
    int clearableOrganizationFields(@Param("id") String id, @Param("base") OrganizationBaseData base);

    /** 来源显式携带本组时才调用；参数化写入五列，NULL 必须真实清空，不能走 ORM 忽略空值策略。 */
    @Update("UPDATE sys_user SET ext1 = #{fields.ext1,jdbcType=VARCHAR}, ext2 = #{fields.ext2,jdbcType=VARCHAR}, ext3 = #{fields.ext3,jdbcType=VARCHAR}, ext4 = #{fields.ext4,jdbcType=VARCHAR}, ext5 = #{fields.ext5,jdbcType=VARCHAR} WHERE id = #{id} AND deleted = 0")
    int updateUserMainFields(@Param("id") String id, @Param("fields") IdentityMainFields fields);

    /** 来源组织与用户采用相同文本快照语义，保持字段更新与组织树、来源映射的事务一致性。 */
    @Update("UPDATE sys_organization SET ext1 = #{fields.ext1,jdbcType=VARCHAR}, ext2 = #{fields.ext2,jdbcType=VARCHAR}, ext3 = #{fields.ext3,jdbcType=VARCHAR}, ext4 = #{fields.ext4,jdbcType=VARCHAR}, ext5 = #{fields.ext5,jdbcType=VARCHAR} WHERE id = #{id} AND deleted = 0")
    int updateOrganizationMainFields(@Param("id") String id, @Param("fields") IdentityMainFields fields);

    @Insert("INSERT INTO sys_identity_source_binding (source_key, source_code, object_type, external_id, local_id, external_account, source_version, payload_hash, source_enabled, local_allowed, binding_status, created_at, updated_at) VALUES (#{v.source_key}, #{v.source_code}, #{v.object_type}, #{v.external_id}, #{v.local_id}, #{v.external_account}, #{v.source_version}, #{v.payload_hash}, #{v.source_enabled}, #{v.local_allowed}, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertBinding(@Param("v") Map<String, Object> values);

    @Update("UPDATE sys_identity_source_binding SET external_account = #{v.external_account}, source_version = #{v.source_version}, payload_hash = #{v.payload_hash}, source_enabled = #{v.source_enabled}, updated_at = CURRENT_TIMESTAMP WHERE source_key = #{v.source_key}")
    int updateBinding(@Param("v") Map<String, Object> values);

    /** 迁移来源键时沿用同一行的本地归属、权限、版本及首次绑定时间。 */
    @Update("""
            UPDATE sys_identity_source_binding SET source_key = #{newKey}, external_id = #{externalId}, updated_at = CURRENT_TIMESTAMP
            WHERE source_key = #{oldKey} AND local_id = #{localId} AND binding_status = 'ACTIVE'
            """)
    int moveBinding(@Param("oldKey") String oldKey, @Param("newKey") String newKey, @Param("externalId") String externalId,
                    @Param("localId") String localId);

    /** 覆盖已关联和仍待关联的子级；本地树继续以原组织 ID 为父级。 */
    @Update("""
            UPDATE sys_identity_source_binding SET parent_external_id = #{externalId}, parent_source_key = #{newKey},
                updated_at = CURRENT_TIMESTAMP WHERE object_type = 'ORGANIZATION' AND parent_source_key = #{oldKey}
            """)
    int moveOrganizationParentReferences(@Param("oldKey") String oldKey, @Param("newKey") String newKey,
                                         @Param("externalId") String externalId);

    /** 父级外部 ID 用于追溯来源快照，哈希引用用于精确匹配后到达的父级，null 表示来源根节点。 */
    @Update("""
            UPDATE sys_identity_source_binding SET parent_external_id = #{parentExternalId}, parent_source_key = #{parentSourceKey}
            WHERE source_key = #{key}
            """)
    int updateOrganizationParent(@Param("key") String key, @Param("parentExternalId") String parentExternalId,
                                 @Param("parentSourceKey") String parentSourceKey);

    /** 只关联活动且未删除的来源子级，哈希查询避免数据库默认排序规则混淆外部 ID 大小写。 */
    @Select("""
            SELECT binding.local_id FROM sys_identity_source_binding binding
            JOIN sys_organization org ON org.id = binding.local_id AND org.deleted = 0
            WHERE binding.object_type = 'ORGANIZATION' AND binding.binding_status = 'ACTIVE'
              AND binding.parent_source_key = #{parentSourceKey}
            ORDER BY binding.local_id
            """)
    List<String> selectOrganizationChildren(String parentSourceKey);

    @SelectProvider(type = IdentitySqlProvider.class, method = "readExtension")
    Map<String, Object> selectExtension(@Param("table") String table, @Param("owner") String owner, @Param("id") String id);

    default Map<String, Object> readExtension(String table, String owner, String id) { return row(selectExtension(table, owner, id)); }

    @InsertProvider(type = IdentitySqlProvider.class, method = "insertExtension")
    int insertExtension(@Param("table") String table, @Param("owner") String owner, @Param("id") String id,
            @Param("values") Map<String, Object> columns);

    @UpdateProvider(type = IdentitySqlProvider.class, method = "updateExtension")
    int updateExtension(@Param("table") String table, @Param("owner") String owner, @Param("id") String id,
            @Param("values") Map<String, Object> columns, @Param("expected") long expected);

    /** 驱动可能返回大写列标签，统一使用不区分大小写的行视图，业务字段名保持稳定。 */
    private static Map<String, Object> row(Map<String, Object> raw) {
        if (raw == null) return null;
        var normalized = new java.util.TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        normalized.putAll(raw);
        return normalized;
    }
}
