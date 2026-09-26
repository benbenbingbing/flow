package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.OffsetPage;
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
// 旧模型查询保留业务 SQL，首行限制由 MyBatis-Plus 分页插件生成。
@Mapper
public interface EntityVersionRolloutBridgeMapper {

    /**
     * 一次读取旧草稿和当前文档，供兼容路由判断是否需要接管。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体版本灰度发布状态结果，供调用方继续处理
     */
    default EntityVersionRolloutState findStateByEntityCode(String entityCode) {
        return findStateByEntityCodeRows(new OffsetPage<>(0, 1), entityCode);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体版本灰度发布状态结果，供调用方继续处理
     */
    @Select("""
            <script>
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

            </script>
            """)
    EntityVersionRolloutState findStateByEntityCodeRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("entityCode") String entityCode);

    /**
     * 旧草稿响应只暴露 active release 的版本号，不读取历史文档。
     *
     * @param releaseId 发布版本ID，后续用于查询发布版本时定位或关联目标
     * @param configId 配置ID，后续用于查询发布版本时定位或关联目标
     * @return 符合条件的实体版本灰度发布{@code bridge}结果，供调用方继续处理
     */
    default Integer findReleaseVersion(String releaseId, String configId) {
        return findReleaseVersionRows(new OffsetPage<>(0, 1), releaseId, configId);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param releaseId 发布版本ID，后续用于查询发布版本行时定位或关联目标
     * @param configId 配置ID，后续用于查询发布版本行时定位或关联目标
     * @return 符合条件的实体版本灰度发布{@code bridge}结果，供调用方继续处理
     */
    @Select("""
            <script>
            SELECT version
            FROM entity_version_config_release
            WHERE id = #{releaseId}
              AND config_id = #{configId}

            </script>
            """)
    Integer findReleaseVersionRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("releaseId") String releaseId,
            @Param("configId") String configId);

    /**
     * 首次由旧页面保存时仅创建 draft-only 占位，不能提前改变新运行时。
     *
     * @param configId 配置ID，后续用于插入旧版草稿时定位或关联目标
     * @param entityId 实体ID，后续用于插入旧版草稿时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param enabled 启用，供本方法插入旧版草稿时使用
     * @param contractVersion 契约版本，供本方法插入旧版草稿时使用
     * @param draftDocument 草稿文档，供本方法插入旧版草稿时使用
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 插入后的旧版草稿结果，供调用方继续处理
     */
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

    /**
     * 旧保存动作只推进草稿 revision，不写 config_document 或 active release。
     *
     * @param configId 配置ID，后续用于更新旧版草稿条件修订版本时定位或关联目标
     * @param expectedRevision 预期修订版本，供本方法更新旧版草稿条件修订版本时使用
     * @param enabled 启用，供本方法更新旧版草稿条件修订版本时使用
     * @param contractVersion 契约版本，供本方法更新旧版草稿条件修订版本时使用
     * @param draftDocument 草稿文档，供本方法更新旧版草稿条件修订版本时使用
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 更新后的旧版草稿条件修订版本结果，供调用方继续处理
     */
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
     *
     * @param configId 配置ID，后续用于处理同步旧版草稿时定位或关联目标
     * @param savedRevision {@code saved}修订版本，供本方法处理同步旧版草稿时使用
     * @param contractVersion 契约版本，供本方法处理同步旧版草稿时使用
     * @param configDocument 配置文档，供本方法处理同步旧版草稿时使用
     * @param updateBy 更新，供本方法处理同步旧版草稿时使用
     * @return 处理后的同步旧版草稿结果，供调用方继续处理
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

    /**
     * 主配置 CAS 已持有配置行锁，旧发布也先锁该行，因此版本号不会并发碰撞。
     *
     * @param configId 配置ID，后续用于查询下一步发布版本时定位或关联目标
     * @return 符合条件的实体版本灰度发布{@code bridge}结果，供调用方继续处理
     */
    @Select("""
            SELECT COALESCE(MAX(version), 0) + 1
            FROM entity_version_config_release
            WHERE config_id = #{configId}
            """)
    Integer findNextReleaseVersion(
            @Param("configId") String configId);

    /**
     * 每次当前配置保存都创建新的不可变过渡快照，保护历史版本的追溯语义。
     *
     * @param releaseId 发布版本ID，后续用于插入兼容性发布版本时定位或关联目标
     * @param configId 配置ID，后续用于插入兼容性发布版本时定位或关联目标
     * @param releaseVersion 发布版本，供本方法插入兼容性发布版本时使用
     * @param contractVersion 契约版本，供本方法插入兼容性发布版本时使用
     * @param configDocument 配置文档，供本方法插入兼容性发布版本时使用
     * @param scopeHash 作用域哈希，供本方法插入兼容性发布版本时使用
     * @param publishedBy 已发布，供本方法插入兼容性发布版本时使用
     * @param publishedByName 已发布名称，后续用于插入兼容性发布版本时匹配或展示
     * @return 插入后的兼容性发布版本结果，供调用方继续处理
     */
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

    /**
     * 把新建兼容快照切为旧运行时 active；不再增加已由主 CAS 更新的 revision。
     *
     * @param configId 配置ID，后续用于激活兼容性发布版本时定位或关联目标
     * @param savedRevision {@code saved}修订版本，供本方法激活兼容性发布版本时使用
     * @param releaseId 发布版本ID，后续用于激活兼容性发布版本时定位或关联目标
     * @param updateBy 更新，供本方法激活兼容性发布版本时使用
     * @return 激活后的兼容性发布版本结果，供调用方继续处理
     */
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
