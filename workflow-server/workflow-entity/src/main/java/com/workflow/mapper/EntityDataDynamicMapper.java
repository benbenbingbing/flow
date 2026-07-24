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
     * 根据ID查询（带数据权限过滤），仅返回权限校验通过且未删除的记录。
     *
     * @param tableName      数据表名
     * @param id             数据 ID
     * @param permissionSql  数据权限 SQL 片段
     * @return 数据 Map，无则返回 null
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByIdWithPermission")
    @Options(statementType = StatementType.PREPARED)
    Map<String, Object> selectByIdWithPermission(
            @Param("tableName") String tableName,
            @Param("id") String id,
            @Param("permissionSql") String permissionSql);

    /**
     * 根据流程实例ID查询
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByProcessInstanceId")
    @Options(statementType = StatementType.PREPARED)
    Map<String, Object> selectByProcessInstanceId(@Param("tableName") String tableName, 
                                                   @Param("processInstanceId") String processInstanceId);

    /**
     * 查询列表（全量未删除记录，按创建时间倒序）。
     *
     * @param tableName 数据表名
     * @return 数据 Map 列表
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
     * 更新当前任务信息，允许清空任务字段
     */
    @UpdateProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "updateCurrentTask")
    @Options(statementType = StatementType.PREPARED)
    int updateCurrentTask(@Param("tableName") String tableName,
                          @Param("id") String id,
                          @Param("currentTaskId") String currentTaskId,
                          @Param("currentTaskName") String currentTaskName,
                          @Param("currentTaskAssignee") String currentTaskAssignee);

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
    /**
     * 查询列表（带数据权限过滤）
     *
     * @param tableName     数据表名
     * @param permissionSql 数据权限 SQL 片段
     * @return 数据 Map 列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectListWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectListWithPermission(@Param("tableName") String tableName,
                                                        @Param("permissionSql") String permissionSql);

    /**
     * 分页查询（不带条件），按创建时间倒序。
     *
     * @param tableName 数据表名
     * @param offset    偏移量
     * @param limit     每页数量
     * @return 数据 Map 列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectPage")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPage(
            @Param("tableName") String tableName,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 分页查询（带数据权限过滤），按创建时间倒序。
     *
     * @param tableName     数据表名
     * @param permissionSql 数据权限 SQL 片段
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 数据 Map 列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectPageWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPageWithPermission(
            @Param("tableName") String tableName,
            @Param("permissionSql") String permissionSql,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 条件查询（带数据权限过滤）
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectByConditionWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectByConditionWithPermission(@Param("tableName") String tableName,
                                                               @Param("condition") Map<String, Object> condition,
                                                               @Param("permissionSql") String permissionSql);

    /**
     * 分页条件查询（不带权限过滤），按创建时间倒序。
     *
     * @param tableName 数据表名
     * @param condition 查询条件
     * @param offset    偏移量
     * @param limit     每页数量
     * @return 数据 Map 列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectPageByCondition")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPageByCondition(
            @Param("tableName") String tableName,
            @Param("condition") Map<String, Object> condition,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 分页条件查询（带数据权限过滤），按创建时间倒序。
     *
     * @param tableName     数据表名
     * @param condition     查询条件
     * @param permissionSql 数据权限 SQL 片段
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 数据 Map 列表
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "selectPageByConditionWithPermission")
    @Options(statementType = StatementType.PREPARED)
    List<Map<String, Object>> selectPageByConditionWithPermission(
            @Param("tableName") String tableName,
            @Param("condition") Map<String, Object> condition,
            @Param("permissionSql") String permissionSql,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 统计数量
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "count")
    @Options(statementType = StatementType.PREPARED)
    long count(@Param("tableName") String tableName);

    /**
     * 统计数量（根据条件）
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "countByCondition")
    @Options(statementType = StatementType.PREPARED)
    long countByCondition(@Param("tableName") String tableName,
                          @Param("condition") Map<String, Object> condition);

    /**
     * 统计数量（带数据权限过滤）
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "countWithPermission")
    @Options(statementType = StatementType.PREPARED)
    long countWithPermission(@Param("tableName") String tableName,
                             @Param("permissionSql") String permissionSql);

    /**
     * 统计数量（根据条件并带数据权限过滤）。
     *
     * @param tableName     数据表名
     * @param condition     查询条件
     * @param permissionSql 数据权限 SQL 片段
     * @return 记录总数
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "countByConditionWithPermission")
    @Options(statementType = StatementType.PREPARED)
    long countByConditionWithPermission(
            @Param("tableName") String tableName,
            @Param("condition") Map<String, Object> condition,
            @Param("permissionSql") String permissionSql);

    /**
     * 统计已关联流程实例（process_instance_id 非空）的记录数量。
     *
     * @param tableName 数据表名
     * @return 流程实例记录总数
     */
    @SelectProvider(type = com.workflow.mapper.provider.EntityDataSqlProvider.class, method = "countProcessInstances")
    @Options(statementType = StatementType.PREPARED)
    long countProcessInstances(@Param("tableName") String tableName);
}
