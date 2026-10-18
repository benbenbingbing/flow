package com.workflow.process.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.admin.authorization.application.PermissionUtil;
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

/** 保存来源事实与重绘图的不可变展示档案；查看时再次执行实体数据范围授权。 */
@Service
@RequiredArgsConstructor
public class ProcessArchiveService implements ProcessArchivePort {
    private final ProcessVersionHistoryMapper versions;
    private final EntityHistoryImportPort entities;
    private final com.workflow.process.archive.infrastructure.persistence.ProcessArchiveMapper mapper;
    private final ObjectMapper json;

    @Override
    public List<ProcessTemplate> templates() {
        require("history-import:read");
        return versions.selectList(Wrappers.<ProcessVersionHistory>lambdaQuery()
                .eq(ProcessVersionHistory::getDeleted, 0).orderByDesc(ProcessVersionHistory::getPublishedAt))
                .stream().limit(500).map(this::convert).toList();
    }

    @Override
    public ProcessTemplate template(String id) {
        require("history-import:read");
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
        require("history-import:execute");
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

    @Override
    public List<Map<String, Object>> list(String entityCode, String recordId) {
        require("process:archive:read");
        entities.readable(entityCode, recordId);
        return mapper.list(entityCode, recordId).stream().map(row -> Map.<String,Object>of("id", row.id(), "name", row.name(),
                "result", row.result(), "sourceSystem", row.sourceSystem(), "sourceId", row.sourceId())).toList();
    }

    @Override
    public Map<String, Object> detail(String id) {
        require("process:archive:read");
        var document = mapper.document(id);
        if (document == null) throw new IllegalArgumentException("归档不存在");
        Archive archive;
        try { archive = json.readerFor(Archive.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(document); }
        catch (Exception failure) { throw new IllegalStateException("归档数据无法读取", failure); }
        Map<String, Object> values = visibleSnapshot(archive.entityCode(), archive.recordId(), archive.data());
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
                var data = visibleSnapshot(record.entityCode(), record.recordId(), record.data());
                related.add(Map.of("entityCode", record.entityCode(), "recordId", record.recordId(), "name", record.schema().name(),
                        "data", data, "fields", record.schema().fields().stream().filter(f -> data.containsKey(f.code())).toList()));
            } catch (ForbiddenException denied) {
                // 一条共享业务记录权限收回不影响主档案；不可读关联不返回标识或字段。
            }
        }
        result.put("related", related);
        return result;
    }

    private Map<String, Object> visibleSnapshot(String entityCode, String recordId, Map<String, Object> snapshot) {
        var readable = entities.readable(entityCode, recordId);
        var currentFields = entities.schema(entityCode).fields().stream().map(Field::code).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> values = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> {
            if (readable.containsKey(key) && currentFields.contains(key))
                // 审计展示按十进制文本返回，避免浏览器 JSON number 丢失大金额的小数位。
                values.put(key, value instanceof java.math.BigDecimal decimal ? decimal.toPlainString() : value);
        });
        return values;
    }

    private String encode(Object value) {
        try { return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalArgumentException("归档编码失败", invalid); }
    }
    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) throw new ForbiddenException("缺少权限: " + permission);
    }
}
