package com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper;

import com.workflow.contracts.identity.model.*;
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

    @Select("SELECT * FROM sys_identity_source_binding WHERE object_type = #{type} AND local_id = #{id}")
    Map<String, Object> selectLocal(@Param("type") IdentityObjectType type, @Param("id") String id);

    default Map<String, Object> findLocal(IdentityObjectType type, String id) { return row(selectLocal(type, id)); }

    @SelectProvider(type = IdentitySqlProvider.class, method = "findObject")
    Map<String, Object> selectObject(@Param("type") IdentityObjectType type, @Param("id") String id);

    default Map<String, Object> findObject(IdentityObjectType type, String id) { return row(selectObject(type, id)); }

    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{id} AND status = '0' AND deleted = 0")
    int activeUser(String id);

    @Update("UPDATE sys_identity_source_binding SET local_allowed = #{allowed}, updated_at = CURRENT_TIMESTAMP WHERE source_key = #{key}")
    int localStatus(@Param("key") String key, @Param("allowed") boolean allowed);

    @Update("UPDATE sys_identity_source_binding SET binding_status = 'RETIRED', updated_at = CURRENT_TIMESTAMP WHERE object_type = #{type} AND local_id = #{id}")
    int retire(@Param("type") IdentityObjectType type, @Param("id") String id);

    /** 新建外部账号没有密码、角色或权限，认证模式不能由业务参数指定。 */
    @Insert("INSERT INTO sys_user (id, username, nickname, password, authentication_mode, email, phone, avatar, status, org_id, dept_id, password_reset_required, token_version, deleted, create_time, update_time) VALUES (#{id}, #{username}, #{base.nickname}, NULL, 'EXTERNAL_ONLY', #{base.email}, #{base.phone}, #{base.avatar}, #{status}, #{org}, #{dept}, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertUser(@Param("id") String id, @Param("username") String username, @Param("base") UserBaseData base,
            @Param("status") String status, @Param("org") String org, @Param("dept") String dept);

    /** 完整基础快照的 null 需要实际写入，不使用 ORM 忽略 null 的更新策略。 */
    @Update("UPDATE sys_user SET nickname = #{base.nickname}, email = #{base.email}, phone = #{base.phone}, avatar = #{base.avatar}, status = #{status}, org_id = #{org}, dept_id = #{dept}, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int updateUser(@Param("id") String id, @Param("base") UserBaseData base,
            @Param("status") String status, @Param("org") String org, @Param("dept") String dept);

    @Update("UPDATE sys_organization SET phone = #{base.phone}, email = #{base.email}, address = #{base.address}, description = #{base.description} WHERE id = #{id}")
    int clearableOrganizationFields(@Param("id") String id, @Param("base") OrganizationBaseData base);

    @Insert("INSERT INTO sys_identity_source_binding (source_key, source_code, object_type, external_id, local_id, external_account, source_version, payload_hash, source_enabled, local_allowed, binding_status, created_at, updated_at) VALUES (#{v.source_key}, #{v.source_code}, #{v.object_type}, #{v.external_id}, #{v.local_id}, #{v.external_account}, #{v.source_version}, #{v.payload_hash}, #{v.source_enabled}, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertBinding(@Param("v") Map<String, Object> values);

    @Update("UPDATE sys_identity_source_binding SET external_account = #{v.external_account}, source_version = #{v.source_version}, payload_hash = #{v.payload_hash}, source_enabled = #{v.source_enabled}, updated_at = CURRENT_TIMESTAMP WHERE source_key = #{v.source_key}")
    int updateBinding(@Param("v") Map<String, Object> values);

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
