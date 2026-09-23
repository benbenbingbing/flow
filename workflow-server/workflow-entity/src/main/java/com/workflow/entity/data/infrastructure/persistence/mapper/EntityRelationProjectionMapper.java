package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.entity.data.infrastructure.persistence.provider.EntityRelationProjectionSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import com.workflow.core.database.OffsetPage;
import java.util.HashMap;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.mapping.StatementType;

import java.util.List;
import java.util.Map;

/** 关系图最小投影的只读 MyBatis Mapper。 */
@Mapper
public interface EntityRelationProjectionMapper {

    /**
     * 查询参数保持只读，分页对象由 Mapper 添加到 MP 可识别的参数容器中。
     *
     * @param parameters 参数集合，供本方法查询实体关系投影分页时使用
     * @return 实体关系投影集合，供调用方遍历或展示
     */
    default List<Map<String, Object>> selectPage(Map<String, Object> parameters) {
        Map<String, Object> paged = new HashMap<>(parameters);
        paged.put("__page", new OffsetPage<>(((Number) parameters.get("offset")).longValue(), ((Number) parameters.get("pageSize")).longValue()));
        return selectPageRows(paged);
    }

    /**
     * 查询分页行；查询结果供调用方展示或继续处理。
     *
     * @param parameters 参数集合，供本方法查询分页行时使用
     * @return 实体关系投影集合，供调用方遍历或展示
     */
    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectPage")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPageRows(Map<String, Object> parameters);

    /**
     * 统计实体关系投影；结果供后续判断或展示使用。
     *
     * @param parameters 参数集合，供本方法统计实体关系投影时使用
     * @return 符合条件的实体关系投影数量
     */
    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "count")
    @Options(statementType = StatementType.PREPARED)
    long count(Map<String, Object> parameters);

    /**
     * 查询参数保持只读，分页对象由 Mapper 添加到 MP 可识别的参数容器中。
     *
     * @param parameters 参数集合，供本方法查询多实例值集合时使用
     * @return 实体关系投影集合，供调用方遍历或展示
     */
    default List<Map<String, Object>> selectMultiValues(Map<String, Object> parameters) {
        Map<String, Object> paged = new HashMap<>(parameters);
        paged.put("__page", new OffsetPage<>(0, ((Number) parameters.get("limitPlusOne")).longValue()));
        return selectMultiValuesRows(paged);
    }

    /**
     * 查询多实例值集合行；查询结果供调用方展示或继续处理。
     *
     * @param parameters 参数集合，供本方法查询多实例值集合行时使用
     * @return 实体关系投影集合，供调用方遍历或展示
     */
    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectMultiValues")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectMultiValuesRows(
            Map<String, Object> parameters);
}
