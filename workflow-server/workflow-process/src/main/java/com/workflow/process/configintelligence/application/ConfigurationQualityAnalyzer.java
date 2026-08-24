package com.workflow.process.configintelligence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityFinding;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 可解释的配置质量评分与智能建议引擎。 */
@Component
@RequiredArgsConstructor
public class ConfigurationQualityAnalyzer {

    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|secret|privateKey|accessToken|apiKey).*");
    private static final Pattern UNSAFE_SCRIPT = Pattern.compile(
            "(?i).*(<script|getRuntime\\s*\\(|getClass\\s*\\(|\\.class\\b|javascript:|System\\.exit).*",
            Pattern.DOTALL);
    private final ObjectMapper objectMapper;

    public QualityReport analyze(String assetType, String assetId, Map<String, Object> configuration) {
        JsonNode root = objectMapper.valueToTree(configuration == null ? Map.of() : configuration);
        List<QualityFinding> findings = new ArrayList<>();
        Map<String, String> ids = new HashMap<>();
        inspect(root, "$", 0, findings, ids);
        inspectDomainRules(root, findings);
        if (!root.hasNonNull("name") && !root.hasNonNull("title")) {
            findings.add(warning("CONFIG_NAME_MISSING", "$", "配置缺少可读名称", "补充名称以改善检索和审计", 5));
        }
        if (!root.hasNonNull("description") && !root.hasNonNull("documentation")) {
            findings.add(info("CONFIG_DESCRIPTION_MISSING", "$", "配置缺少用途说明", "补充业务目的、边界和责任人", 2));
        }
        int score = Math.max(0, 100 - findings.stream().mapToInt(QualityFinding::deduction).sum());
        String grade = score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : "D";
        int blockers = (int) findings.stream().filter(item -> "BLOCKER".equals(item.severity())).count();
        int warnings = (int) findings.stream().filter(item -> "WARNING".equals(item.severity())).count();
        List<String> suggestions = findings.stream()
                .map(QualityFinding::recommendation)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(8)
                .toList();
        return new QualityReport(
                assetType.toUpperCase(Locale.ROOT), assetId, fingerprint(root), score, grade,
                blockers, warnings, List.copyOf(findings), suggestions, Instant.now());
    }

    private void inspect(
            JsonNode node,
            String path,
            int depth,
            List<QualityFinding> findings,
            Map<String, String> ids) {
        if (depth > 14) {
            findings.add(warning("CONFIG_NESTING_TOO_DEEP", path,
                    "配置嵌套超过 14 层", "拆分为可复用子配置或蓝图", 6));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String childPath = path + "/" + entry.getKey();
                JsonNode value = entry.getValue();
                if (SECRET_KEY.matcher(entry.getKey()).matches()
                        && value.isValueNode() && !value.asText().isBlank()) {
                    findings.add(blocker("PLAINTEXT_SECRET", childPath,
                            "配置疑似包含明文密钥", "改用平台密钥引用，不要在配置快照中保存凭据", 25));
                }
                if ("id".equalsIgnoreCase(entry.getKey()) && value.isTextual()) {
                    String previous = ids.putIfAbsent(value.asText(), childPath);
                    if (previous != null) {
                        findings.add(blocker("DUPLICATE_CONFIG_ID", childPath,
                                "配置 ID 与 " + previous + " 重复", "为组件或节点生成稳定唯一 ID", 18));
                    }
                }
                inspect(value, childPath, depth + 1, findings, ids);
            });
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                inspect(node.get(index), path + "/" + index, depth + 1, findings, ids);
            }
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (UNSAFE_SCRIPT.matcher(value).matches()) {
                findings.add(blocker("UNSAFE_EXECUTABLE_CONTENT", path,
                        "配置包含不允许的脚本或反射调用", "改用受控表达式、扩展能力或服务任务白名单", 25));
            }
            if (value.startsWith("http://")) {
                findings.add(warning("INSECURE_HTTP_ENDPOINT", path,
                        "外部地址使用明文 HTTP", "生产连接器应使用 HTTPS 并配置证书校验", 8));
            }
            if (value.contains("${{") && value.contains("}}")) {
                findings.add(warning("UNRESOLVED_BLUEPRINT_PARAMETER", path,
                        "仍存在未实例化的蓝图参数", "实例化蓝图并验证所有必填参数", 8));
            }
        }
    }

    private void inspectDomainRules(JsonNode root, List<QualityFinding> findings) {
        JsonNode dataScope = root.at("/dataScope/type");
        if (dataScope.isTextual() && "UNRESTRICTED".equalsIgnoreCase(dataScope.asText())) {
            findings.add(blocker("UNRESTRICTED_DATA_SCOPE", "/dataScope/type",
                    "列表数据范围为不受限", "改为显式范围并配置无规则时拒绝", 22));
        }
        JsonNode operations = root.path("operations");
        if (operations.isObject()) {
            operations.fields().forEachRemaining(entry -> {
                JsonNode rule = entry.getValue();
                if (rule.path("enabled").asBoolean(false)
                        && rule.path("permissionCode").asText().isBlank()) {
                    findings.add(warning("OPERATION_PERMISSION_MISSING", "/operations/" + entry.getKey(),
                            "已启用操作未配置权限码", "为敏感操作声明独立权限码", 7));
                }
                if (rule.path("reasonTemplateRequired").asBoolean(false)
                        && rule.path("reasonTemplates").isEmpty()) {
                    findings.add(blocker("REASON_TEMPLATE_EMPTY", "/operations/" + entry.getKey(),
                            "操作要求理由模板但模板为空", "至少配置一个可审计理由模板", 16));
                }
            });
        }
        JsonNode emptyAssignee = root.path("emptyAssigneeStrategy");
        if ("RETRY".equalsIgnoreCase(emptyAssignee.path("policy").asText())
                && emptyAssignee.path("responsibilityOwner").asText().isBlank()) {
            findings.add(warning("EMPTY_ASSIGNEE_OWNER_MISSING", "/emptyAssigneeStrategy",
                    "重试型空办理人策略未指定责任人", "配置责任人并接收超时事件", 7));
        }
    }

    private QualityFinding blocker(String code, String path, String message, String recommendation, int deduction) {
        return new QualityFinding("BLOCKER", code, path, message, recommendation, deduction);
    }

    private QualityFinding warning(String code, String path, String message, String recommendation, int deduction) {
        return new QualityFinding("WARNING", code, path, message, recommendation, deduction);
    }

    private QualityFinding info(String code, String path, String message, String recommendation, int deduction) {
        return new QualityFinding("INFO", code, path, message, recommendation, deduction);
    }

    private String fingerprint(JsonNode node) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(node.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成配置质量指纹", exception);
        }
    }
}
