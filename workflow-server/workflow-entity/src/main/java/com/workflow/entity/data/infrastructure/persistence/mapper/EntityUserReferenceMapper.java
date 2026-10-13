package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface EntityUserReferenceMapper {

    /** 按关系顺序读取用户 ID，服务端生成的分页 SQL 保留人数上限检查。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> selectRelatedUserIds(@Param("statement") ParameterizedSql statement);

    /** 连同软删除用户读取身份，阻止其 ID 被同名 username 接管。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectUserIdentities(@Param("statement") ParameterizedSql statement);

    /** 为尚未匹配任何用户 ID 的历史输入读取规范 username。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> selectLegacyUsernames(@Param("statement") ParameterizedSql statement);
}
