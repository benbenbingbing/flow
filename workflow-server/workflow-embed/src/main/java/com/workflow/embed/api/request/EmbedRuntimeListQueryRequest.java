package com.workflow.embed.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Browser-controlled subset of a list query; all target and trusted policy inputs are omitted. */
public class EmbedRuntimeListQueryRequest {

    @Min(1)
    @Max(Integer.MAX_VALUE)
    private Integer pageNum;

    @Min(1)
    @Max(200)
    private Integer pageSize;

    @Valid
    @Size(max = 32)
    private List<EmbedRuntimeListFilterRequest> filters = new ArrayList<>();

    private final Map<String, Object> unexpected = new LinkedHashMap<>();

    /**
     * 按筛选条件分页查询嵌入式运行时列表查询请求；结果供列表展示。
     *
     * @return 符合条件的嵌入式运行时列表查询请求结果，供调用方继续处理
     */
    public Integer getPageNum() {
        return pageNum;
    }

    /**
     * 设置分页数量；后续读取或执行将使用更新后的状态。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     */
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * 按筛选条件分页查询嵌入式运行时列表查询请求；结果供列表展示。
     *
     * @return 符合条件的嵌入式运行时列表查询请求结果，供调用方继续处理
     */
    public Integer getPageSize() {
        return pageSize;
    }

    /**
     * 设置分页大小；后续读取或执行将使用更新后的状态。
     *
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 读取过滤条件；查询结果供调用方展示或继续处理。
     *
     * @return 嵌入式运行时列表过滤请求集合，供调用方遍历或展示
     */
    public List<EmbedRuntimeListFilterRequest> getFilters() {
        return filters;
    }

    /**
     * 设置过滤条件；后续读取或执行将使用更新后的状态。
     *
     * @param filters 过滤条件，供本方法设置过滤条件时使用
     */
    public void setFilters(List<EmbedRuntimeListFilterRequest> filters) {
        this.filters = filters == null ? new ArrayList<>() : new ArrayList<>(filters);
    }

    /**
     * Captures forbidden fields such as releaseId, context, fixedFilters, scene and sorts.
     *
     * @param name 名称，后续用于处理{@code unexpected}时匹配或展示
     * @param value 待处理{@code unexpected}的原始输入，结果供调用方继续使用
     */
    @JsonAnySetter
    public void unexpected(String name, Object value) {
        unexpected.put(name, value);
    }

    /**
     * 判断是否{@code shape}允许；判断结果决定调用方的后续分支。
     *
     * @return {@code shape}允许条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "Runtime list query contains unsupported fields")
    public boolean isShapeAllowed() {
        return unexpected.isEmpty();
    }
}
