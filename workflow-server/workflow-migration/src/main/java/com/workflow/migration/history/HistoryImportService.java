package com.workflow.migration.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import lombok.RequiredArgsConstructor;
import com.workflow.migration.history.infrastructure.persistence.HistoryImportBatchMapper;
import com.workflow.migration.history.infrastructure.persistence.HistoryImportBatchMapper.Batch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** 持久化批次协调器；每次执行推进一个有界业务组，客户端中断后可以继续。 */
@Service
@RequiredArgsConstructor
public class HistoryImportService {
    private final HistoryImportBatchMapper mapper;
    private final ObjectMapper json;
    private final HistoryImportPlanner planner;
    private final HistoryImportWorkbook workbook;
    private final EntityHistoryImportPort entities;
    private final ProcessArchivePort archives;
    private final PlatformTransactionManager transactions;

    public record GroupResult(String group, int created, int reused, List<Map<String, String>> records,
                              List<String> archiveIds) {}

    public Map<String, Object> catalog() {
        require("history-import:read");
        return Map.of("entities", entities.schemas(), "processes", archives.templates());
    }

    /** 创建仅当前登录用户可操作的批次；只暂存配置，尚不创建业务记录。 */
    @Transactional
    public Map<String, Object> create(Plan plan) {
        require("history-import:execute"); HistoryImportPlanner.validatePlan(plan);
        // 创建时验证实体可见性；执行仍会重查权限，不能把此检查当作永久授权。
        for (var spec : plan.entities()) entities.schema(spec.entityCode());
        String id = UUID.randomUUID().toString();
        mapper.create(id, UserContext.getUserId(), plan.name(), encode(plan));
        return view(owned(id, false));
    }

