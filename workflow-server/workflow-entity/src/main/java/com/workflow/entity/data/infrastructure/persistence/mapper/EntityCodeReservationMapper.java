package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface EntityCodeReservationMapper {

    /** 通过数据库唯一键串行化同一业务编码的分配，失败时由业务层返回编码冲突。 */
    @InsertProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int insertClaim(@Param("statement") ParameterizedSql statement);

    /** 覆盖上线前尚无占用行的历史编码，防止新旧记录被分配相同编号。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long countExistingCodes(@Param("statement") ParameterizedSql statement);

    /** 查找存量重复编码，作为启用自定义编码规则之前的阻断条件。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> selectDuplicateCodes(@Param("statement") ParameterizedSql statement);
}
