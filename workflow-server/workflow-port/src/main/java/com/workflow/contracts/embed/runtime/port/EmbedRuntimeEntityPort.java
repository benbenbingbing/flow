package com.workflow.contracts.embed.runtime.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Embed Runtime 读取实体列表发布版本的防腐层端口。
 */
public interface EmbedRuntimeEntityPort {

    /**
     * 读取精确列表发布版本的结构定义。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于加载列表结构时定位或关联目标
     * @param listReleaseVersion 列表发布版本，供本方法加载列表结构时使用
     * @return 符合条件的列表结构结果，供调用方继续处理
     */
    ListSchema loadListSchema(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion);

    /**
     * 查询固定列表发布版本。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于查询嵌入式运行时实体列表时定位或关联目标
     * @param listReleaseVersion 列表发布版本，供本方法查询嵌入式运行时实体列表时使用
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param encodedClientFilters Embed Facade 根据发布字段操作符生成的实体内部条件键
     * @param trustedContextFilters 服务端 Session/Release 解析的固定上下文条件
     * @return 查询后的嵌入式运行时实体列表结果，供调用方继续处理
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

    /**
     * 已发布列表版本的结构定义。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityName 实体名称，后续用于处理列表结构时匹配或展示
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listName 列表名称，后续用于处理列表结构时匹配或展示
     * @param selection 选择，保存在对象中供后续校验、查询或展示
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param toolbarActions {@code toolbar}动作集合，保存在对象中供后续校验、查询或展示
     * @param rowActions 行动作集合，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 已发布列表字段。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param label 标签，后续用于处理字段时匹配或展示
     * @param type 类型标识，决定后续字段采用的处理分支
     * @param width {@code width}，保存在对象中供后续校验、查询或展示
     * @param shown {@code shown}，保存在对象中供后续校验、查询或展示
     * @param queryable {@code queryable}，保存在对象中供后续校验、查询或展示
     * @param queryOperator 查询操作人，保存在对象中供后续校验、查询或展示
     * @param options 选项，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 枚举字段选项。
     *
     * @param label 标签，后续用于处理选项时匹配或展示
     * @param value 待处理选项的原始输入，结果供调用方继续使用
     */
    record Option(String label, Object value) {
    }

    /**
     * 列表工具栏或行操作。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param label 标签，后续用于处理动作时匹配或展示
     * @param placement {@code placement}，保存在对象中供后续校验、查询或展示
     */
    record Action(String key, String label, String placement) {
    }

    /**
     * 固定发布版本的分页查询结果。
     *
     * @param rows 行，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    record ListPage(
            List<Row> rows,
            long total,
            int pageNum,
            int pageSize) {
    }

    /**
     * 列表行及其按操作维度计算的能力快照。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param updatedAt {@code updated}时间，后续用于判断有效期或展示该事件的发生时间
     * @param actionCapabilities 动作能力集合，保存在对象中供后续校验、查询或展示
     */
    record Row(
            String id,
            Map<String, Object> values,
            Instant updatedAt,
            Map<String, ActionCapability> actionCapabilities) {
    }

    /**
     * 行操作的可见性与可执行性。
     *
     * @param visible 可见，保存在对象中供后续校验、查询或展示
     * @param enabled 启用，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    record ActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }
}
