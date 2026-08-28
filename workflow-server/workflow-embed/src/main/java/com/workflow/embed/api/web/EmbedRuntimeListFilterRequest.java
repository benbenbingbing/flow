package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览器可提交的单个 Embed 列表过滤项。
 *
 * <p>客户端只声明字段和值形状，不能声明操作符或 Entity 内部的
 * {@code _op/_start/_end} 条件键。实际操作符始终来自不可变列表发布版本。</p>
 */
public class EmbedRuntimeListFilterRequest {

    @NotBlank
    @Size(max = 100)
    private String field;

    private Object value;

    private List<Object> values;

    @Valid
    private Range range;

    private boolean valuePresent;
    private boolean valuesPresent;
    private boolean rangePresent;
    private final Map<String, Object> unexpected = new LinkedHashMap<>();

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
        this.valuePresent = true;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values == null ? null : new ArrayList<>(values);
        this.valuesPresent = true;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
        this.rangePresent = true;
    }

    /** Captures forbidden fields, most importantly a browser-supplied operator. */
    @JsonAnySetter
    public void unexpected(String name, Object suppliedValue) {
        unexpected.put(name, suppliedValue);
    }

    /** Exactly one value shape is allowed; the Facade matches it to the published operator. */
    @AssertTrue(message = "Runtime list filter shape is invalid")
    public boolean isShapeAllowed() {
        int shapes = (valuePresent ? 1 : 0)
                + (valuesPresent ? 1 : 0)
                + (rangePresent ? 1 : 0);
        return unexpected.isEmpty() && shapes == 1;
    }

    @JsonIgnore
    public boolean hasValue() {
        return valuePresent;
    }

    @JsonIgnore
    public boolean hasValues() {
        return valuesPresent;
    }

    @JsonIgnore
    public boolean hasRange() {
        return rangePresent;
    }

    /** Strongly shaped inclusive range used only when the published operator is BETWEEN. */
    public static class Range {

        private Object start;
        private Object end;
        private boolean startPresent;
        private boolean endPresent;
        private final Map<String, Object> unexpected = new LinkedHashMap<>();

        public Object getStart() {
            return start;
        }

        public void setStart(Object start) {
            this.start = start;
            this.startPresent = true;
        }

        public Object getEnd() {
            return end;
        }

        public void setEnd(Object end) {
            this.end = end;
            this.endPresent = true;
        }

        @JsonAnySetter
        public void unexpected(String name, Object suppliedValue) {
            unexpected.put(name, suppliedValue);
        }

        @AssertTrue(message = "Runtime list filter range is invalid")
        public boolean isShapeAllowed() {
            return unexpected.isEmpty() && startPresent && endPresent;
        }
    }
}
