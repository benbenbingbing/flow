package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 人员路径只查询服务端元数据生成的最小投影；所有记录值绑定，查询具有超时和结果预算。 */
@Mapper
public interface EntityUserPathMapper {
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(timeout = 10, useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> select(@Param("statement") ParameterizedSql statement);
}
