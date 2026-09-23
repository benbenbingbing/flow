package com.workflow.entity.version.application.model;

import java.util.List;

/**
 * 历史业务版本恢复的只读预演结果。
 *
 * <p>该模型只描述差异和阻断项，不是可执行命令。当前服务端没有恢复写入口，
 * 调用方不得把 {@link Action} 直接转发给实体变更执行链。</p>
 *
 * @param schemaVersion 结构版本，保存在对象中供后续校验、查询或展示
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param targetVersionNo 目标版本号，保存在对象中供后续校验、查询或展示
 * @param executable {@code executable}，保存在对象中供后续校验、查询或展示
 * @param summary 摘要，保存在对象中供后续校验、查询或展示
 * @param actions 动作集合，保存在对象中供后续校验、查询或展示
 * @param blockers 阻断项，保存在对象中供后续校验、查询或展示
 */
public record EntityVersionRestorePlan(
        int schemaVersion,
        String entityCode,
        String recordId,
        Integer targetVersionNo,
        boolean executable,
        Summary summary,
        List<Action> actions,
        List<Blocker> blockers) {

    /**
     * 初始化实体版本恢复方案，保存构造参数供后续方法使用。
     *
     * @param schemaVersion 结构版本，保存在对象中供后续校验、查询或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param targetVersionNo 目标版本号，保存在对象中供后续校验、查询或展示
     * @param executable {@code executable}，保存在对象中供后续校验、查询或展示
     * @param summary 摘要，保存在对象中供后续校验、查询或展示
     * @param actions 动作集合，保存在对象中供后续校验、查询或展示
     * @param blockers 阻断项，保存在对象中供后续校验、查询或展示
     */
    public EntityVersionRestorePlan {
        actions = actions == null ? List.of() : List.copyOf(actions);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    /**
     * 一项只读恢复意图；operation 仅允许 CREATE/UPDATE/DELETE/LINK/UNLINK。
     *
     * @param operation 操作标识，决定后续动作采用的处理分支
     * @param nodeCode 节点编码，后续用于处理动作时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param parentRecordId 父级记录ID，后续用于处理动作时定位或关联目标
     * @param changedFieldCodes 已变更字段编码集合，保存在对象中供后续校验、查询或展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     */
    public record Action(
            String operation,
            String nodeCode,
            String entityCode,
            String recordId,
            String parentRecordId,
            List<String> changedFieldCodes,
            String description) {

        /**
         * 初始化动作，保存构造参数供后续方法使用。
         *
         * @param operation 操作标识，决定后续动作采用的处理分支
         * @param nodeCode 节点编码，后续用于初始化动作时定位或关联目标
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         * @param parentRecordId 父级记录ID，后续用于初始化动作时定位或关联目标
         * @param changedFieldCodes 已变更字段编码集合，保存在对象中供后续校验、查询或展示
         * @param description 描述，保存在对象中供后续校验、查询或展示
         */
        public Action {
            changedFieldCodes = changedFieldCodes == null
                    ? List.of() : List.copyOf(changedFieldCodes);
        }
    }

    /**
     * 阻断恢复执行的安全、权限、流程或版本问题。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param nodeCode 节点编码，后续用于处理{@code blocker}时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public record Blocker(
            String code,
            String nodeCode,
            String entityCode,
            String recordId,
            String message) {
    }

    /**
     * 封装摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param createCount 创建数量，保存在对象中供后续校验、查询或展示
     * @param updateCount 更新数量，保存在对象中供后续校验、查询或展示
     * @param deleteCount 删除数量，保存在对象中供后续校验、查询或展示
     * @param linkCount 链接数量，保存在对象中供后续校验、查询或展示
     * @param unlinkCount {@code unlink}数量，保存在对象中供后续校验、查询或展示
     * @param blockerCount {@code blocker}数量，保存在对象中供后续校验、查询或展示
     */
    public record Summary(
            int createCount,
            int updateCount,
            int deleteCount,
            int linkCount,
            int unlinkCount,
            int blockerCount) {
    }
}
