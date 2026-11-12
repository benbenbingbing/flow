package com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.record.IdentitySourceSnapshot;
import com.workflow.contracts.identity.model.IdentityObjectType;
import org.apache.ibatis.annotations.*;

/** 原文独立于用户、组织列表和来源绑定；写入方必须先持有目录事务锁。 */
@Mapper
public interface IdentitySourceSnapshotMapper {
    /** 去重仅查询小字段，不把历史大文本加载到每次同步事务中。 */
    @Select("SELECT source_key, source_version, payload_hash, raw_hash, raw_bytes, is_current FROM sys_identity_source_snapshot WHERE source_key = #{key}")
    @Results(id = "snapshotMetadata", value = {
        @Result(column = "source_key", property = "sourceKey"),
        @Result(column = "source_version", property = "sourceVersion"),
        @Result(column = "payload_hash", property = "payloadHash"),
        @Result(column = "raw_hash", property = "rawHash"),
        @Result(column = "raw_bytes", property = "rawBytes"),
        @Result(column = "is_current", property = "isCurrent")
    })
    IdentitySourceSnapshot metadata(String key);

    /** 单次联查提供一致的版本和原文视图；只能在登录、管理权限与对象范围校验后调用。 */
    @Select("""
        SELECT b.source_code, b.external_id, b.source_version AS current_source_version,
               s.source_key, s.source_version, s.payload_hash, s.raw_json, s.raw_hash,
               s.raw_bytes, s.is_current, s.recorded_at, s.recorded_by
        FROM sys_identity_source_binding b LEFT JOIN sys_identity_source_snapshot s ON s.source_key = b.source_key
        WHERE b.object_type = #{type} AND b.local_id = #{id}
        """)
    @Results(value = {
        @Result(column = "source_code", property = "sourceCode"),
        @Result(column = "external_id", property = "externalId"),
        @Result(column = "current_source_version", property = "currentSourceVersion"),
        @Result(column = "source_key", property = "sourceKey"),
        @Result(column = "source_version", property = "sourceVersion"),
        @Result(column = "payload_hash", property = "payloadHash"),
        @Result(column = "raw_json", property = "rawJson", typeHandler = IdentityRawJsonTypeHandler.class),
        @Result(column = "raw_hash", property = "rawHash"),
        @Result(column = "raw_bytes", property = "rawBytes"),
        @Result(column = "is_current", property = "isCurrent"),
        @Result(column = "recorded_at", property = "recordedAt"),
        @Result(column = "recorded_by", property = "recordedBy")
    })
    IdentitySourceSnapshot read(@Param("type") IdentityObjectType type, @Param("id") String id);

    @Insert("""
        INSERT INTO sys_identity_source_snapshot
        (source_key, source_version, payload_hash, raw_json, raw_hash, raw_bytes, is_current, recorded_at, recorded_by)
        VALUES (#{sourceKey}, #{sourceVersion,jdbcType=BIGINT}, #{payloadHash},
        #{rawJson,typeHandler=com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityRawJsonTypeHandler},
        #{rawHash}, #{rawBytes}, 1, CURRENT_TIMESTAMP, #{recordedBy})
        """)
    int insert(IdentitySourceSnapshot snapshot);

    @Update("""
        UPDATE sys_identity_source_snapshot SET source_version = #{sourceVersion,jdbcType=BIGINT}, payload_hash = #{payloadHash},
        raw_json = #{rawJson,typeHandler=com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityRawJsonTypeHandler},
        raw_hash = #{rawHash}, raw_bytes = #{rawBytes}, is_current = 1, recorded_at = CURRENT_TIMESTAMP, recorded_by = #{recordedBy}
        WHERE source_key = #{sourceKey}
        """)
    int update(IdentitySourceSnapshot snapshot);

    /** 无原文的业务更新只能使旧原文过期，不改写旧记录的采集人和采集时间。 */
    @Update("UPDATE sys_identity_source_snapshot SET is_current = 0 WHERE source_key = #{key} AND is_current = 1")
    int markStale(String key);
}
