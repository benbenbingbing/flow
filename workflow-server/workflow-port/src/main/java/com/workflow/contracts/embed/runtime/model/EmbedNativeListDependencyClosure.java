package com.workflow.contracts.embed.runtime.model;

import java.util.List;

/**
 * 一次 Embed Launch 固定的原生列表依赖闭包。
 *
 * <p>闭包从根 List Release 的 open-list 边递归生成，并随 Runtime Snapshot 和
 * {@code elr1} 令牌一起固定。运行时只能在闭包声明的精确坐标间导航，不能重新解析
 * ACTIVE，也不能把浏览器提交的坐标当作授权依据。</p>
 *
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
 */
public record EmbedNativeListDependencyClosure(
        int version,
        List<ListNode> nodes) {

    public static final int CURRENT_VERSION = 1;

    /**
     * 初始化嵌入式原生列表依赖闭包，保存构造参数供后续方法使用。
     *
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
     */
    public EmbedNativeListDependencyClosure {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /**
     * 闭包中的一个精确列表版本、其新增表单结论以及允许导航的目标边。
     *
     * @param list 列表，保存在对象中供后续校验、查询或展示
     * @param defaultFormResolved 默认表单已解析，保存在对象中供后续校验、查询或展示
     * @param defaultForm 默认表单，保存在对象中供后续校验、查询或展示
     * @param targets 目标集合，保存在对象中供后续校验、查询或展示
     */
    public record ListNode(
            ListCoordinate list,
            boolean defaultFormResolved,
            FormCoordinate defaultForm,
            List<ListCoordinate> targets) {

        /**
         * 初始化列表节点，保存构造参数供后续方法使用。
         *
         * @param list 列表，保存在对象中供后续校验、查询或展示
         * @param defaultFormResolved 默认表单已解析，保存在对象中供后续校验、查询或展示
         * @param defaultForm 默认表单，保存在对象中供后续校验、查询或展示
         * @param targets 目标集合，保存在对象中供后续校验、查询或展示
         */
        public ListNode {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    /**
     * 列表稳定标识和精确发布坐标；listConfigId 用于阻止同 key 资源替换。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listConfigId 列表配置ID，后续用于处理列表坐标时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于处理列表坐标时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     */
    public record ListCoordinate(
            String entityCode,
            String listKey,
            String listConfigId,
            String listReleaseId,
            int listReleaseVersion) {
    }

    /**
     * Flow 原生 new-data 规则在物化时解析出的可选默认表单精确坐标。
     *
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理表单坐标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     */
    public record FormCoordinate(
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }
}
