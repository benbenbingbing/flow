package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.entity.data.infrastructure.persistence.provider.EntityRelationProjectionSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.mapping.StatementType;

import java.util.List;
import java.util.Map;

/** 关系图最小投影的只读 MyBatis Mapper。 */
@Mapper
public interface EntityRelationProjectionMapper {

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectPage")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPage(Map<String, Object> parameters);

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "count")
    @Options(statementType = StatementType.PREPARED)
    long count(Map<String, Object> parameters);

    @SelectProvider(
            type = EntityRelationProjectionSqlProvider.class,
            method = "selectMultiValues")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectMultiValues(
            Map<String, Object> parameters);
}
