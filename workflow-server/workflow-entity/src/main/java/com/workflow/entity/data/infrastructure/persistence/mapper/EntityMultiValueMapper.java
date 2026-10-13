package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 通过 MyBatis 执行受控的实体业务 SQL；表列名由实体白名单/方言校验，值始终预编译绑定。 */
@Mapper
public interface EntityMultiValueMapper {

    /** 统计有效字典项，零条时业务层拒绝保存非法代码值。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Integer countValidDictItem(@Param("statement") ParameterizedSql statement);

    /** 清除指定字段的旧关系，与随后写入的新关系处于同一事务。 */
    @DeleteProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int deleteFieldValues(@Param("statement") ParameterizedSql statement);

    /** 保存一条带顺序的多值关系，记录目标实体身份以支持字段发布后的兼容性检查。 */
    @InsertProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int insertValue(@Param("statement") ParameterizedSql statement);

    /** 按记录、字段和排序读取关系；过期目标实体关系由业务层过滤。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectValues(@Param("statement") ParameterizedSql statement);

    /** 随主记录清理该记录的全部多值关系。 */
    @DeleteProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int deleteRecordValues(@Param("statement") ParameterizedSql statement);

    /** 查询首个有效字典目标 ID；分页 SQL 由当前数据库方言生成。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> selectDictIds(@Param("statement") ParameterizedSql statement);

    /** 将持久化字典 ID 转回表单 API 使用的业务代码。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> selectDictCodes(@Param("statement") ParameterizedSql statement);

    /** 读取标量字典标签和状态，用于回填表单显示选项。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectScalarDictOptions(@Param("statement") ParameterizedSql statement);

    /** 读取多值字典标签和状态，保留停用字典项的历史显示。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectDictOptions(@Param("statement") ParameterizedSql statement);

    /** 读取目录允许的显示字段；同时投影非空 ID，使空标签仍保留其目标记录。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    @ResultMap("databaseRow")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Map<String, Object>> selectReferenceOptions(@Param("statement") ParameterizedSql statement);
}
