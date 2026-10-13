package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface PermissionSqlEvaluationMapper {

    /** 在数据库内评估已校验的用户权限条件，显式类型的 NULL 属性保留 SQL 三值逻辑。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Integer evaluateUserPredicate(@Param("statement") ParameterizedSql statement);
}
