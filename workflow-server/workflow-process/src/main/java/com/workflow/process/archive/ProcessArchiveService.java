package com.workflow.process.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.port.migration.model.history.HistoryIdentity;
import com.workflow.port.migration.EntityHistoryImportPort;
import com.workflow.port.migration.ProcessArchiveInstancePort;
import com.workflow.port.migration.model.history.HistoryImportModels.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.*;

/** 保存不可变的历史档案。页面读取额外按当前实体的可见行和字段投影。 */
@Service
@RequiredArgsConstructor
public class ProcessArchiveService implements ProcessArchiveInstancePort {
    private final ProcessVersionHistoryMapper versions;
    private final EntityHistoryImportPort entities;
    private final com.workflow.process.archive.infrastructure.persistence.ProcessArchiveMapper mapper;
    private final ObjectMapper json;

    @Override
    public List<ProcessTemplate> templates() {
        return versions.selectList(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getDeleted, 0).orderByDesc(ProcessVersionHistory::getPublishedAt))
                .stream().limit(500).map(this::convert).toList();
    }

    @Override
    public ProcessTemplate template(String id) {
        var version = versions.selectById(id);
        if (version == null || Integer.valueOf(1).equals(version.getDeleted())) throw new IllegalArgumentException("流程发布版本不存在");
        return convert(version);
    }

    private ProcessTemplate convert(ProcessVersionHistory version) {
        return new ProcessTemplate(version.getId(), version.getProcessName(), version.getVersion(),
                version.getBpmnXml(), nodeIndex(version.getBpmnXml()));
    }

    /** 仅解析节点身份，不执行 BPMN；禁用外部实体，源版本也不能触发文件或网络读取。 */
    public static Map<String, String> nodeIndex(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var elements = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "*");
            Map<String, String> nodes = new LinkedHashMap<>();
            for (int i = 0; i < elements.getLength(); i++) {
                var element = (org.w3c.dom.Element) elements.item(i);
                if (!Set.of("userTask", "manualTask", "serviceTask", "task", "startEvent", "endEvent", "subProcess", "receiveTask", "scriptTask").contains(element.getLocalName())) continue;
                String id = element.getAttribute("id");
                if (!id.isBlank()) nodes.put(id, element.getAttribute("name").isBlank() ? id : element.getAttribute("name"));
            }
            return nodes;
        } catch (Exception invalid) { throw new IllegalArgumentException("流程图无法解析", invalid); }
    }

    /** 一次性保存完整事件数组，保留同节点多次办理；同源不同内容报冲突而不覆盖。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String save(Archive archive) {
        String sourceKey = HistoryIdentity.hash(archive.sourceSystem(), archive.instance().sourceId());
        String document = encode(archive);
        String hash = HistoryIdentity.hash(document);
        var matches = mapper.findSource(sourceKey);
        if (!matches.isEmpty()) {
            if (!matches.get(0).contentHash().equals(hash)) throw new BusinessConflictException("ARCHIVE_SOURCE_CONFLICT", "同一来源流程的历史内容或映射不同: " + archive.instance().sourceId());
            return matches.get(0).id();
        }
        String id = UUID.randomUUID().toString();
        mapper.insert(
                id, sourceKey, archive.sourceSystem(), archive.instance().sourceId(), archive.entityCode(), archive.recordId(),
                archive.instance().name(), archive.instance().result(), hash, document);
        return id;
    }

    /**
     * 在实体查看权限和当前数据范围内列出迁入流程轮次，供普通详情页复用。
     * 不要求导入管理权限；无档案返回空列表，无权查看关联实体时拒绝读取。
     * @param entityCode 关联实体编码，与记录 ID 一起限定档案查询范围
     * @param recordId 当前业务记录 ID，读取前重新校验当前用户的数据范围
     * @return 按迁入时间排序的档案摘要，不包含原始档案文档
     */
    public List<Map<String, Object>> listForUser(String entityCode, String recordId) {
        entities.requireReadable(entityCode, recordId);
        return list(entityCode, recordId);
    }

    @Override
    public List<Map<String, Object>> list(String entityCode, String recordId) {
        return mapper.list(entityCode, recordId).stream().map(row -> Map.<String,Object>of("id", row.id(), "name", row.name(),
                "result", row.result(), "sourceSystem", row.sourceSystem(), "sourceId", row.sourceId())).toList();
    }

    /**
     * 按档案关联的实体记录重新授权并读取只读详情；关联快照逐条检查权限。
     * @param id 档案 ID，仅定位档案，不授予访问权
     * @return 经过当前实体字段权限裁剪的档案详情
     * @throws ForbiddenException 当前用户无权查看主业务记录
     * @throws IllegalArgumentException 档案不存在
     */
    public Map<String, Object> detailForUser(String id) {
        return detail(id, true);
    }

    @Override
    public Map<String, Object> detail(String id) {
        return detail(id, false);
    }

    private Map<String, Object> detail(String id, boolean checkAccess) {
        var document = mapper.document(id);
        if (document == null) throw new IllegalArgumentException("归档不存在");
        Archive archive;
        try { archive = json.readerFor(Archive.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(document); }
        catch (Exception failure) { throw new IllegalStateException("归档数据无法读取", failure); }
        Map<String, Object> values = projectSnapshot(archive.entityCode(), archive.recordId(), archive.data(), checkAccess);
        List<Map<String, Object>> events = archive.events().stream().sorted(Comparator.comparingInt(EventRow::sequence).thenComparing(EventRow::sourceId))
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", e.sourceId()); item.put("nodeId", archive.nodeMappings().get(e.node()));
                    item.put("sourceNode", e.node()); item.put("actor", e.actor()); item.put("action", archive.actionMappings().getOrDefault(e.action(), e.action()));
                    item.put("rawAction", e.action()); item.put("comment", e.comment()); item.put("handledAt", e.handledAt()); item.put("sequence", e.sequence()); return item;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("sourceType", "HISTORICAL_IMPORT"); result.put("sourceSystem", archive.sourceSystem());
        result.put("instance", archive.instance()); result.put("template", archive.template()); result.put("entityCode", archive.entityCode());
        result.put("recordId", archive.recordId()); result.put("data", values); result.put("fields", archive.schema().fields().stream().filter(f -> values.containsKey(f.code())).toList());
        result.put("events", events); result.put("dataScope", "ENTITY_FINAL");
        List<Map<String, Object>> related = new ArrayList<>();
        if (archive.related() != null) for (var record : archive.related()) {
            try {
                var data = projectSnapshot(record.entityCode(), record.recordId(), record.data(), checkAccess);
                related.add(Map.of("entityCode", record.entityCode(), "recordId", record.recordId(), "name", record.schema().name(),
                        "data", data, "fields", record.schema().fields().stream().filter(f -> data.containsKey(f.code())).toList()));
            } catch (ForbiddenException denied) {
                // 一条共享业务记录权限收回不影响主档案；不可读关联不返回标识或字段。
            }
        }
        result.put("related", related);
        return result;
    }

    private Map<String, Object> projectSnapshot(
            String entityCode, String recordId, Map<String, Object> snapshot, boolean checkAccess) {
        Set<String> visibleFields = null;
        if (checkAccess) {
            var readable = entities.readable(entityCode, recordId);
            visibleFields = entities.schema(entityCode).fields().stream().map(Field::code)
                    .filter(readable::containsKey).collect(java.util.stream.Collectors.toSet());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (var entry : snapshot.entrySet()) {
            if (visibleFields == null || visibleFields.contains(entry.getKey())) {
                // 审计展示按十进制文本返回，避免浏览器 JSON number 丢失大金额的小数位。
                Object value = entry.getValue();
                values.put(entry.getKey(), value instanceof java.math.BigDecimal decimal ? decimal.toPlainString() : value);
            }
        }
        return values;
    }

    private String encode(Object value) {
        try { return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalArgumentException("归档编码失败", invalid); }
    }
}
