package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.query.DatabaseSort;

import java.util.List;

/**
 * 定义实体发布历史的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper

/**
 * 实体发布版本历史Mapper
 */
public interface EntityPublishHistoryMapper extends BaseMapper<EntityPublishHistory> {

    /**
     * 根据实体ID查询版本历史列表（按版本号降序）
     *
     * @param entityId 实体ID，后续用于查询实体ID时定位或关联目标
     * @return 实体发布历史集合，供调用方遍历或展示
     */
    default List<EntityPublishHistory> findByEntityId(String entityId) {
        return selectList(Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityId, entityId)
                .orderByDesc(EntityPublishHistory::getVersion));
    }

    /**
     * 统计实体发布历史条数。
     *
     * @param entityId 实体定义 ID
     * @return 历史总数
     */
    default long countByEntityId(String entityId) {
        return selectCount(Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityId, entityId));
    }

    /**
     * 按版本号倒序分页查询实体发布历史。
     *
     * @param entityId 实体定义 ID
     * @param offset   起始偏移量
     * @param pageSize 本页条数
     * @return 当前页历史记录
     */
    default List<EntityPublishHistory> findPageByEntityId(String entityId, long offset, long pageSize) {
        return selectList(new OffsetPage<>(offset, pageSize), Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityId, entityId)
                .orderByDesc(EntityPublishHistory::getVersion));
    }

    /**
     * 利用实体与版本唯一索引精确读取一条发布历史。
     *
     * @param entityId 实体定义 ID
     * @param version  发布版本号
     * @return 指定版本；不存在时返回 null
     */
    default EntityPublishHistory findByEntityIdAndVersion(String entityId, Integer version) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityId, entityId)
                .eq(EntityPublishHistory::getVersion, version))
                .stream().findFirst().orElse(null);
    }

    /**
     * 获取实体的最新版本号
     *
     * @param entityId 实体ID，后续用于读取最新版本时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    default Integer getLatestVersion(String entityId) {
        List<Object> values = selectObjs(Wrappers.<EntityPublishHistory>query()
                .select("MAX(version)")
                .eq("entity_id", entityId));
        return values.isEmpty() || values.get(0) == null ? null : ((Number) values.get(0)).intValue();
    }

    /**
     * 查询实体的最新发布记录
     *
     * @param entityId 实体ID，后续用于查询最新实体ID时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    default EntityPublishHistory findLatestByEntityId(String entityId) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityId, entityId)
                .orderByDesc(EntityPublishHistory::getVersion))
                .stream().findFirst().orElse(null);
    }

    /**
     * 锁定当前发布历史作为同一实体自关联写入的稳定互斥点。
     * 实体发布先持有 entity_definition 独占锁，因此不会与历史锁形成反序。
     * 禁用缓存以保证与 JDBC 混用时仍能读取当前已锁定的发布记录。
     *
     * @param entityId 实体ID，后续用于查询最新实体ID更新时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    @SelectProvider(type = LockingSql.class, method = "latest")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    EntityPublishHistory findLatestByEntityIdForUpdate(
            @Param("entityId") String entityId);

    /** 发布顺序和实体条件由业务指定，方言仅生成安全的首行基表锁查询。 */
    class LockingSql {
        /**
         * 生成最新文本，供后续匹配或展示。
         *
         * @param context 执行上下文，向后续最新步骤传递身份、配置或状态
         * @return 处理后的最新文本，供调用方比较或展示
         */
        public static String latest(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).firstForUpdate(
                    "entity_publish_history", "entity_id = #{entityId}",
                    List.of(new DatabaseSort("version", true), new DatabaseSort("id", true)), "id");
        }
    }

    /**
     * 按实体编码查询最新发布记录
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    default EntityPublishHistory findLatestByEntityCode(String entityCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityPublishHistory>lambdaQuery()
                .eq(EntityPublishHistory::getEntityCode, entityCode)
                .orderByDesc(EntityPublishHistory::getVersion))
                .stream().findFirst().orElse(null);
    }
}
