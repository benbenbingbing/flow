package com.workflow.contracts.embed;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Embed Runtime 读取实体列表发布版本的防腐层端口。
 *
 * <p>调用方只能传服务端从 Embed Release 解析出的目标版本和固定条件；浏览器请求不直接接触
 * release、scene、context 或固定过滤器。实现仍须应用当前 Flow 用户的数据权限。</p>
 */
public interface EmbedRuntimeEntityPort {

    ListSchema loadListSchema(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion);

    /**
     * 查询固定列表发布版本。
     *
     * @param encodedClientFilters Embed Facade 根据发布字段操作符生成的 Entity 内部条件键；
     *                             不能直接接收浏览器过滤对象
     * @param trustedContextFilters 由服务端 Session/Release 解析的固定上下文条件
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

    record Option(String label, Object value) {
    }

    record Action(String key, String label, String placement) {
    }

    record ListPage(
            List<Row> rows,
            long total,
            int pageNum,
            int pageSize) {
    }

    record Row(
            String id,
            Map<String, Object> values,
            Instant updatedAt,
            Map<String, ActionCapability> actionCapabilities) {
    }

    record ActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }
}
