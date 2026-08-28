package com.workflow.embed.api.web;

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

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<EmbedRuntimeListFilterRequest> getFilters() {
        return filters;
    }

    public void setFilters(List<EmbedRuntimeListFilterRequest> filters) {
        this.filters = filters == null ? new ArrayList<>() : new ArrayList<>(filters);
    }

    /** Captures forbidden fields such as releaseId, context, fixedFilters, scene and sorts. */
    @JsonAnySetter
    public void unexpected(String name, Object value) {
        unexpected.put(name, value);
    }

    @AssertTrue(message = "Runtime list query contains unsupported fields")
    public boolean isShapeAllowed() {
        return unexpected.isEmpty();
    }
}
