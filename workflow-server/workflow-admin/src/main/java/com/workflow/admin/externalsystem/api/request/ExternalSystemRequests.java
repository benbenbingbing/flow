package com.workflow.admin.externalsystem.api.request;

import java.util.List;

/**
 * 外部系统及其参数集合的写接口请求契约。
 */
public final class ExternalSystemRequests {

    /**
     * 初始化外部系统{@code requests}，保存构造参数供后续方法使用。
     */
    private ExternalSystemRequests() {
    }

    /**
     * 创建外部系统请求；系统编码创建后不可修改或复用。
     *
     * @param systemCode 系统编码，后续用于处理创建外部系统时定位或关联目标
     * @param systemName 系统名称，后续用于处理创建外部系统时匹配或展示
     * @param status 状态标识，决定后续创建外部系统采用的处理分支
     * @param address 地址，保存在对象中供后续校验、查询或展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     */
    public record CreateExternalSystem(
            String systemCode,
            String systemName,
            String status,
            String address,
            String description,
            List<ParameterInput> parameters) {
    }

    /**
     * 更新外部系统请求；契约刻意不包含 systemCode，防止修改稳定业务标识。
     * expectedVersion 必须来自最近一次列表或详情响应。
     *
     * @param systemName 系统名称，后续用于处理更新外部系统时匹配或展示
     * @param status 状态标识，决定后续更新外部系统采用的处理分支
     * @param address 地址，保存在对象中供后续校验、查询或展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     */
    public record UpdateExternalSystem(
            String systemName,
            String status,
            String address,
            String description,
            List<ParameterInput> parameters,
            Long expectedVersion) {
    }

    /**
     * 启用或禁用外部系统请求，expectedVersion 用于检测并发修改。
     *
     * @param status 状态标识，决定后续变更外部系统状态采用的处理分支
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     */
    public record ChangeExternalSystemStatus(
            String status,
            Long expectedVersion) {
    }

    /**
     * 删除外部系统请求；期望版本用于阻止旧页面删除已被他人修改的数据。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     */
    public record DeleteExternalSystem(Long expectedVersion) {
    }

    /**
     * 参数输入。id 仅用于兼容详情回填；聚合保存会重建参数行并返回新 id。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param nameZh 名称{@code zh}，保存在对象中供后续校验、查询或展示
     * @param nameEn 名称{@code en}，保存在对象中供后续校验、查询或展示
     * @param value 待处理参数输入的原始输入，结果供调用方继续使用
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     */
    public record ParameterInput(
            String id,
            String nameZh,
            String nameEn,
            String value,
            Integer sortOrder) {
    }
}
