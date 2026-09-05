package com.workflow.contracts.embed.runtime.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Embed Runtime 读取实体列表发布版本的防腐层端口。
 */
public interface EmbedRuntimeEntityPort {

    /** 读取精确列表发布版本的结构定义。 */
    ListSchema loadListSchema(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion);

    /**
     * 查询固定列表发布版本。
     *
     * @param encodedClientFilters Embed Facade 根据发布字段操作符生成的实体内部条件键
     * @param trustedContextFilters 服务端 Session/Release 解析的固定上下文条件
     */
    ListPage queryList(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion,
            int pageNum,
            int pageSize,
            Map<String, Object> encodedClientFilters,
            Map<String, Object> trustedContextFilters);

    /** 已发布列表版本的结构定义。 */
    record ListSchema(
            String entityCode,
            String entityName,
            String listKey,
            String listName,
            Map<String, Object> selection,
            List<Field> fields,
            List<Action> toolbarActions,
            List<Action> rowActions) {
    }

    /** 已发布列表字段。 */
    record Field(
            String code,
            String label,
            String type,
            Integer width,
            boolean shown,
            boolean queryable,
            String queryOperator,
            List<Option> options) {
    }

    /** 枚举字段选项。 */
    record Option(String label, Object value) {
    }

    /** 列表工具栏或行操作。 */
    record Action(String key, String label, String placement) {
    }

    /** 固定发布版本的分页查询结果。 */
    record ListPage(
            List<Row> rows,
            long total,
            int pageNum,
            int pageSize) {
    }

    /** 列表行及其按操作维度计算的能力快照。 */
    record Row(
            String id,
            Map<String, Object> values,
            Instant updatedAt,
            Map<String, ActionCapability> actionCapabilities) {
    }

    /** 行操作的可见性与可执行性。 */
    record ActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }
}
