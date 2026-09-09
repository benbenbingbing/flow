package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionRolloutState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 数据版本单配置模型滚动发布期间的新旧存储双写桥。
 *
 * <p>N 版应用查询优先解析有效的 active release，以兼容旧 Pod 的发布结果；新保存
 * 同时通过本桥投影旧存储，让混部节点观察到同一份配置，不重新开放发布历史业务。
 * 最终投影、对账有效 active release 后，N+1 改为 config-only 读取，但仍保留本桥
 * 执行兼容写入；N+2 才删除本 Mapper，并在旧 schema 保留期间完成滚动、等待所有
 * N+1 Pod 和在途事务退出；N+3 才执行 pre-upgrade contract，删除旧表、旧字段和
 * 发布权限。</p>
 */
@Mapper
public interface EntityVersionRolloutBridgeMapper {

    /** 一次读取旧草稿和当前文档，供兼容路由判断是否需要接管。 */
    @Select("""
            SELECT id,
                   entity_id,
                   entity_code,
                   enabled,
                   active_release_id,
                   revision,
                   status,
                   draft_document,
                   config_document,
                   update_by,
                   update_time
            FROM entity_version_config
            WHERE entity_code = #{entityCode}
              AND deleted = 0
            LIMIT 1
            """)
    EntityVersionRolloutState findStateByEntityCode(
            @Param("entityCode") String entityCode);

    /** 旧草稿响应只暴露 active release 的版本号，不读取历史文档。 */
    @Select("""
            SELECT version
            FROM entity_version_config_release
            WHERE id = #{releaseId}
              AND config_id = #{configId}
            LIMIT 1
            """)
    Integer findReleaseVersion(
            @Param("releaseId") String releaseId,
            @Param("configId") String configId);

    /** 首次由旧页面保存时仅创建 draft-only 占位，不能提前改变新运行时。 */
    @Insert("""
            INSERT INTO entity_version_config (
                id,
                entity_id,
                entity_code,
                enabled,
                contract_version,
                draft_document,
                config_document,
                revision,
                status,
                migration_state,
                create_by,
                create_time,
                update_by,
                update_time,
                deleted
            ) VALUES (
                #{configId},
                #{entityId},
                #{entityCode},
                #{enabled},
                #{contractVersion},
                #{draftDocument},
                NULL,
                1,
                'DRAFT',
                'MIGRATED',
                #{userId},
                CURRENT_TIMESTAMP,
                #{userId},
                CURRENT_TIMESTAMP,
                0
            )
            """)
    int insertLegacyDraft(
            @Param("configId") String configId,
            @Param("entityId") String entityId,
            @Param("entityCode") String entityCode,
            @Param("enabled") Boolean enabled,
            @Param("contractVersion") Integer contractVersion,
            @Param("draftDocument") String draftDocument,
            @Param("userId") String userId);

    /** 旧保存动作只推进草稿 revision，不写 config_document 或 active release。 */
    @Update("""
            UPDATE entity_version_config
            SET enabled = #{enabled},
                contract_version = #{contractVersion},
                draft_document = #{draftDocument},
                revision = revision + 1,
                status = 'DRAFT',
                migration_state = 'MIGRATED',
                update_by = #{userId},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{configId}
              AND revision = #{expectedRevision}
              AND deleted = 0
            """)
    int updateLegacyDraftIfRevision(
            @Param("configId") String configId,
            @Param("expectedRevision") Integer expectedRevision,
            @Param("enabled") Boolean enabled,
            @Param("contractVersion") Integer contractVersion,
            @Param("draftDocument") String draftDocument,
            @Param("userId") String userId);

    /**
     * 同步旧草稿信封，但不增加 revision；revision 已由当前配置的 CAS 写入增加。
     */
    @Update("""
            UPDATE entity_version_config
            SET draft_document = #{configDocument},
                contract_version = #{contractVersion},
                migration_state = 'MIGRATED',
                status = 'PUBLISHED',
                update_by = #{updateBy},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{configId}
              AND revision = #{savedRevision}
              AND deleted = 0
            """)
    int syncLegacyDraft(
            @Param("configId") String configId,
            @Param("savedRevision") Integer savedRevision,
            @Param("contractVersion") Integer contractVersion,
            @Param("configDocument") String configDocument,
            @Param("updateBy") String updateBy);

    /** 主配置 CAS 已持有配置行锁，旧发布也先锁该行，因此版本号不会并发碰撞。 */
    @Select("""
            SELECT COALESCE(MAX(version), 0) + 1
            FROM entity_version_config_release
            WHERE config_id = #{configId}
            """)
    Integer findNextReleaseVersion(
            @Param("configId") String configId);

    /** 每次当前配置保存都创建新的不可变过渡快照，保护历史版本的追溯语义。 */
    @Insert("""
            INSERT INTO entity_version_config_release (
                id,
                config_id,
                version,
                contract_version,
                config_document,
                scope_hash,
                published_by,
                published_by_name,
                publish_time,
                create_time
            ) VALUES (
                #{releaseId},
                #{configId},
                #{releaseVersion},
                #{contractVersion},
                #{configDocument},
                #{scopeHash},
                #{publishedBy},
                #{publishedByName},
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            """)
    int insertCompatibilityRelease(
            @Param("releaseId") String releaseId,
            @Param("configId") String configId,
            @Param("releaseVersion") Integer releaseVersion,
            @Param("contractVersion") Integer contractVersion,
            @Param("configDocument") String configDocument,
            @Param("scopeHash") String scopeHash,
            @Param("publishedBy") String publishedBy,
            @Param("publishedByName") String publishedByName);

    /** 把新建兼容快照切为旧运行时 active；不再增加已由主 CAS 更新的 revision。 */
    @Update("""
            UPDATE entity_version_config
            SET active_release_id = #{releaseId},
                status = 'PUBLISHED',
                update_by = #{updateBy},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{configId}
              AND revision = #{savedRevision}
              AND deleted = 0
            """)
    int activateCompatibilityRelease(
            @Param("configId") String configId,
            @Param("savedRevision") Integer savedRevision,
            @Param("releaseId") String releaseId,
            @Param("updateBy") String updateBy);
}
