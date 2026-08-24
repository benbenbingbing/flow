package com.workflow.migration.reference.application;

import java.util.List;

/** 配置正反向引用、路径追踪与变更影响分析模型。 */
public final class ConfigurationReferenceModels {

    private ConfigurationReferenceModels() {
    }

    public record ReferenceEdge(
            String id,
            String sourceType,
            String sourceKey,
            Integer sourceVersion,
            String targetType,
            String targetKey,
            boolean required,
            String strength,
            String location,
            String parseStatus) {

        public String sourceNodeKey() {
            return sourceType + ":" + sourceKey;
        }

        public String targetNodeKey() {
            return targetType + ":" + targetKey;
        }
    }

    public record ReferenceNode(
            String type,
            String key,
            int depth,
            String reachedBy,
            Integer sourceVersion) {

        public String nodeKey() {
            return type + ":" + key;
        }
    }

    public record ImpactReport(
            String focusType,
            String focusKey,
            String direction,
            List<ReferenceNode> nodes,
            List<ReferenceEdge> edges,
            List<List<String>> cycles,
            List<ReferenceEdge> unknownReferences,
            boolean hardDependencyBlocked,
            boolean truncated) {
    }
}
