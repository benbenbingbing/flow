package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 动态实体聚合查询服务。
 *
 * <p>业务数据写入统一由 EntityMutationPort 进入变更管道，本服务不提供写方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataDynamicService {

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityRelationRuntimeService relationRuntimeService;
    private final EntityMultiValueRuntimeService multiValueRuntimeService;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService sysUserService;
    private final EntityPublishedSnapshotService snapshotService;

    /**
     * 按实体编码查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode) {
        return findByEntityCode(entityCode, null);
    }

    /**
     * 按实体编码查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(
            String entityCode,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectList(tableName);
        } else {
            dataList = dynamicMapper.selectListWithPermission(
                    tableName,
                    permission.getSqlCondition(),
                            permission.getSqlParameters());
        }

        List<EntityField> runtimeFields = getRuntimeFields(entityCode);
        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(
                        data,
                        entityCode,
                        runtimeFields))
                .toList();
        enrichMultiValues(entityCode, records);
        return records;
    }

    /**
     * 按实体编码查询简要实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(
            String entityCode) {
        return findByEntityCodeSimple(entityCode, null);
    }

    /**
     * 按实体编码查询简要实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(
            String entityCode,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);

        if (!permission.isHasPermission()) {
            return new ArrayList<>();
        }
        if (!permission.isNeedFilter()) {
            return dynamicMapper.selectList(tableName);
        }
        return dynamicMapper.selectListWithPermission(
                tableName,
                permission.getSqlCondition(),
                            permission.getSqlParameters());
    }

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPage(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                getDataPermission(entityCode, listKey));
    }

    /**
     * 按用户权限分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageForUser(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            SysUser user) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                dataPermissionEngine.calculatePermission(
                        entityCode,
                        listKey,
                        user));
    }

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @param plan 数据范围方案，后续提供 SQL 条件和参数以限制查询结果
     * @return 符合条件的实体数据结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageWithDataScopePlan(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataScopePlan plan) {
        if (plan == null
                || plan.sqlFragment() == null
                || plan.sqlFragment().isBlank()) {
            throw new IllegalArgumentException(
                    "数据权限计划不能为空");
        }
        DataPermissionResult permission = plan.allowed()
                ? createAllowedPermission(plan)
                : DataPermissionResult.denyAll();
        permission.setMatchedRuleNames(plan.matchedPolicies());
        permission.setReleaseVersion(plan.releaseVersion());
        permission.setExplanation(plan.explanation());
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                permission);
    }

    /**
     * 列表扩展计划必须将条件和参数一起传递，包含类型明确的 SQL NULL。
     *
     * @param plan 数据范围方案，后续提供 SQL 条件和参数以限制查询结果
     * @return 创建后的允许权限结果，供调用方继续处理
     */
    private DataPermissionResult createAllowedPermission(
            DataScopePlan plan) {
        if ("1=1".equals(plan.sqlFragment())) {
            return DataPermissionResult.allowAll();
        }
        return DataPermissionResult.withCondition(
                plan.sqlFragment(), plan.parameters());
    }

    /**
     * 按用户权限分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    private PageResult<EntityDataDTO> findPageWithPermission(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataPermissionResult permission) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(
                1,
                Math.min(200, requestedPageSize));
        long offset = (pageNum - 1) * pageSize;
        String tableName =
                dynamicTableService.getTableName(entityCode);
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(
                        definition,
                        condition);
        permission.intersect(prepared.sqlCondition());
        if (!permission.isHasPermission()) {
            return new PageResult<>(
                    List.of(),
                    0,
                    pageNum,
                    pageSize);
        }

        List<EntityField> runtimeFields = getRuntimeFields(entityCode);
        Map<String, Object> preparedCondition = EntityQueryConditions.fromPublishedFields(prepared.condition(), runtimeFields);
        PageRows pageRows = loadPageRows(
                tableName,
                preparedCondition,
                permission,
                offset,
                pageSize);
        List<EntityDataDTO> records =
                pageRows.rows().stream()
                        .map(data -> recordMapper.toDto(
                                data,
                                entityCode,
                                runtimeFields))
                        .toList();
        enrichMultiValues(entityCode, records);
        return new PageResult<>(
                records,
                pageRows.total(),
                pageNum,
                pageSize);
    }

    /**
     * 按筛选条件分页查询分页行；结果供列表展示。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的分页行结果，供调用方继续处理
     */
    private PageRows loadPageRows(
            String tableName,
            Map<String, Object> condition,
            DataPermissionResult permission,
            long offset,
            long pageSize) {
        boolean hasCondition =
                condition != null && !condition.isEmpty();
        if (hasCondition && permission.isNeedFilter()) {
            return new PageRows(
                    dynamicMapper.countByConditionWithPermission(
                            tableName,
                            condition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters()),
                    dynamicMapper.selectPageByConditionWithPermission(
                            tableName,
                            condition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters(),
                            offset,
                            pageSize));
        }
        if (hasCondition) {
            return new PageRows(
                    dynamicMapper.countByCondition(
                            tableName,
                            condition),
                    dynamicMapper.selectPageByCondition(
                            tableName,
                            condition,
                            offset,
                            pageSize));
        }
        if (permission.isNeedFilter()) {
            return new PageRows(
                    dynamicMapper.countWithPermission(
                            tableName,
                            permission.getSqlCondition(),
                            permission.getSqlParameters()),
                    dynamicMapper.selectPageWithPermission(
                            tableName,
                            permission.getSqlCondition(),
                            permission.getSqlParameters(),
                            offset,
                            pageSize));
        }
        return new PageRows(
                dynamicMapper.count(tableName),
                dynamicMapper.selectPage(
                        tableName,
                        offset,
                        pageSize));
    }

    /**
     * 按ID查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findById(
            String entityCode,
            String id) {
        Map<String, Object> data = dynamicMapper.selectById(
                dynamicTableService.getTableName(entityCode),
                id);
        if (data == null) {
            throw new RuntimeException("数据不存在: " + id);
        }
        return assembleAggregate(data, entityCode);
    }

    /**
     * 按ID查询当前用户可访问的实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体数据结果，供调用方继续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleById(
            String entityCode,
            String id,
            String listKey) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            throw new ForbiddenException(
                    "数据不存在或无权访问");
        }
        Map<String, Object> data =
                permission.isNeedFilter()
                        ? dynamicMapper.selectByIdWithPermission(
                                tableName,
                                id,
                                permission.getSqlCondition(),
                            permission.getSqlParameters())
                        : dynamicMapper.selectById(
                                tableName,
                                id);
        if (data == null) {
            throw new ForbiddenException(
                    "数据不存在或不在当前数据权限范围内");
        }
        return assembleAggregate(data, entityCode);
    }

    /**
     * 历史版本访问校验专用：逻辑删除记录仍按原行级权限判断。
     * 禁止写入、固化或普通详情接口复用此方法。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleIncludingDeletedById(
            String entityCode,
            String id,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            throw new ForbiddenException("数据不存在或无权访问");
        }
        Map<String, Object> data = permission.isNeedFilter()
                ? dynamicMapper.selectByIdIncludingDeletedWithPermission(
                        tableName,
                        id,
                        permission.getSqlCondition(),
                        permission.getSqlParameters())
                : dynamicMapper.selectByIdIncludingDeleted(tableName, id);
        if (data == null) {
            throw new ForbiddenException("数据不存在或不在当前数据权限范围内");
        }
        return assembleAggregate(data, entityCode);
    }

    /**
     * 按流程实例ID查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findByProcessInstanceId(
            String entityCode,
            String processInstanceId) {
        Map<String, Object> data =
                dynamicMapper.selectByProcessInstanceId(
                        dynamicTableService.getTableName(entityCode),
                        processInstanceId);
        if (data == null) {
            throw new RuntimeException(
                    "数据不存在: " + processInstanceId);
        }
        return assembleAggregate(data, entityCode);
    }

    /**
     * 按流程实例ID查询当前用户可访问的实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体数据结果，供调用方继续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleByProcessInstanceId(
            String entityCode,
            String processInstanceId,
            String listKey) {
        Map<String, Object> data =
                dynamicMapper.selectByProcessInstanceId(
                        dynamicTableService.getTableName(entityCode),
                        processInstanceId);
        if (data == null || data.get("id") == null) {
            throw new ForbiddenException(
                    "数据不存在或无权访问");
        }
        return findAccessibleById(
                entityCode,
                String.valueOf(data.get("id")),
                listKey);
    }

    /**
     * 按条件查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(
            String entityCode,
            Map<String, Object> condition) {
        return findByCondition(entityCode, null, condition);
    }

    /**
     * 按条件查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 实体数据集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(
            String entityCode,
            String listKey,
            Map<String, Object> condition) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(
                        requireDefinition(entityCode),
                        condition);
        permission.intersect(prepared.sqlCondition());
        List<EntityField> runtimeFields = getRuntimeFields(entityCode);
        Map<String, Object> preparedCondition = EntityQueryConditions.fromPublishedFields(prepared.condition(), runtimeFields);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectByCondition(
                    tableName,
                    preparedCondition);
        } else {
            dataList =
                    dynamicMapper.selectByConditionWithPermission(
                            tableName,
                            preparedCondition,
                            permission.getSqlCondition(),
                            permission.getSqlParameters());
        }

        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(
                        data,
                        entityCode,
                        runtimeFields))
                .toList();
        enrichMultiValues(entityCode, records);
        return records;
    }

    /**
     * 统计实体数据动态；结果供后续判断或展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体数据动态数量
     */
    @Transactional(readOnly = true)
    public long count(String entityCode) {
        return count(entityCode, null);
    }

    /**
     * 统计实体数据动态；结果供后续判断或展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体数据动态数量
     */
    @Transactional(readOnly = true)
    public long count(
            String entityCode,
            String listKey) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission =
                getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            return 0;
        }
        if (!permission.isNeedFilter()) {
            return dynamicMapper.count(tableName);
        }
        return dynamicMapper.countWithPermission(
                tableName,
                permission.getSqlCondition(),
                            permission.getSqlParameters());
    }

    /**
     * 校验并获取定义；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的定义结果，供调用方继续处理
     */
    private EntityDefinition requireDefinition(
            String entityCode) {
        return definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException(
                        "实体不存在: " + entityCode));
    }

    /**
     * 组装聚合对象；结果供后续流程传递或持久化。
     *
     * @param data 数据，后续用于组装聚合对象并传递处理结果
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 组装后的聚合对象结果，供调用方继续处理
     */
    private EntityDataDTO assembleAggregate(
            Map<String, Object> data,
            String entityCode) {
        EntityDataDTO dto = toRuntimeDto(data, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        relationRuntimeService.loadRelationData(dto);
        return dto;
    }

    /**
     * 补充多实例值集合；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param records 记录集合，作为 {@code multiValueRuntimeService.enrich} 的输入影响后续处理
     */
    private void enrichMultiValues(
            String entityCode,
            Collection<EntityDataDTO> records) {
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        if (definition == null
                || definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        multiValueRuntimeService.enrich(
                definition,
                records);
    }

    /**
     * 读取当前系统用户；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的系统用户结果，供调用方继续处理
     */
    private SysUser getCurrentSysUser() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return sysUserService.getById(userId);
    }

    /**
     * 读取数据权限；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的数据权限结果，供调用方继续处理
     */
    private DataPermissionResult getDataPermission(
            String entityCode,
            String listKey) {
        SysUser user = getCurrentSysUser();
        if (user == null) {
            // 无身份不能借创建人为空的历史记录获得权限；空串在不同数据库中也不具有相同语义。
            return DataPermissionResult.denyAll();
        }
        return dataPermissionEngine.calculatePermission(
                entityCode,
                listKey,
                user);
    }

    /**
     * 转换为运行时DTO；输出作为后续校验或处理的输入。
     *
     * @param data 数据，后续用于转换为运行时DTO并传递处理结果
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 转换为后的运行时DTO结果，供调用方继续处理
     */
    private EntityDataDTO toRuntimeDto(
            Map<String, Object> data,
            String entityCode) {
        return recordMapper.toDto(
                data,
                entityCode,
                getRuntimeFields(entityCode));
    }

    /**
     * 读取运行时字段；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体字段集合，供调用方遍历或展示
     */
    private List<EntityField> getRuntimeFields(
            String entityCode) {
        try {
            EntityPublishedSnapshot snapshot =
                    snapshotService.getLatestByEntityCode(
                            entityCode);
            return snapshot.getFields() == null
                    ? List.of()
                    : snapshot.getFields();
        } catch (RuntimeException exception) {
            log.debug(
                    "读取实体发布字段失败，使用兼容字段映射: entityCode={}, reason={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(exception.getMessage()));
            return List.of();
        }
    }

    /**
     * 封装分页行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param rows 行，保存在对象中供后续校验、查询或展示
     */
    private record PageRows(
            long total,
            List<Map<String, Object>> rows) {
    }
}
