package com.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体版本差异对比DTO
 */
@Data
public class EntityVersionDiffDTO {

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 实体编码
     */
    private String entityCode;

    /**
     * 实体名称
     */
    private String entityName;

    /**
     * 当前版本号（已发布的最新版本）
     */
    private Integer currentVersion;

    /**
     * 即将发布的版本号
     */
    private Integer nextVersion;

    /**
     * 是否是首次发布
     */
    private Boolean isFirstPublish;

    /**
     * 新增的字段列表
     */
    private List<FieldDiff> addedFields = new ArrayList<>();

    /**
     * 修改的字段列表
     */
    private List<FieldDiff> modifiedFields = new ArrayList<>();

    /**
     * 删除的字段列表（理论上已发布字段不会删除）
     */
    private List<FieldDiff> removedFields = new ArrayList<>();

    /**
     * 无变更的字段列表
     */
    private List<FieldDiff> unchangedFields = new ArrayList<>();

    /**
     * 变更摘要描述
     */
    private String changeSummary;

    /**
     * 即将执行的DDL列表
     */
    private List<String> pendingDdls = new ArrayList<>();

    /**
     * 字段差异详情
     */
    @Data
    public static class FieldDiff {
        /**
         * 字段ID
         */
        private String fieldId;

        /**
         * 字段编码
         */
        private String fieldCode;

        /**
         * 字段名称
         */
        private String fieldName;

        /**
         * 字段类型
         */
        private String fieldType;

        /**
         * 数据库类型
         */
        private String dbType;

        /**
         * 数据库列名（下划线命名）
         */
        private String dbColumnName;

        /**
         * 是否必填
         */
        private Boolean isRequired;

        /**
         * 是否已发布
         */
        private Boolean isPublished;

        /**
         * 是否系统字段
         */
        private Boolean isSystem;

        /**
         * 变更类型：ADD-新增, MODIFY-修改, REMOVE-删除, UNCHANGED-无变化
         */
        private ChangeType changeType;

        /**
         * 变更详情描述
         */
        private String changeDescription;

        /**
         * 原值（修改前的值）
         */
        private Object oldValue;

        /**
         * 新值（修改后的值）
         */
        private Object newValue;

        public enum ChangeType {
            ADD, MODIFY, REMOVE, UNCHANGED
        }
    }
}