    public List<Map<String, Object>> list(int page, int size) {
        require("history-import:read");
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数错误");
        return mapper.list(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size, false), UserContext.getUserId())
                .getRecords().stream().map(row -> Map.<String,Object>of("id", row.id(), "name", row.name(), "state", row.state(),
                        "revision", row.revision(), "createdAt", row.createdAt())).toList();
    }

    public Map<String, Object> get(String id) { require("history-import:read"); return view(owned(id, false)); }

    /** 修改未执行批次并使预检失效；revision 防止并发页面覆盖，来源命名空间不可变。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updatePlan(String id, int revision, Plan plan) {
        require("history-import:execute"); var batch = editable(id, revision);
        HistoryImportPlanner.validatePlan(plan);
        if (!read(batch.plan(), Plan.class).sourceSystem().equals(plan.sourceSystem())) throw new IllegalArgumentException("批次来源系统不可修改，请新建批次");
        mapper.deleteGroups(id);
        mapper.plan(id, plan.name(), encode(plan));
        return view(owned(id, false));
    }

    public byte[] template(String id, String kind) throws java.io.IOException {
        require("history-import:read"); return workbook.template(read(owned(id, false).plan(), Plan.class), kind);
    }

    /** 解析并暂存标准文件，替换同类输入；任何格式错误均保持原批次内容不变。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upload(String id, int revision, String kind, MultipartFile file) throws java.io.IOException {
        require("history-import:execute"); var batch = editable(id, revision);
        Input parsed = workbook.parse(read(batch.plan(), Plan.class), kind, file);
        mapper.deleteGroups(id);
        mapper.upload(id, kind, encode(parsed));
        return view(owned(id, false));
    }

    /** 预检结果包含标准化输入及图快照，配置或文件变更会令预检 hash 失效。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validate(String id, int revision) {
        require("history-import:execute"); var batch = editable(id, revision);
        Plan plan = read(batch.plan(), Plan.class);
        var entityInput = batch.entities() == null ? new Input(List.of(), List.of(), List.of()) : read(batch.entities(), Input.class);
        var historyInput = batch.history() == null ? new Input(List.of(), List.of(), List.of()) : read(batch.history(), Input.class);
        var validated = planner.validate(plan, new Input(entityInput.entities(), historyInput.instances(), historyInput.events()));
        String document = encode(validated), hash = HistoryIdentity.hash(batch.plan(), document);
        mapper.deleteGroups(id);
        for (String group : validated.groups().keySet()) mapper.createGroup(UUID.randomUUID().toString(), id, group, HistoryIdentity.hash(group));
        mapper.ready(id, document, hash);
        return view(owned(id, false));
    }

    /** 一组包含关联实体与其流程历史，持有批次行锁直到事务完成，重复请求只推进未提交组。 */
    public Map<String, Object> executeNext(String id, String validationHash, String groupKey, boolean retry) {
        require("history-import:execute");
        String[] failedGroup = {null};
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); transaction.setTimeout(60);
        try {
            transaction.executeWithoutResult(status -> {
                Batch batch = owned(id, true);
                if (!Objects.equals(validationHash, batch.validationHash()) || batch.validation() == null) throw new BusinessConflictException("HISTORY_VALIDATION_CHANGED", "预检结果已变化，请重新校验");
                if (batch.state().equals("SUCCEEDED")) return;
                if (!Set.of("READY", "RUNNING", "PARTIAL_SUCCESS", "FAILED").contains(batch.state())) throw new IllegalArgumentException("批次尚未就绪");
                Plan plan = read(batch.plan(), Plan.class);
                var validated = read(batch.validation(), HistoryImportPlanner.Validated.class);
                Map<String, String> states = new HashMap<>();
                mapper.groups(id).forEach(row -> states.put(row.groupKey(), row.state()));
                // 请求固定提交组；响应丢失后的同组重试只返回既有结果，不能意外推进下一组。
                if ("COMMITTED".equals(states.get(groupKey))) return;
                String group = validated.groupOrder().stream().filter(g -> !"COMMITTED".equals(states.get(g))).findFirst().orElse(null);
                if (group == null) return;
                if (!Objects.equals(group, groupKey)) throw new BusinessConflictException("HISTORY_GROUP_ORDER", "请按预检的依赖顺序执行提交组");
                if ("FAILED".equals(states.get(group)) && !retry) throw new IllegalArgumentException("上一组失败，请检查错误后重试或修正文件创建新批次");
                failedGroup[0] = group;
                for (var spec : plan.entities()) if (!entities.schema(spec.entityCode()).fingerprint().equals(validated.schemas().get(spec.key()).fingerprint())) throw new BusinessConflictException("HISTORY_SCHEMA_CHANGED", "实体发布配置已变化，请创建新批次重新校验");
                GroupResult result = applyGroup(id, plan, validated, group);
                mapper.commitGroup(id, HistoryIdentity.hash(group), encode(result));
                mapper.state(id, mapper.pending(id) == 0 ? "SUCCEEDED" : "RUNNING");
            });
        } catch (RuntimeException failure) {
            // 业务事务回滚后另记失败；先锁批次并复查该组状态，避免覆盖并发重试的成功结果。
            if (failedGroup[0] != null) new TransactionTemplate(transactions).executeWithoutResult(status -> {
                owned(id, true);
                String message = failure.getMessage() == null ? "导入失败，请联系管理员" : failure.getMessage();
                int changed = mapper.failGroup(id, HistoryIdentity.hash(failedGroup[0]), message.substring(0, Math.min(1900, message.length())));
                if (changed > 0) {
                    mapper.state(id, mapper.committed(id) == 0 ? "FAILED" : "PARTIAL_SUCCESS");
                }
            });
            throw failure;
        }
        return get(id);
    }

    /** 在调用方组事务中按依赖创建实体并固定档案，来源 ID 转换不能改变幂等内容身份。 */
    private GroupResult applyGroup(String batchId, Plan plan, HistoryImportPlanner.Validated validated, String group) {
        var specs = HistoryImportPlanner.index(plan.entities(), EntitySpec::key, "实体配置");
        var rows = HistoryImportPlanner.index(validated.input().entities(), HistoryImportPlanner::key, "实体记录");
        Map<String, EntityResult> saved = new HashMap<>();
        Map<String, Map<String, Object>> frozenValues = new HashMap<>();
        int created = 0, reused = 0;
        List<Map<String, String>> recordResults = new ArrayList<>();
        for (String rowKey : validated.groups().get(group)) {
            EntityRow row = rows.get(rowKey); EntitySpec spec = specs.get(row.entityKey());
            Map<String, Object> values = new LinkedHashMap<>(row.values());
            for (var ref : HistoryImportPlanner.safe(spec.references()).entrySet()) {
                Object old = values.get(ref.getKey());
                List<String> targets = new ArrayList<>();
                for (String sourceId : HistoryImportPlanner.referenceIds(old)) {
                    EntityResult local = saved.get(HistoryImportPlanner.key(ref.getValue(), sourceId));
                    SourceRecord existing = local == null ? entities.findSource(plan.sourceSystem(), ref.getValue(), sourceId) : null;
                    if (local == null && existing == null) throw new IllegalArgumentException("关联记录尚未导入: " + sourceId);
                    targets.add(local != null ? local.id() : existing.recordId());
                }
                if (!targets.isEmpty()) values.put(ref.getKey(), old instanceof List<?> ? targets : targets.get(0));
            }
            String contentHash = HistoryIdentity.hash(encode(spec), encode(row.values()));
            EntityResult result = entities.create(plan.sourceSystem(), spec, row, contentHash, values, batchId);
            saved.put(rowKey, result); frozenValues.put(rowKey, values);
            if (result.reused()) reused++; else created++;
            recordResults.add(Map.of("entityCode", spec.entityCode(), "recordId", result.id(), "sourceId", row.sourceId(), "result", result.reused() ? "REUSED" : "CREATED"));
        }
        List<String> archiveIds = new ArrayList<>();
        for (InstanceRow instance : validated.input().instances()) {
            String rowKey = HistoryImportPlanner.key(instance.entityKey(), instance.recordId());
            if (!saved.containsKey(rowKey)) continue;
            var events = validated.input().events().stream().filter(e -> instance.sourceId().equals(e.instanceId())).toList();
            Map<String, RelatedRecord> related = new LinkedHashMap<>();
            for (String member : validated.groups().get(group)) {
                var memberRow = rows.get(member); var memberSpec = specs.get(memberRow.entityKey());
                if (!member.equals(rowKey)) related.put(member, new RelatedRecord(memberSpec.entityCode(), saved.get(member).id(),
                        frozenValues.get(member), validated.schemas().get(memberSpec.key())));
                // 共享业务实体可先在其他组提交；归档保留迁入当时可见值，随后业务修改不改写历史。
                for (var ref : HistoryImportPlanner.safe(memberSpec.references()).entrySet())
                    for (String sourceId : HistoryImportPlanner.referenceIds(memberRow.values().get(ref.getKey()))) {
                        String relatedKey = HistoryImportPlanner.key(ref.getValue(), sourceId);
                        if (saved.containsKey(relatedKey) || related.containsKey(relatedKey)) continue;
                        var target = entities.findSource(plan.sourceSystem(), ref.getValue(), sourceId);
                        related.put(relatedKey, new RelatedRecord(target.entityCode(), target.recordId(),
                                entities.readable(target.entityCode(), target.recordId()), validated.schemas().get(ref.getValue())));
                    }
            }
            archiveIds.add(archives.save(new Archive(plan.sourceSystem(), instance, specs.get(instance.entityKey()).entityCode(), saved.get(rowKey).id(),
                    validated.template(), frozenValues.get(rowKey), validated.schemas().get(instance.entityKey()),
                    HistoryImportPlanner.safe(plan.nodeMappings()), HistoryImportPlanner.safe(plan.actionMappings()), events, new ArrayList<>(related.values()))));
        }
        return new GroupResult(group, created, reused, recordResults, archiveIds);
    }

    private Batch editable(String id, int revision) {
        Batch batch = owned(id, true);
        if (batch.revision() != revision) throw new BusinessConflictException("HISTORY_REVISION_CHANGED", "批次已变化，请刷新");
        if (!Set.of("DRAFT", "UPLOADED", "READY").contains(batch.state())) throw new IllegalArgumentException("已开始执行的批次不可改写，请新建批次");
        return batch;
    }
    private Batch owned(String id, boolean lock) {
        Batch batch = lock ? mapper.lock(id) : mapper.get(id);
        if (batch == null || !Objects.equals(batch.owner(), UserContext.getUserId())) throw new ForbiddenException("批次不存在或无权访问");
        return batch;
    }
    private Map<String, Object> view(Batch batch) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", batch.id()); result.put("name", batch.name()); result.put("state", batch.state()); result.put("revision", batch.revision());
        result.put("plan", read(batch.plan(), Plan.class)); result.put("validationHash", batch.validationHash());
        result.put("entitiesUploaded", batch.entities() != null); result.put("historyUploaded", batch.history() != null);
        if (batch.history() != null) {
            var history = read(batch.history(), Input.class);
            result.put("sourceNodes", history.events().stream().map(EventRow::node).distinct().toList());
            result.put("sourceActions", history.events().stream().map(EventRow::action).distinct().toList());
        }
        if (batch.validation() != null) {
            var validation = read(batch.validation(), HistoryImportPlanner.Validated.class);
            result.put("counts", Map.of("entities", validation.input().entities().size(), "instances", validation.input().instances().size(), "events", validation.input().events().size(), "groups", validation.groups().size()));
            result.put("groupOrder", validation.groupOrder());
        }
        result.put("groups", mapper.groups(batch.id()).stream().map(row -> {
            Map<String, Object> group = new LinkedHashMap<>(); group.put("key", row.groupKey()); group.put("state", row.state());
            group.put("result", row.result() == null ? null : read(row.result(), GroupResult.class)); group.put("error", row.error()); return group;
        }).toList());
        return result;
    }
    private String encode(Object value) {
        try { return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("导入数据编码失败", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readerFor(type).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(value); }
        catch (Exception e) { throw new IllegalStateException("导入记录无法解析", e); }
    }
    private void require(String permission) { if (!PermissionUtil.hasPermission(permission)) throw new ForbiddenException("缺少权限: " + permission); }
}
