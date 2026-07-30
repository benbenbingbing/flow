package com.workflow.entity.version.application.model;

import lombok.Data;

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

    private String id;
    private String entityId;
    private String entityCode;
    private String entityName;
    private Boolean enabled = false;
    private Integer revision;
    private String status;
    private String activeReleaseId;
    private Integer activeReleaseVersion;
    private LocalDateTime updateTime;
    private List<Scenario> scenarios = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();
    private List<TargetBinding> targetBindings = new ArrayList<>();

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
