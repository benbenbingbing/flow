package com.workflow.biz.project.custom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.list.model.ListFieldDataRecord;
import com.workflow.contracts.entity.list.spi.ListFieldDataProvider;
import com.workflow.contracts.entity.list.model.ListFieldDataConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列表自定义字段数据提供者示例。
 *
 * <p>数据源类型为 {@value #DATA_SOURCE_TYPE}。每个配置字段会在
 * {@code extData} 中写入可见的演示文本，便于从前端确认 Provider 已执行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCustomListFieldDataProvider
        implements ListFieldDataProvider {

    public static final String DATA_SOURCE_TYPE =
            "PROJECT_CUSTOM_FIELD";
    private static final String DEFAULT_LABEL_PREFIX =
            "项目扩展";

    private final ObjectMapper objectMapper;

    /**
     * 读取数据来源类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的数据来源类型文本，供调用方比较或展示
     */
    @Override
    public String getDataSourceType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义日志字段";
    }

    /**
     * 读取描述；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    @Override
    public String getDescription() {
        return "写入项目自定义演示文本并记录执行日志。";
    }

    /**
     * 判断是否支持查询；判断结果决定调用方的后续分支。
     *
     * @return 查询条件成立时为 true，否则为 false
     */
    @Override
    public boolean supportsQuery() {
        return false;
    }

    /**
     * 读取配置结构；查询结果供调用方展示或继续处理。
     *
     * @return 项目自定义列表字段数据提供者集合，供调用方遍历或展示
     */
    @Override
    public List<Map<String, Object>> getConfigSchema() {
        return List.of(Map.of(
                "key", "labelPrefix",
                "label", "展示前缀",
                "type", "text",
                "required", false,
                "defaultValue", "项目扩展"));
    }

    /**
     * 补充项目自定义列表字段数据提供者；结果供调用方的后续步骤使用。
     *
     * @param records 记录集合，供本方法补充项目自定义列表字段数据提供者时使用
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param context 执行上下文，向后续项目自定义列表字段数据提供者步骤传递身份、配置或状态
     */
    @Override
    public void enrich(
            List<ListFieldDataRecord> records,
            List<ListFieldDataConfig> fields,
            Map<String, Object> context) {
        List<ListFieldDataRecord> safeRecords =
                records == null ? List.of() : records;
        List<ListFieldDataConfig> safeFields =
                fields == null ? List.of() : fields;
        for (ListFieldDataConfig field : safeFields) {
            if (field == null
                    || field.getFieldCode() == null
                    || field.getFieldCode().isBlank()) {
                continue;
            }
            Map<String, Object> config =
                    parseConfig(field.getDataSourceConfig());
            String labelPrefix =
                    text(config.get("labelPrefix"),
                            DEFAULT_LABEL_PREFIX);
            log.info(
                    "开始补充项目列表字段数据: dataSourceType={}, entityCode={}, listKey={}, fieldCode={}, labelPrefix={}, recordCount={}",
                    DATA_SOURCE_TYPE,
                    LogValue.safe(context == null
                            ? null : context.get("entityCode")),
                    LogValue.safe(context == null
                            ? null : context.get("listKey")),
                    LogValue.safe(field.getFieldCode()),
                    LogValue.safe(labelPrefix),
                    safeRecords.size());
            for (ListFieldDataRecord record : safeRecords) {
                if (record == null) {
                    continue;
                }
                if (record.getExtData() == null) {
                    record.setExtData(new HashMap<>());
                }
                String identity =
                        record.getCode() == null
                                || record.getCode().isBlank()
                                ? record.getId()
                                : record.getCode();
                record.getExtData().put(
                        field.getFieldCode(),
                        labelPrefix
                                + ":"
                                + String.valueOf(identity));
            }
        }
        log.info(
                "项目列表字段数据补充完成: dataSourceType={}, entityCode={}, listKey={}, recordCount={}, fieldCount={}",
                DATA_SOURCE_TYPE,
                LogValue.safe(context == null
                        ? null : context.get("entityCode")),
                LogValue.safe(context == null
                        ? null : context.get("listKey")),
                safeRecords.size(),
                safeFields.size());
    }

    /**
     * 解析单列的数据源配置。注册中心保存列表时已做过 JSON 校验，
     * 这里再次解析是为了让运行期直接使用列级参数。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 配置键值结果，供调用方继续处理
     */
    private Map<String, Object> parseConfig(String document) {
        if (document == null || document.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> value =
                    objectMapper.readValue(
                            document,
                            new TypeReference<>() {
                            });
            return value == null
                    ? new LinkedHashMap<>()
                    : value;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "项目列表字段数据源配置不是合法 JSON",
                    exception);
        }
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value, String fallback) {
        if (value == null
                || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }
}
