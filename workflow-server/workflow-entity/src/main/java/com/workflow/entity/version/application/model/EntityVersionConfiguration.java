package com.workflow.entity.version.application.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体数据版本配置文档。
 *
 * <p>同一结构同时用于草稿编辑和不可变发布快照，运行时只读取发布快照。</p>
 */
@Data
public class EntityVersionConfiguration {

    /** V1 为旧场景/整包快照，V2 为触发器、范围和差异策略。 */
    private Integer schemaVersion = 2;
    private String id;
    private String entityId;
    private String entityCode;
    private String entityName;
    private Boolean enabled = false;
    private Integer revision;
    private String status;
    private String migrationState = "NATIVE";
    private String activeReleaseId;
    private Integer activeReleaseVersion;
    private LocalDateTime updateTime;
    private List<Scenario> scenarios = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();
    private List<TargetBinding> targetBindings = new ArrayList<>();
    private List<CaptureTrigger> triggers = new ArrayList<>();
    private SnapshotScope snapshotScope = new SnapshotScope();
    private DiffPolicy diffPolicy = new DiffPolicy();
    /** 仅用于管理端选择，不进入运行时匹配。 */
    private List<RelationOption> relationOptions = new ArrayList<>();
    /** 仅用于管理端选择，不进入运行时匹配。 */
    private List<FieldPresentation> fieldOptions = new ArrayList<>();

    @Data
    public static class CaptureTrigger {

        private String triggerCode;
        private String triggerName;
        private String triggerType = "ROOT_MUTATION";
        private String relationCode;
        private List<String> sourceTypes = new ArrayList<>();
        private List<String> operationTypes = new ArrayList<>();
        private List<String> businessIntents = new ArrayList<>();
        private Map<String, Object> condition = new LinkedHashMap<>();
        private String versionTitleTemplate;
        private Integer priority = 0;
        private Boolean enabled = true;
    }

    @Data
    public static class SnapshotScope {

        private ScopeNode root = new ScopeNode();
        private List<RelationScope> relations = new ArrayList<>();
        private ScopeLimits limits = new ScopeLimits();
        /** 发布时根据已冻结选择器与字段结构计算。 */
        private String scopeHash;
    }

    @Data
    public static class ScopeNode {

        private String nodeCode = "ROOT";
        private String entityCode;
        private String entityName;
        private String entityReleaseId;
        private Integer entityReleaseVersion;
        private String fieldMode = "ALL_PUBLISHED";
        private List<String> fieldCodes = new ArrayList<>();
        /** 发布时冻结，捕获和历史展示均不得再读取当前定义。 */
        private List<FieldPresentation> fields = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RelationScope extends ScopeNode {

        public RelationScope() {
            setNodeCode(null);
        }

        private String relationCode;
        private String relationName;
        private String childEntityCode;
        private String childEntityName;
        private String dataKey;
        private String childRefFieldCode;
        private String relationType;
        private FixedFilter filter = new FixedFilter();
        private Integer maxRows = 500;
        private Boolean enabled = true;
    }

    @Data
    public static class FixedFilter {

        private String logic = "ALL";
        private List<FilterCondition> conditions = new ArrayList<>();
    }

    @Data
    public static class FilterCondition {

        private String fieldCode;
        private String operator = "EQ";
        private Object value;
    }

    @Data
    public static class ScopeLimits {

        private Integer maxRowsPerRelation = 500;
        private Integer maxRowsPerVersion = 2000;
        private Long maxBytesPerVersion = 5L * 1024L * 1024L;
        private String overflowPolicy = "FAIL";
    }

    @Data
    public static class DiffPolicy {

        private Boolean changedOnlyDefault = true;
        private Boolean trackOrder = false;
        private List<String> ignoredFieldCodes = new ArrayList<>();
    }

    @Data
    public static class FieldPresentation {

        private String fieldCode;
        private String fieldName;
        private String fieldLabel;
        private String fieldType;
        private String sectionCode = "BUSINESS";
        private String sectionName = "业务字段";
        private Integer sortOrder = 0;
        private Integer span = 12;
        private String renderHint;
        private String dictType;
        private Map<String, String> optionLabels = new LinkedHashMap<>();
    }

    @Data
    public static class RelationOption {

        private String relationCode;
        private String relationName;
        private String childEntityCode;
        private String childEntityName;
        private String relationType;
        private List<FieldPresentation> fields = new ArrayList<>();
    }

    @Data
    public static class Scenario {

        private String id;
        private String scenarioCode;
        private String scenarioName;
        private List<String> sourceTypes = new ArrayList<>();
        private List<String> operationTypes = new ArrayList<>();
        private List<String> businessIntents = new ArrayList<>();
        private Map<String, Object> condition = new LinkedHashMap<>();
        private Integer priority = 0;
        private String versionTitleTemplate;
        private Boolean enabled = true;
    }

    @Data
    public static class Step {

        private String id;
        private String scenarioCode;
        private String phase = "BEFORE_WRITE";
        private String stepType;
        private String stepName;
        private String providerCode;
        private Map<String, Object> config = new LinkedHashMap<>();
        private Integer sortOrder = 0;
        private Boolean enabled = true;
    }

    @Data
    public static class TargetBinding {

        private String id;
        private String bindingCode;
        private String bindingName;
        private String sourceEntityCode;
        private String targetEntityCode;
        private String resolverType;
        private String resolverCode;
        private Map<String, Object> resolverConfig =
                new LinkedHashMap<>();
        private Map<String, Object> fieldMapping =
                new LinkedHashMap<>();
        private String applyStrategy = "MERGE";
        private Boolean enabled = true;
    }
}
