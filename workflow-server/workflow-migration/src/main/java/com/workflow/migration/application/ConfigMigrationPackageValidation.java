package com.workflow.migration.application;

import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * wfpack 的语义边界校验。文件哈希只能证明内容完整，不能证明字段值符合迁移协议；
 * 这些检查必须在导入批次及迁移基线入库前执行，人工确认来源也不能豁免。
 */
final class ConfigMigrationPackageValidation {

    // 旧快照仍由现有兼容读取逻辑处理，不能把包格式版本误当成快照版本。
    private static final Set<Integer> SNAPSHOT_VERSIONS = Set.of(1, 2, 3);
    private static final Map<String, Set<String>> SECTIONS_BY_TYPE = Map.of(
            "ENTITY", Set.of("definition", "fields", "relations", "statuses", "codeRule",
                    "forms", "lists", "dataPermissions", "scopePolicies", "scopeBindings", "menus",
                    "interfaceExtensions", "dataSources", "extensions", "eventBindings", "excelConfiguration"),
            "SYSTEM_ENTITY_UI", Set.of("forms", "lists", "interfaceExtensions", "dataSources", "extensions", "eventBindings"),
            "PROCESS", Set.of("definition", "bpmnXml", "nodes", "nodeForms", "nodeApprovals", "flowActions", "statusMappings"),
            "WORK_CALENDAR", Set.of("configuration"),
            "TASK_SLA_POLICY", Set.of("configuration"),
            "DICTIONARY", Set.of("definition", "items"));

    private ConfigMigrationPackageValidation() {}

    /** 校验源包编号并原样返回，避免缺失值被转换为字符串 null 或悄悄生成假来源编号。 */
    static String packageNo(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)
                || !text.equals(text.strip()) || "null".equalsIgnoreCase(text)
                || text.codePointCount(0, text.length()) > 100
                || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("发布包 packageNo 必须为非空字符串，最多 100 个字符，且不能包含首尾空白或控制字符");
        }
        return text;
    }

    /** 只接受 JSON 整数，禁止负值、小数和字符串版本污染导入历史及后续版本判断。 */
    static int positiveVersion(Object value, String field) {
        if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            try {
                int version = Integer.parseInt(value.toString());
                if (version > 0) return version;
            } catch (NumberFormatException ignored) {
                // JSON 可容纳比 Java/数据库 int 更大的整数，需要在写库前给出协议错误。
            }
        }
        throw new IllegalArgumentException("发布包 " + field + " 必须为 1～2147483647 的整数");
    }

    /** 核对清单声明与正文协议，不能用可读取的正文掩盖不支持或矛盾的版本声明。 */
    static void snapshotVersion(Map<String, Object> asset, Map<String, Object> snapshot, String path) {
        int declared = positiveVersion(asset.get("snapshotSchemaVersion"), path + " snapshotSchemaVersion");
        int actual = positiveVersion(snapshot.get("schemaVersion"), path + " schemaVersion");
        if (!SNAPSHOT_VERSIONS.contains(declared) || !SNAPSHOT_VERSIONS.contains(actual)) {
            throw new IllegalArgumentException("不支持的快照版本: " + path
                    + "，snapshotSchemaVersion=" + declared + "，schemaVersion=" + actual);
        }
        if (declared != actual) {
            throw new IllegalArgumentException("发布包快照版本不一致: " + path
                    + "，snapshotSchemaVersion=" + declared + "，schemaVersion=" + actual);
        }
    }

    /**
     * 拒绝未知或跨资产类型的分区，包括与合法名称混用及 full=true 时附带的非法名称。
     * assetType 为空时只核对全局名称，供还未读取资产的选择合并使用；读取快照后再次按类型校验。
     */
    static void selection(String assetType, Object value) {
        if (!(value instanceof Map<?, ?> selection) || !selection.containsKey("sections")) return;
        Set<String> allowed = assetType == null ? null : SECTIONS_BY_TYPE.get(assetType);
        if (allowed == null) {
            allowed = new TreeSet<>();
            SECTIONS_BY_TYPE.values().forEach(allowed::addAll);
        }
        if (!(selection.get("sections") instanceof Collection<?> sections)) {
            throw new IllegalArgumentException("发布包 selection.sections 必须为配置分区名称数组");
        }
        for (Object section : sections) {
            if (!(section instanceof String name) || !allowed.contains(name)) {
                throw new IllegalArgumentException("不支持的配置分区 selection.sections: " + section
                        + (assetType == null ? "" : "，资产类型=" + assetType)
                        + "，可选值=" + String.join(", ", new TreeSet<>(allowed)));
            }
        }
    }

    /**
     * 校验附加依赖清单的结构，不改变依赖权威来源：实际解析仍使用资产快照中的 dependencies。
     * 保留旧包省略可选标志及增加扩展属性的能力，不做未经定义的逐字节清单比较。
     */
    static void dependencies(Object value) {
        if (!(value instanceof Collection<?> dependencies)) {
            throw new IllegalArgumentException("发布包 dependencies.json 必须为依赖数组");
        }
        int index = 0;
        for (Object raw : dependencies) {
            String location = "发布包 dependencies.json[" + index++ + "]";
            if (!(raw instanceof Map<?, ?> dependency)
                    || !(dependency.get("type") instanceof String type) || !StringUtils.hasText(type)
                    || !(dependency.get("key") instanceof String key) || !StringUtils.hasText(key)) {
                throw new IllegalArgumentException(location + " 必须为包含非空字符串 type、key 的依赖对象");
            }
            for (String flag : Set.of("required", "targetOnly")) {
                if (dependency.containsKey(flag) && !(dependency.get(flag) instanceof Boolean)) {
                    throw new IllegalArgumentException(location + "." + flag + " 必须为布尔值");
                }
            }
        }
    }
}
