package com.workflow.process.definition.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.entity.port.EntityUserPathPort;
import com.workflow.process.assignment.domain.EntityUserReferenceFieldConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 发布时把人员路径重编译为本环境实体版本，浏览器提供的版本坐标不作为权威输入。 */
@Component
@RequiredArgsConstructor
public class EntityUserPathBpmnCompiler {
    private final EntityUserPathPort paths;
    private final ObjectMapper json;

    /** 只处理已知人员配置属性；嵌套的下一审批人 RESOLVER 使用同一编译规则。 */
    public boolean compile(Document document) throws com.fasterxml.jackson.core.JsonProcessingException {
        boolean changed = false;
        var properties = document.getElementsByTagNameNS("http://flowable.org/bpmn", "property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (!Set.of("assigneeConfig", "multiInstanceConfig").contains(property.getAttribute("name"))) continue;
            JsonNode value = json.readTree(property.getAttribute("value"));
            if (compileNode(value)) {
                property.setAttribute("value", json.writeValueAsString(value));
                changed = true;
            }
        }
        return changed;
    }

    /** 只遍历可执行的已知配置槽位，不解释自定义解析器参数中的同名 JSON 键。 */
    private boolean compileNode(JsonNode node) {
        if (!(node instanceof ObjectNode object)) return false;
        boolean changed = false;
        String type = node.path("assigneeType").asText();
        if (Set.of("interface", "resolver").contains(type)) {
            String resolver = node.path("resolverCode").asText(node.path("interfaceName").asText());
            changed |= compileParams(object, resolver, "extraParams");
        }
        String collection = node.path("collectionResolverCode").asText(node.path("collectionInterface").asText());
        changed |= compileParams(object, collection, "collectionExtraParams");
        JsonNode selection = node.path("nextApproverSelection");
        if (selection.path("visible").asBoolean() && "RESOLVER".equals(selection.path("source").path("type").asText())
                && selection.path("source") instanceof ObjectNode source) {
            changed |= compileParams(source, source.path("resolverCode").asText(), "extraParams");
        }
        return changed;
    }

    private boolean compileParams(ObjectNode node, String resolver, String key) {
        if (!EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(resolver) || node.path(key).path("schemaVersion").asInt() != 2) return false;
        ObjectNode params = (ObjectNode) node.path(key);
        // 发布重新钉定，跨环境旧 historyId、用户伪造版本或旧发布快照均不会被沿用。
        params.remove("versions");
        var config = EntityUserReferenceFieldConfig.parse(json.convertValue(params, Map.class));
        var description = paths.describe(config.entityCode(), config.path(), List.of());
        params.set("versions", json.valueToTree(description.versions()));
        params.set("path", json.valueToTree(description.path()));
        return true;
    }
}
