package com.workflow.process.configintelligence.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 配置蓝图、影响分析、质量评分和便携包模型。 */
public final class ConfigurationIntelligenceModels {

    private ConfigurationIntelligenceModels() {
    }

    public record BlueprintSaveRequest(
            String id,
            String blueprintKey,
            String name,
            String category,
            String description,
            Integer version,
            Map<String, Object> parameterSchema,
            Map<String, Object> bundle) {
    }

    public record Blueprint(
            String id,
            String blueprintKey,
            String name,
            String category,
            String description,
            int version,
            String status,
            Map<String, Object> parameterSchema,
            Map<String, Object> bundle,
            String checksum,
            long downloadCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record InstantiateRequest(Map<String, Object> parameters) {
    }

    public record BlueprintInstance(
            String blueprintId,
            String blueprintKey,
            int blueprintVersion,
            Map<String, Object> effectiveParameters,
            Map<String, Object> renderedBundle,
            String checksum) {
    }

    public record DependencySaveRequest(
            String targetType,
            String targetId,
            String relationType,
            Boolean required,
            Map<String, Object> metadata) {
    }

    public record DependencyEdge(
            String id,
            AssetRef source,
            AssetRef target,
            String relationType,
            boolean required,
            Map<String, Object> metadata) {
    }

    public record AssetRef(String type, String id) {
        public String key() {
            return type + ":" + id;
        }
    }

    public record ImpactRequest(
            String assetType,
            String assetId,
            String direction,
            Integer maxDepth) {
    }

    public record ImpactNode(AssetRef asset, int depth, String reachedBy) {
    }

    public record ImpactReport(
            AssetRef focus,
            String direction,
            List<ImpactNode> affectedNodes,
            List<DependencyEdge> affectedEdges,
            List<List<String>> cycles,
            int riskScore,
            String riskLevel,
            List<String> recommendations,
            boolean truncated) {
    }

    public record QualityAnalyzeRequest(
            String assetType,
            String assetId,
            Map<String, Object> configuration) {
    }

    public record QualityFinding(
            String severity,
            String code,
            String path,
            String message,
            String recommendation,
            int deduction) {
    }

    public record QualityReport(
            String assetType,
            String assetId,
            String fingerprint,
            int score,
            String grade,
            int blockerCount,
            int warningCount,
            List<QualityFinding> findings,
            List<String> smartSuggestions,
            Instant generatedAt) {
    }

    public record PortablePackage(
            Map<String, Object> manifest,
            Map<String, Object> payload,
            String packageChecksum) {
    }

    public record PackageVerification(boolean valid, String expectedChecksum, String actualChecksum) {
    }
}
