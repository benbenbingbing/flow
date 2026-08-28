package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

/** 浏览器可控的引用字段候选查询子集。 */
public class EmbedRuntimeLookupQueryRequest {

    @NotBlank
    @Pattern(regexp = "CREATE|VIEW")
    private String mode;

    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")
    private String recordId;

    @Size(max = 200)
    private String keyword;

    @Size(max = 32)
    private Map<String, Object> filters = new LinkedHashMap<>();

    @Min(1)
    private int pageNum = 1;

    @Min(1)
    @Max(50)
    private int pageSize = 20;

    private final Map<String, Object> unexpected = new LinkedHashMap<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? new LinkedHashMap<>() : filters;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /** 捕获未公开 filter/provider/release/context 等攻击者输入。 */
    @JsonAnySetter
    public void unexpected(String name, Object value) {
        unexpected.put(name, value);
    }

    @AssertTrue(message = "Runtime lookup query contains unsupported fields")
    public boolean isShapeAllowed() {
        return unexpected.isEmpty() && coordinateShapeAllowed();
    }

    private boolean coordinateShapeAllowed() {
        if ("CREATE".equals(mode)) {
            return recordId == null || recordId.isBlank();
        }
        return !"VIEW".equals(mode) || recordId != null && !recordId.isBlank();
    }
}
