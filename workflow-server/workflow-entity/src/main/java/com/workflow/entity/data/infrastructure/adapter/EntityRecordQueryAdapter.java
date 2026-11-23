package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.port.entity.mutation.model.EntitySubmissionCommand;
import com.workflow.port.entity.mutation.model.EntitySubmissionCommand.Intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.model.EntityRecordPage;
import com.workflow.port.entity.EntityRecordPort;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import com.workflow.entity.data.application.mapping.EntityRecordRevision;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 复用实体运行时的权限与发布态查询，只向调用方返回独立业务投影。 */
@Component
public class EntityRecordQueryAdapter implements EntityRecordPort {
    private final EntityDataDynamicService records;
    private final ObjectMapper json;
    private final ObjectProvider<EntityDataActionService> actions;
    private final EntitySubmissionAdapter submissions;

    public EntityRecordQueryAdapter(EntityDataDynamicService records, ObjectMapper json,
            ObjectProvider<EntityDataActionService> actions, @Lazy EntitySubmissionAdapter submissions) {
        this.records = records;
        this.json = json;
        this.actions = actions;
        this.submissions = submissions;
    }

    /** 不绕过实体读取策略；不存在记录保持原有 null 语义。 */
    @Override
    public EntityRecordData findById(String entityCode, String recordId) {
        actions.getObject().requirePublishedQueryPermission(entityCode, null, true);
        return revisionProjection(records.findVisibleById(entityCode, recordId));
    }

    @Override
    public EntityRecordData findById(String entityCode, String listKey, String recordId) {
        actions.getObject().requirePublishedQueryPermission(entityCode, listKey, true);
        return revisionProjection(records.findVisibleById(entityCode, recordId, listKey));
    }

    private EntityRecordData revisionProjection(EntityDataDTO record) {
        if (record == null) return null;
        var view = EntityRecordProjection.project(record);
        view.setRevision(EntityRecordRevision.calculate(json, json.convertValue(record, new TypeReference<>() { })));
        return view;
    }

    /** 条件白名单和数据范围过滤均由原实体服务执行。 */
    @Override
    public List<EntityRecordData> findByCondition(String entityCode, Map<String, Object> condition) {
        return records.findByCondition(entityCode, condition).stream().map(EntityRecordProjection::project).toList();
    }

    @Override
    public List<EntityRecordData> findByIds(String entityCode, List<String> recordIds) {
        actions.getObject().requirePublishedQueryPermission(entityCode, null, true);
        var ids = recordIds.stream().filter(org.springframework.util.StringUtils::hasText).distinct().toList();
        var result = new java.util.ArrayList<EntityRecordData>();
        // 分段限制绑定参数量，排序仍由调用方提供的 ID 顺序决定。
        for (int offset = 0; offset < ids.size(); offset += 200) {
            var batch = ids.subList(offset, Math.min(offset + 200, ids.size()));
            var matches = findByCondition(entityCode, Map.of("id_op", "IN", "id", batch)).stream()
                    .collect(Collectors.toMap(EntityRecordData::getId, Function.identity()));
            batch.stream().map(matches::get).filter(java.util.Objects::nonNull).forEach(result::add);
        }
        return List.copyOf(result);
    }

    /** 将宿主分页结果投影为 Port 模型，保留实际生效的分页边界。 */
    @Override
    public EntityRecordPage page(String entityCode, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        return page(entityCode, null, condition, pageNumber, pageSize, sortField, descending);
    }

    @Override
    public EntityRecordPage page(String entityCode, String listKey, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        actions.getObject().requirePublishedQueryPermission(entityCode, listKey, false);
        var result = records.findPage(entityCode, listKey, condition, pageNumber, pageSize,
                sortField, descending ? "DESC" : "ASC");
        return new EntityRecordPage(result.getRecords().stream().map(EntityRecordProjection::project).toList(),
                result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    @Override
    public long count(String entityCode, Map<String, Object> condition) {
        actions.getObject().requirePublishedQueryPermission(entityCode, null, false);
        return records.countMatching(entityCode, condition);
    }

    @Override
    public EntityRecordData findByProcessInstance(String entityCode, String processInstanceId) {
        actions.getObject().requirePublishedQueryPermission(entityCode, null, true);
        return EntityRecordPort.super.findByProcessInstance(entityCode, processInstanceId);
    }

    @Override
    public EntityRecordData saveDraft(EntitySubmissionCommand command) {
        return submit(command, Intent.CREATE_DRAFT);
    }

    @Override
    public EntityRecordData update(EntitySubmissionCommand command) {
        return submit(command, Intent.UPDATE);
    }

    @Override
    public EntityRecordData createAndStart(EntitySubmissionCommand command) {
        return submit(command, Intent.CREATE_AND_START);
    }

    @Override
    public EntityRecordData startExisting(EntitySubmissionCommand command) {
        return submit(command, Intent.START_EXISTING);
    }

    @Override
    public EntityRecordData restart(EntitySubmissionCommand command) {
        return submit(command, Intent.RESTART);
    }

    @Override
    public void delete(String entityCode, String recordId, String listKey) {
        actions.getObject().deleteFromPort(entityCode, recordId, listKey);
    }

    @Override
    public void deleteBatch(String entityCode, List<String> recordIds, String listKey) {
        actions.getObject().batchDeleteFromPort(entityCode, recordIds, listKey);
    }

    @Override
    public Map<String, RecordCapability> getRecordCapabilities(String entityCode, String recordId, String listKey) {
        return actions.getObject().getRecordCapabilities(entityCode, recordId, listKey);
    }

    private EntityRecordData submit(EntitySubmissionCommand command, Intent expectedIntent) {
        // 在生成幂等回执前拒绝不匹配的意图，避免从一个方法意外进入另一个写入流程。
        if (command.intent() != expectedIntent) {
            throw new IllegalArgumentException("提交意图必须为 " + expectedIntent);
        }
        return submissions.submit(command);
    }
}
