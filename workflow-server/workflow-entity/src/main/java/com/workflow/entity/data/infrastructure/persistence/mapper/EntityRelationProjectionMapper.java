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

    /** 查询参数保持只读，分页对象由 Mapper 添加到 MP 可识别的参数容器中。 */
    default List<Map<String, Object>> selectPage(Map<String, Object> parameters) {
        Map<String, Object> paged = new HashMap<>(parameters);
        paged.put("__page", new OffsetPage<>(((Number) parameters.get("offset")).longValue(), ((Number) parameters.get("pageSize")).longValue()));
        return selectPageRows(paged);
    }

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectPage")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPageRows(Map<String, Object> parameters);

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "count")
    @Options(statementType = StatementType.PREPARED)
    long count(Map<String, Object> parameters);

    /** 查询参数保持只读，分页对象由 Mapper 添加到 MP 可识别的参数容器中。 */
    default List<Map<String, Object>> selectMultiValues(Map<String, Object> parameters) {
        Map<String, Object> paged = new HashMap<>(parameters);
        paged.put("__page", new OffsetPage<>(0, ((Number) parameters.get("limitPlusOne")).longValue()));
        return selectMultiValuesRows(paged);
    }

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectMultiValues")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectMultiValuesRows(
            Map<String, Object> parameters);
}
