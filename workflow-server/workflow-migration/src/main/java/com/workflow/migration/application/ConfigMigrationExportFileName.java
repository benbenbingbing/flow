package com.workflow.migration.application;

import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** 为下载文件生成可辨识的资产名称，包内编号和签名仍由编解码器维护。 */
final class ConfigMigrationExportFileName {
    private static final int MAX_FILE_NAME_BYTES = 255;

    /**
     * 初始化配置迁移导出文件名称，保存构造参数供后续方法使用。
     */
    private ConfigMigrationExportFileName() {
    }

    /**
     * 根据用户选中的资产生成文件名；自动补齐的依赖不应冒充本次导出的主体。
     * 批量导出展示前两项和总数，并保留完整包编号以区分重复导出。
     *
     * @param packageNo 本次生成的唯一包编号
     * @param selectedAssets 本次显式选择且已去重的资产，不包含自动补齐的依赖
     * @return 带类型、名称、编码、版本及 .wfpack 扩展名的安全文件名
     */
    static String create(String packageNo, List<ConfigMigrationAsset> selectedAssets) {
        String suffix = "_" + sanitize(packageNo) + ".wfpack";
        boolean multiple = selectedAssets.size() > 1;
        String prefix = multiple ? "批量-" : "";
        String count = multiple ? "-共" + selectedAssets.size() + "项" : "";
        int shown = Math.min(selectedAssets.size(), 2);
        int budget = MAX_FILE_NAME_BYTES - bytes(suffix + prefix + count) - Math.max(0, shown - 1);
        if (budget < 48) {
            throw new IllegalArgumentException("迁移标记过长，无法生成导出文件名");
        }
        String description = selectedAssets.isEmpty() ? "配置迁移" : selectedAssets.stream()
                .limit(shown)
                .map(asset -> describe(asset, budget / shown))
                .collect(Collectors.joining("_"));
        return prefix + description + count + suffix;
    }

    /**
     * 生成{@code describe}文本，供后续匹配或展示。
     *
     * @param asset 资产，作为 {@code sanitize} 的输入影响后续处理
     * @param budget {@code budget}，作为 {@code truncate} 的输入影响后续处理
     * @return 处理后的{@code describe}文本，供调用方比较或展示
     */
    private static String describe(ConfigMigrationAsset asset, int budget) {
        String type = switch (asset.getAssetType()) {
            case "ENTITY" -> "实体";
            case "PROCESS" -> "流程";
            case "SYSTEM_ENTITY_UI" -> "系统实体UI";
            case "DICTIONARY" -> "字典";
            case "WORK_CALENDAR" -> "工作日历";
            case "TASK_SLA_POLICY" -> "SLA策略";
            default -> "配置";
        };
        String version = asset.getSourceVersion() == null ? "" : "-v" + asset.getSourceVersion();
        String name = sanitize(asset.getAssetName());
        String code = sanitize(asset.getBusinessKey());
        if (name.isBlank() || name.equals(code)) {
            return type + "-" + truncate(code, budget - bytes(type + "-" + version)) + version;
        }
        // 分别给名称和编码预留空间，避免长中文名称把可用于定位的编码全部挤掉。
        int textBudget = budget - bytes(type + "--" + version);
        int nameBudget = Math.min(bytes(name), Math.max(textBudget / 2, textBudget - bytes(code)));
        return type + "-" + truncate(name, nameBudget) + "-"
                + truncate(code, textBudget - nameBudget) + version;
    }

    /**
     * 清洗配置迁移导出文件名称；结果供调用方的后续步骤使用。
     *
     * @param value 待清洗配置迁移导出文件名称的原始输入，结果供调用方继续使用
     * @return 清洗后的配置迁移导出文件名称文本，供调用方比较或展示
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "-")
                .replaceAll("\\s+", " ").trim().replaceAll("^[. ]+|[. ]+$", "");
    }

    /**
     * 按 UTF-8 字节截断且保留完整字符，兼容中文文件名在常见文件系统中的长度限制。
     *
     * @param value 待处理{@code truncate}的原始输入，结果供调用方继续使用
     * @param budget {@code budget}，供本方法处理{@code truncate}时使用
     * @return 处理后的{@code truncate}文本，供调用方比较或展示
     */
    private static String truncate(String value, int budget) {
        int end = 0;
        int used = 0;
        while (end < value.length()) {
            int next = end + Character.charCount(value.codePointAt(end));
            int size = bytes(value.substring(end, next));
            if (used + size > budget) {
                break;
            }
            used += size;
            end = next;
        }
        return value.substring(0, end);
    }

    /**
     * 处理字节，并将结果传给后续步骤。
     *
     * @param value 待处理字节的原始输入，结果供调用方继续使用
     * @return 处理后的字节结果，供调用方继续处理
     */
    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
