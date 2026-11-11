package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinition;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.contracts.migration.spi.ConfigMigrationParameterReferenceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.*;

/** 身份和动作目录只按稳定编码复用；此服务绝不创建目录数据。 */
@Service
@RequiredArgsConstructor
public class ConfigMigrationReferenceService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysGroupMapper groupMapper;
    private final SysOrganizationMapper organizationMapper;
    private final FlowActionDefinitionMapper actionMapper;
    private final FlowActionCatalogPort actionCatalog;
    private List<ConfigMigrationParameterReferenceProvider> parameterReferenceProviders = List.of();

    /** 扩展可选择注册参数引用契约；无适配器时仍检查固定目录 ID，并给出具体参数路径与建议。 */
    @Autowired(required = false)
    void configureParameterReferences(List<ConfigMigrationParameterReferenceProvider> providers) {
        parameterReferenceProviders = List.copyOf(providers);
    }

    /** 导出 ID 语义字段为带类型的编码引用，并补充真实依赖清单。 */
    Map<String, Object> exportReferences(Map<String, Object> snapshot) {
        List<Map<String, Object>> dependencies = dependencies(snapshot);
        if (snapshot.get("bpmnXml") instanceof String xml) {
            ConfigMigrationBpmnReferences.formReferences(xml).forEach(form -> dependencies.add(Map.of(
                    "type", "FORM", "key", form, "required", true, "source", "BPMN 实体表单绑定")));
        }
        BiFunction<String, String, String> identity = (type, id) -> {
            String key = id.startsWith("wf-ref://") ? code(type, id) : sourceCode(type, id);
            dependencies.add(Map.of("type", type, "key", key, "required", true,
                    "targetOnly", true, "source", "权限规则固定身份"));
            return reference(type, key);
        };
        Map<String, Object> result = rewrite(
                ConfigMigrationValueReferences.rewrite(snapshot, identity, true), identity, UnaryOperator.identity());
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map<String, Object> action : maps(result.get("flowActions"))) {
            String actionCode = text(action.get("actionCode"));
            if (actionCode.isBlank()) {
                FlowActionDefinition definition = null;
                String id = text(action.get("actionDefinitionId"));
                if (!id.isBlank()) definition = actionMapper.selectById(id);
                if (definition == null) definition = actionByHandler(text(action.get("interfaceName")));
                actionCode = definition.getActionCode();
            }
            action.remove("actionDefinitionId");
            action.put("actionCode", actionCode);
            dependencies.add(Map.of("type", "FLOW_ACTION_CODE", "key", actionCode,
                    "required", true, "targetOnly", true, "source", "流程动作"));
            actions.add(action);
        }
        if (result.containsKey("flowActions")) result.put("flowActions", actions);
        // 新包以 actionCode 为准，旧 handlerName 仅保留说明，不再要求两套映射同时存在。
        if (!actions.isEmpty()) dependencies.removeIf(value -> "FLOW_ACTION_HANDLER".equals(value.get("type")));
        rewriteAssignments(result, (type, key, context) -> {
            if (!type.endsWith("_ID")) return key;
            String identityType = type.substring(0, type.length() - 3);
            String stable = key.startsWith("wf-ref://") ? code(identityType, key) : sourceCode(identityType, key);
            dependencies.add(Map.of("type", identityType, "key", stable, "required", true,
                    "targetOnly", true, "source", "流程固定身份 / " + context,
                    "references", List.of(new java.util.LinkedHashMap<>(context))));
            return reference(identityType, stable);
        });
        // 每个导出副本独立缓存目录检查；不会扫描整张身份表，也不持有跨请求的过期 ID。
        Map<String, String> checkedIds = new HashMap<>();
        result = ConfigMigrationOpaqueReferences.rewrite(result, identity, parameterReferenceProviders,
                id -> checkedIds.computeIfAbsent(id, this::sourceIdentityType), true);
        result.put("dependencies", ConfigMigrationAssignmentSupport.mergeDependencies(dependencies));
        return result;
    }

    /** 分析和执行共用解析器；在任何业务写入之前即可校验全部身份与动作引用。 */
    Map<String, Object> importReferences(Map<String, Object> snapshot, BiFunction<String, String, String> mapping) {
        if (snapshot.get("bpmnXml") instanceof String xml) ConfigMigrationBpmnReferences.formReferences(xml);
        BiFunction<String, String, String> identity =
                (type, value) -> targetId(type, mapping.apply(type, code(type, value)));
        Map<String, Object> parameters = ConfigMigrationOpaqueReferences.rewrite(snapshot, identity,
                parameterReferenceProviders, unused -> "", false);
        Map<String, Object> result = rewrite(
                ConfigMigrationValueReferences.rewrite(parameters, identity, false), identity, UnaryOperator.identity());
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map<String, Object> action : maps(result.get("flowActions"))) {
            String actionCode = text(action.remove("actionCode"));
            // 新包必须具有 actionCode，源 ID 即使撞到合法对象也不能参与解析。
            action.remove("actionDefinitionId");
            if (actionCode.isBlank()) throw new IllegalArgumentException("流程动作缺少 actionCode，请重新导出");
            FlowActionDefinition definition = actionByCode(mapping.apply("FLOW_ACTION_CODE", actionCode));
            if (!Boolean.TRUE.equals(definition.getEnabled())
                    || !actionCatalog.isConfiguredAndAvailable(definition.getHandlerName())) {
                throw new IllegalArgumentException("流程动作不可用: " + definition.getActionCode());
            }
            action.put("actionDefinitionId", definition.getId());
            action.put("interfaceName", definition.getHandlerName());
            actions.add(action);
        }
        if (result.containsKey("flowActions")) result.put("flowActions", actions);
        return result;
    }

    /** 从实际正文重查人员声明，防止缺失或过期的依赖清单绕过导入校验。 */
    void validateAssignments(Map<String, Object> snapshot, BiFunction<String, String, String> mapping) {
        rewriteAssignments(object(snapshot), (type, key, context) -> {
            String identityType = type.endsWith("_ID") ? type.substring(0, type.length() - 3) : type;
            if (java.util.Set.of("USER", "ROLE", "GROUP", "DEPT").contains(identityType)) {
                String stable = type.endsWith("_ID") ? code(identityType, key) : key;
                targetId(identityType, mapping.apply(identityType, stable));
            }
            return key;
        });
    }

    private static void rewriteAssignments(Map<String, Object> snapshot,
            ConfigMigrationAssignmentSupport.ReferenceMapper mapper) {
        if (snapshot.get("bpmnXml") instanceof String xml) snapshot.put("bpmnXml",
                ConfigMigrationAssignmentSupport.rewriteBpmn(xml, text(snapshot.get("businessKey")), mapper));
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> node : maps(snapshot.get("nodes"))) {
            if (node.get("configJson") instanceof String config) node.put("configJson",
                    ConfigMigrationAssignmentSupport.rewriteNodeConfig(config, Map.of("nodeId", text(node.get("nodeId"))), mapper));
            nodes.add(node);
        }
        if (snapshot.containsKey("nodes")) snapshot.put("nodes", nodes);
    }

    /** 仅在来源系统读 ID；兼容历史 username/code，并拒绝缺失对象及缺失编码。 */
    String sourceCode(String type, String key) {
        String result = null;
        switch (type) {
            case "USER" -> {
                var value = userMapper.selectById(key);
                if (value == null) value = userMapper.selectByUsername(key);
                if (value != null) result = value.getUsername();
            }
            case "ROLE" -> {
                var value = roleMapper.selectById(key);
                if (value == null) value = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, key));
                if (value != null) result = value.getRoleCode();
            }
            case "GROUP" -> {
                var value = groupMapper.selectById(key);
                if (value == null) value = groupMapper.selectByGroupCode(key);
                if (value != null) result = value.getGroupCode();
            }
            case "DEPT" -> {
                var value = organizationMapper.selectById(key);
                if (value == null) value = organizationMapper.selectByCode(key);
                if (value != null) result = value.getOrgCode();
            }
            default -> throw new IllegalArgumentException("未知身份引用类型: " + type);
        }
        if (result == null || result.isBlank()) throw new IllegalArgumentException("源身份不存在或缺少编码: " + type + "/" + key);
        return result;
    }

    /** 只在源端检查不透明配置里的字面量是否为真实目录主键，不将检查结果用于目标解析。 */
    private String sourceIdentityType(String id) {
        if (id.isBlank() || id.startsWith("wf-ref://") || id.length() > 128) return "";
        if (userMapper != null && userMapper.selectById(id) != null) return "USER";
        if (roleMapper != null && roleMapper.selectById(id) != null) return "ROLE";
        if (groupMapper != null && groupMapper.selectById(id) != null) return "GROUP";
        if (organizationMapper != null && organizationMapper.selectById(id) != null) return "DEPT";
        return "";
    }

    /** 目标端只查询编码；禁止任何 selectById 回退，避免源 ID 撞号越权。 */
    String targetId(String type, String key) {
        String result = switch (type) {
            case "USER" -> { var value = userMapper.selectByUsername(key); yield value == null ? null : value.getId(); }
            case "ROLE" -> { var value = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, key)); yield value == null ? null : value.getId(); }
            case "GROUP" -> { var value = groupMapper.selectByGroupCode(key); yield value == null ? null : value.getId(); }
            case "DEPT" -> { var value = organizationMapper.selectByCode(key); yield value == null ? null : value.getId(); }
            default -> throw new IllegalArgumentException("未知身份引用类型: " + type);
        };
        if (result == null || result.isBlank()) throw new IllegalArgumentException("目标身份编码不存在: " + type + "/" + key);
        return result;
    }

    FlowActionDefinition actionByCode(String code) {
        var value = actionMapper.selectOne(new LambdaQueryWrapper<FlowActionDefinition>().eq(FlowActionDefinition::getActionCode, code));
        if (value == null) throw new IllegalArgumentException("动作编码不存在: " + code);
        return value;
    }

    private FlowActionDefinition actionByHandler(String name) {
        if (name.isBlank()) throw new IllegalArgumentException("流程动作缺少稳定编码和处理器名，请重新导出");
        var value = actionMapper.selectOne(new LambdaQueryWrapper<FlowActionDefinition>().eq(FlowActionDefinition::getHandlerName, name));
        if (value == null) throw new IllegalArgumentException("动作处理器不存在: " + name);
        return value;
    }

    private static List<Map<String, Object>> dependencies(Map<String, Object> snapshot) {
        return new ArrayList<>(maps(snapshot.get("dependencies")));
    }

    static List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> list ? list.stream().map(ConfigMigrationReferenceSupport::object).toList() : List.of();
    }
}
