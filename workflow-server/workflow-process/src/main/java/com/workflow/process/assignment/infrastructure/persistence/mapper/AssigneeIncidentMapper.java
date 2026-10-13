package com.workflow.process.assignment.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.springframework.util.LinkedCaseInsensitiveMap;

/** 空办理人事件及处置审计持久化。SQL 仅由服务端构造，业务值统一作为绑定参数传入。 */
@Mapper
public interface AssigneeIncidentMapper {
    /**
     * 查询事件和审计；显式使用具体 Map 类型，Oracle 大写列仍可按小写物理列读取。
     * MyBatis 对非 void 方法忽略 @ResultType，因此不能退回 List&lt;Map&gt;。
     */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    List<LinkedCaseInsensitiveMap<Object>> selectRows(@Param("statement") ParameterizedSql statement);

    /** 读取单列 ID，保留方言分页和冲突后的当前读语义。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    List<String> selectIds(@Param("statement") ParameterizedSql statement);

    /** 返回聚合数量；COUNT 在空集合中仍返回 0。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    Long count(@Param("statement") ParameterizedSql statement);

    /** 执行事件或审计状态变更；加入调用方事务，唯一冲突由保存点协调器处理。 */
    @UpdateProvider(type = ParameterizedSqlProvider.class, method = "sql")
    int execute(@Param("statement") ParameterizedSql statement);
}
