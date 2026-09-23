package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigReadRow;
import java.util.List;

/**
 * 将旧发布行投影为当前配置文档。业务优先级集中在实体模块，数据库只读取原始文本。
 * 有效 active release 优先；无效或不存在时原样保留当前文档，legacy draft 永不参与回退。
 */
final class CurrentVersionDocumentProjection {
    /**
     * 初始化当前版本文档投影，保存构造参数供后续方法使用。
     */
    private CurrentVersionDocumentProjection() {}
    private static final ObjectReader JSON = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(100).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .reader();

    /**
     * 返回独立当前行，避免附带的 release 原始数据流入业务返回值或被误写回配置表。
     *
     * @param row 行，作为 {@code current.setId} 的输入影响后续处理
     * @return 解析后的当前版本文档投影结果，供调用方继续处理
     */
    static EntityVersionConfig resolve(EntityVersionConfigReadRow row) {
        if (row == null) return null;
        var current = new EntityVersionConfig();
        current.setId(row.getId());
        current.setEntityId(row.getEntityId());
        current.setEntityCode(row.getEntityCode());
        current.setEnabled(row.getEnabled());
        current.setRevision(row.getRevision());
        current.setCreateBy(row.getCreateBy());
        current.setCreateTime(row.getCreateTime());
        current.setUpdateBy(row.getUpdateBy());
        current.setUpdateTime(row.getUpdateTime());
        current.setDeleted(row.getDeleted());
        current.setConfigDocument(effectiveDocument(row));
        return current;
    }

    /**
     * 生成有效文档文本，供后续匹配或展示。
     *
     * @param row 行，作为 {@code JSON.readTree} 的输入影响后续处理
     * @return 处理后的有效文档文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String effectiveDocument(EntityVersionConfigReadRow row) {
        if (row.getSourceReleaseId() == null || row.getSourceReleaseDocument() == null) return row.getConfigDocument();
        try {
            JsonNode release = JSON.readTree(row.getSourceReleaseDocument());
            if (release == null || release.isMissingNode() || hasOutOfRangeNumber(release)) return row.getConfigDocument();
            // 与既有 JSON_SET/REMOVE 一致：只调整对象根节点，保留数组、标量和 JSON null 的原有语义。
            if (release instanceof ObjectNode object) {
                object.put("schemaVersion", row.getSourceContractVersion() == null ? 1 : row.getSourceContractVersion());
                object.remove(List.of("status", "migrationState", "activeReleaseId", "activeReleaseVersion"));
            }
            return release.toString();
        } catch (StreamConstraintsException oversizedRelease) {
            // 原 MySQL 对超过 100 层的 JSON 抛错。保持失败可见，不能静默用旧配置掩盖异常发布。
            throw new IllegalArgumentException("已发布的版本配置 JSON 超出解析限制", oversizedRelease);
        } catch (JsonProcessingException invalidRelease) {
            // 必须验证整个 JSON 输入，不能接受“合法对象后附带垃圾”并覆盖有效的当前配置。
            return row.getConfigDocument();
        }
    }

    /**
     * 延续历史 JSON_VALID 的数值范围；1e999 等文本不能成为有效发布并覆盖当前配置。
     *
     * @param node 节点，供本方法判断是否具有{@code out}范围数值时使用
     * @return {@code out}范围数值条件成立时为 true，否则为 false
     */
    private static boolean hasOutOfRangeNumber(JsonNode node) {
        if (node.isNumber() && !Double.isFinite(node.doubleValue())) return true;
        for (JsonNode child : node) if (hasOutOfRangeNumber(child)) return true;
        return false;
    }
}
