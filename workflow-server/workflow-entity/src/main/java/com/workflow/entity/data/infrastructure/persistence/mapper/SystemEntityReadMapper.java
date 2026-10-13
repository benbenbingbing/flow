package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface SystemEntityReadMapper {

    /** 统计受系统实体目录、权限及筛选条件约束的记录数。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long countRecords(@Param("statement") ParameterizedSql statement);

    /** 读取受控系统实体分页，保留大小写不敏感的列访问和 SQL NULL 语义。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectRecords(@Param("statement") ParameterizedSql statement);

    /** 按唯一主键读取关联显示标签，缺失或空标签返回 null。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    String lookupLabel(@Param("statement") ParameterizedSql statement);
}
