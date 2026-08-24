package com.workflow.process.configintelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.configintelligence.application.BlueprintTemplateEngine.RenderResult;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.AssetRef;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.DependencyEdge;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.ImpactReport;
import com.workflow.process.configintelligence.application.ConfigurationIntelligenceModels.QualityReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置智能中心核心规则测试。
 *
 * <p>这些测试直接调用生产规则类，验证蓝图参数安全、依赖图遍历和质量评分的关键契约，
 * 避免通过 Controller 或数据库 Mock 掩盖规则实现偏差。</p>
 */
class ConfigurationIntelligenceRulesTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void blueprintRenderShouldPreserveTypesAndApplyDefaults() {
        BlueprintTemplateEngine engine = new BlueprintTemplateEngine(JSON);
        Map<String, Object> schema = Map.of("parameters", Map.of(
                "owner", Map.of("type", "string", "required", true, "pattern", "^[a-z]+$"),
                "limit", Map.of("type", "integer", "default", 20),
                "enabled", Map.of("type", "boolean", "default", true)
        ));
        Map<String, Object> bundle = Map.of(
                "owner", "${{owner}}",
                "limit", "${{limit}}",
                "enabled", "${{enabled}}",
                "label", "owner-${{owner}}"
        );

        RenderResult result = engine.render(schema, bundle, Map.of("owner", "alice", "limit", 8));

        assertEquals("alice", result.renderedBundle().get("owner"));
        assertEquals(8, result.renderedBundle().get("limit"));
        assertEquals(true, result.renderedBundle().get("enabled"));
        assertEquals("owner-alice", result.renderedBundle().get("label"));
        assertTrue(result.renderedBundle().get("limit") instanceof Number,
                "精确占位符必须保留整数类型，而不是渲染为字符串");
        assertTrue(result.renderedBundle().get("enabled") instanceof Boolean,
                "精确占位符必须保留布尔类型，而不是渲染为字符串");
    }

    @Test
    void blueprintRenderShouldRejectUnknownAndEmbeddedComplexParameters() {
        BlueprintTemplateEngine engine = new BlueprintTemplateEngine(JSON);
        Map<String, Object> simpleSchema = Map.of("parameters", Map.of(
                "owner", Map.of("type", "string", "required", true)
        ));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> engine.render(simpleSchema, Map.of("owner", "${{owner}}"),
                        Map.of("owner", "alice", "script", "ignored")));
        assertTrue(unknown.getMessage().contains("未声明"));

        Map<String, Object> complexSchema = Map.of("parameters", Map.of(
                "owners", Map.of("type", "array", "required", true)
        ));
        IllegalArgumentException embedded = assertThrows(IllegalArgumentException.class,
                () -> engine.render(complexSchema, Map.of("label", "owners=${{owners}}"),
                        Map.of("owners", List.of("alice", "bob"))));
        assertTrue(embedded.getMessage().contains("完整字段值"));
    }

    @Test
    void dependencyAnalysisShouldExposeTransitiveImpactAndCycles() {
        AssetRef entity = new AssetRef("ENTITY", "customer");
        AssetRef form = new AssetRef("FORM", "customer-edit");
        AssetRef process = new AssetRef("PROCESS", "customer-approval");
        List<DependencyEdge> edges = List.of(
                new DependencyEdge("edge-1", entity, form, "REFERENCES", true, Map.of()),
                new DependencyEdge("edge-2", form, process, "USES", true, Map.of()),
                new DependencyEdge("edge-3", process, entity, "CALLS", false, Map.of())
        );

        ImpactReport report = new DependencyImpactAnalyzer().analyze(entity, edges, "DOWNSTREAM", 8);
        Set<String> affectedIds = report.affectedNodes().stream()
                .map(node -> node.asset().id())
                .collect(Collectors.toSet());

        assertEquals(Set.of("customer-edit", "customer-approval"), affectedIds);
        assertEquals(3, report.affectedEdges().size());
        assertFalse(report.cycles().isEmpty(), "循环依赖必须在影响报告中显式暴露");
        assertTrue(report.riskScore() > 0);
        assertFalse(report.recommendations().isEmpty());
        assertFalse(report.truncated());
    }

    @Test
    void qualityAnalysisShouldExplainAndScoreUnsafeConfiguration() {
        ConfigurationQualityAnalyzer analyzer = new ConfigurationQualityAnalyzer(JSON);
        Map<String, Object> safe = Map.of(
                "name", "客户审批",
                "description", "客户资料变更审批配置",
                "dataScope", Map.of("type", "DEPARTMENT"),
                "operations", Map.of(
                        "approve", Map.of("enabled", true, "permissionCode", "customer:approve")
                )
        );
        Map<String, Object> unsafe = Map.of(
                "name", "x",
                "password", "plain-secret-123",
                "script", "Runtime.getRuntime().exec('id')",
                "dataScope", Map.of("type", "UNRESTRICTED"),
                "operations", Map.of(
                        "approve", Map.of("enabled", true),
                        "reject", Map.of("enabled", true, "reasonTemplateRequired", true,
                                "reasonTemplates", List.of())
                )
        );

        QualityReport safeReport = analyzer.analyze("FORM", "safe-form", safe);
        QualityReport unsafeReport = analyzer.analyze("FORM", "unsafe-form", unsafe);
        Set<String> findingCodes = unsafeReport.findings().stream()
                .map(finding -> finding.code())
                .collect(Collectors.toSet());

        assertTrue(safeReport.score() > unsafeReport.score());
        assertTrue(unsafeReport.blockerCount() >= 3);
        assertTrue(findingCodes.contains("PLAINTEXT_SECRET"));
        assertTrue(findingCodes.contains("UNSAFE_EXECUTABLE_CONTENT"));
        assertTrue(findingCodes.contains("UNRESTRICTED_DATA_SCOPE"));
        assertTrue(findingCodes.contains("OPERATION_PERMISSION_MISSING"));
        assertTrue(findingCodes.contains("REASON_TEMPLATE_EMPTY"));
        assertFalse(unsafeReport.smartSuggestions().isEmpty());
        assertEquals(unsafeReport.fingerprint(), analyzer.analyze("FORM", "unsafe-form", unsafe).fingerprint(),
                "相同配置必须生成稳定指纹，便于质量趋势去重");
    }
}
