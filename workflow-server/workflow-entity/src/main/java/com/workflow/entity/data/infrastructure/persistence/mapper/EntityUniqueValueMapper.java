package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface EntityUniqueValueMapper {

    /** 占用唯一值；唯一键冲突交由业务层转换为稳定错误，回滚时同步撤销占用。 */
    @InsertProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int insertClaim(@Param("statement") ParameterizedSql statement);

    /** 释放普通字段占用，保留业务编码的永久身份占用。 */
    @DeleteProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int releaseClaims(@Param("statement") ParameterizedSql statement);
}
