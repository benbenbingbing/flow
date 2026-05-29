package com.workflow.mapper;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.mapping.StatementType;

import java.util.List;
import java.util.Map;

/**
 * 实体数据动态 Mapper
 * 支持动态表名和动态字段
 * 
 * 注意：所有方法都需要传入 tableName 参数
 */
@Mapper
public interface EntityDataDynamicMapper {

    /**
     * 根据ID查询
     * 
     * @param tableName 表名
     * @param id 数据ID
     * @return 数据Map
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectById")
    @Options(statementType = StatementType.PREPARED)
    Map<String, Object> selectById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 根据流程实例ID查询
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByProcessInstanceId")
    @Options(statementType = StatementType.PREPARED)
    Map<String, Object> selectByProcessInstanceId(@Param("tableName") String tableName, 
                                                   @Param("processInstanceId") String processInstanceId);

    /**
     * 查询列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectList")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectList(@Param("tableName") String tableName);

    /**
     * 条件查询
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByCondition")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectByCondition(@Param("tableName") String tableName, 
                                                 @Param("condition") Map<String, Object> condition);

    /**
     * 插入数据
     * 
     * @param tableName 表名
     * @param data 数据Map（包含所有字段）
     * @return 影响行数
     */
    @InsertProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "insert")
    @Options(statementType = StatementType.PREPARED)
    int insert(@Param("tableName") String tableName, @Param("data") Map<String, Object> data);

    /**
     * 更新数据
     * 
     * @param tableName 表名
     * @param data 数据Map（必须包含id字段）
     * @return 影响行数
     */
    @UpdateProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "update")
    @Options(statementType = StatementType.PREPARED)
    int update(@Param("tableName") String tableName, @Param("data") Map<String, Object> data);

    /**
     * 逻辑删除
     */
    @UpdateProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "deleteById")
    @Options(statementType = StatementType.PREPARED)
    int deleteById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 物理删除
     */
    @DeleteProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "physicalDeleteById")
    @Options(statementType = StatementType.PREPARED)
    int physicalDeleteById(@Param("tableName") String tableName, @Param("id") String id);

    /**
     * 查询列表（带数据权限过滤）
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectListWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectListWithPermission(@Param("tableName") String tableName,
                                                        @Param("permissionSql") String permissionSql);

    /**
     * 条件查询（带数据权限过滤）
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByConditionWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectByConditionWithPermission(@Param("tableName") String tableName,
                                                               @Param("condition") Map<String, Object> condition,
                                                               @Param("permissionSql") String permissionSql);

    /**
     * 统计数量
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "count")
    @Options(statementType = StatementType.PREPARED)
    long count(@Param("tableName") String tableName);

    /**
     * 执行原生SQL查询（用于复杂查询）
     * 
     * @param sql 完整SQL语句（已在Service层组装好）
     * @return 结果列表
     */
    @Select("${sql}")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> executeQuery(@Param("sql") String sql);

    /**
     * 执行原生SQL更新
     */
    @Update("${sql}")
    @Options(statementType = StatementType.PREPARED)
    int executeUpdate(@Param("sql") String sql);
}
