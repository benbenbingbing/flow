package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 实体数据迁移服务
 * 将旧表（统一表+JSON）的数据迁移到新表（独立表结构）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataMigrationService {

    private final EntityDataMapper oldDataMapper;
    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final DynamicTableService dynamicTableService;
    private final ObjectMapper objectMapper;

    /**
     * 迁移指定实体的所有数据
     * 
     * @param entityCode 实体编码
     * @return 迁移的数据条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int migrateEntityData(String entityCode) {
        log.info("开始迁移实体 [{}] 的数据", entityCode);

        // 1. 获取实体定义
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + entityCode));

        // 2. 确保新表已创建
        if (!dynamicTableService.tableExists(entityCode)) {
            dynamicTableService.createEntityTable(definition);
        }

        String tableName = dynamicTableService.getTableName(entityCode);

        // 3. 查询旧表数据
        List<EntityData> oldDataList = oldDataMapper.findByEntityCode(entityCode);
        log.info("找到 {} 条旧数据需要迁移", oldDataList.size());

        int migratedCount = 0;
        int failedCount = 0;

        // 4. 逐条迁移
        for (EntityData oldData : oldDataList) {
            try {
                Map<String, Object> newData = convertToNewFormat(oldData);
                
                // 检查新表是否已有此记录
                Map<String, Object> existing = dynamicMapper.selectById(tableName, oldData.getId());
                if (existing == null) {
                    dynamicMapper.insert(tableName, newData);
                    migratedCount++;
                } else {
                    log.debug("数据 [{}] 已存在于新表，跳过", oldData.getId());
                }
            } catch (Exception e) {
                log.error("迁移数据 [{}] 失败: {}", oldData.getId(), e.getMessage());
                failedCount++;
            }
        }

        log.info("实体 [{}] 数据迁移完成：成功 {}, 失败 {}", entityCode, migratedCount, failedCount);
        return migratedCount;
    }

    /**
     * 迁移所有实体的数据
     * 
     * @return 迁移统计信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> migrateAll() {
        log.info("开始迁移所有实体数据");

        List<EntityDefinition> entities = definitionMapper.selectList(null);
        Map<String, Object> result = new HashMap<>();
        int totalMigrated = 0;
        List<String> failedEntities = new ArrayList<>();

        for (EntityDefinition entity : entities) {
            try {
                int count = migrateEntityData(entity.getEntityCode());
                totalMigrated += count;
                result.put(entity.getEntityCode(), count);
            } catch (Exception e) {
                log.error("迁移实体 [{}] 失败: {}", entity.getEntityCode(), e.getMessage());
                failedEntities.add(entity.getEntityCode());
            }
        }

        result.put("totalMigrated", totalMigrated);
        result.put("failedEntities", failedEntities);

        log.info("所有实体数据迁移完成，共迁移 {} 条数据", totalMigrated);
        return result;
    }

    /**
     * 将旧格式数据转换为新格式
     */
    private Map<String, Object> convertToNewFormat(EntityData oldData) {
        Map<String, Object> newData = new HashMap<>();

        // 基础字段
        newData.put("id", oldData.getId());
        newData.put("data_no", oldData.getDataNo());
        newData.put("title", oldData.getTitle());
        newData.put("name", oldData.getName());
        newData.put("code", oldData.getCode());
        newData.put("status", oldData.getStatus());
        newData.put("process_instance_id", oldData.getProcessInstanceId());
        newData.put("process_start_time", oldData.getProcessStartTime());
        newData.put("process_end_time", oldData.getProcessEndTime());
        newData.put("current_task_id", oldData.getCurrentTaskId());
        newData.put("current_task_name", oldData.getCurrentTaskName());
        newData.put("submitter_id", oldData.getSubmitterId());
        newData.put("submitter_name", oldData.getSubmitterName());
        newData.put("submit_time", oldData.getSubmitTime());
        newData.put("created_by", oldData.getCreatedBy());
        newData.put("updated_by", oldData.getUpdatedBy());
        newData.put("created_at", oldData.getCreatedAt());
        newData.put("updated_at", oldData.getUpdatedAt());
        newData.put("deleted", oldData.getDeleted());

        // 解析 JSON 数据到独立字段
        if (oldData.getDataJson() != null && !oldData.getDataJson().isEmpty()) {
            try {
                Map<String, Object> jsonData = objectMapper.readValue(oldData.getDataJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                
                // 将所有 JSON 字段展开到新数据
                for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
                    // 只添加非系统字段（系统字段已在上面的基础字段中处理）
                    if (!isSystemField(entry.getKey())) {
                        newData.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                log.warn("解析数据 [{}] 的 JSON 失败: {}", oldData.getId(), e.getMessage());
            }
        }

        return newData;
    }

    /**
     * 检查是否为系统字段
     */
    private boolean isSystemField(String fieldCode) {
        Set<String> systemFields = new HashSet<>(Arrays.asList(
                "id", "data_no", "dataNo", "title", "name", "code", "status",
                "process_instance_id", "processInstanceId", "processInstance_id",
                "process_start_time", "processStartTime", "process_startTime",
                "process_end_time", "processEndTime", "process_endTime",
                "current_task_id", "currentTaskId", "currentTask_id",
                "current_task_name", "currentTaskName", "currentTask_name",
                "submitter_id", "submitterId", "submitter_id",
                "submitter_name", "submitterName", "submitter_name",
                "submit_time", "submitTime", "submit_time",
                "created_at", "createdAt", "created_at",
                "updated_at", "updatedAt", "updated_at",
                "created_by", "createdBy", "created_by",
                "updated_by", "updatedBy", "updated_by",
                "deleted", "entity_code", "entityCode"
        ));
        return systemFields.contains(fieldCode);
    }

    /**
     * 验证迁移结果
     * 比较旧表和新表的数据条数
     */
    public Map<String, Object> validateMigration(String entityCode) {
        Map<String, Object> result = new HashMap<>();

        // 旧表数量
        int oldCount = oldDataMapper.findByEntityCode(entityCode).size();
        result.put("oldTableCount", oldCount);

        // 新表数量
        String tableName = dynamicTableService.getTableName(entityCode);
        long newCount = 0;
        if (dynamicTableService.tableExists(entityCode)) {
            newCount = dynamicMapper.count(tableName);
        }
        result.put("newTableCount", newCount);

        // 差异
        result.put("difference", oldCount - newCount);
        result.put("consistent", oldCount == newCount);

        return result;
    }
}
