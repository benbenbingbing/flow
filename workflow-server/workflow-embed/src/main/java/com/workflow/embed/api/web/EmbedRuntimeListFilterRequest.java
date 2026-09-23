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

    /**
     * 读取字段；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的字段文本，供调用方比较或展示
     */
    public String getField() {
        return field;
    }

    /**
     * 设置字段；后续读取或执行将使用更新后的状态。
     *
     * @param field 字段，供本方法设置字段时使用
     */
    public void setField(String field) {
        this.field = field;
    }

    /**
     * 读取值；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式运行时列表过滤请求结果，供调用方继续处理
     */
    public Object getValue() {
        return value;
    }

    /**
     * 设置值；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置值的原始输入，结果供调用方继续使用
     */
    public void setValue(Object value) {
        this.value = value;
        this.valuePresent = true;
    }

    /**
     * 读取值集合；查询结果供调用方展示或继续处理。
     *
     * @return 嵌入式运行时列表过滤请求集合，供调用方遍历或展示
     */
    public List<Object> getValues() {
        return values;
    }

    /**
     * 设置值集合；后续读取或执行将使用更新后的状态。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
    public void setValues(List<Object> values) {
        this.values = values == null ? null : new ArrayList<>(values);
        this.valuesPresent = true;
    }

    /**
     * 读取范围；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的范围结果，供调用方继续处理
     */
    public Range getRange() {
        return range;
    }

    /**
     * 设置范围；后续读取或执行将使用更新后的状态。
     *
     * @param range 范围，供本方法设置范围时使用
     */
    public void setRange(Range range) {
        this.range = range;
        this.rangePresent = true;
    }

    /**
     * Captures forbidden fields, most importantly a browser-supplied operator.
     *
     * @param name 名称，后续用于处理{@code unexpected}时匹配或展示
     * @param suppliedValue {@code supplied}值，作为 {@code unexpected.put} 的输入影响后续处理
     */
    @JsonAnySetter
    public void unexpected(String name, Object suppliedValue) {
        unexpected.put(name, suppliedValue);
    }

    /**
     * Exactly one value shape is allowed; the Facade matches it to the published operator.
     *
     * @return {@code shape}允许条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "Runtime list filter shape is invalid")
    public boolean isShapeAllowed() {
        int shapes = (valuePresent ? 1 : 0)
                + (valuesPresent ? 1 : 0)
                + (rangePresent ? 1 : 0);
        return unexpected.isEmpty() && shapes == 1;
    }

    /**
     * 判断是否具有值；判断结果决定调用方的后续分支。
     *
     * @return 值条件成立时为 true，否则为 false
     */
    @JsonIgnore
    public boolean hasValue() {
        return valuePresent;
    }

    /**
     * 判断是否具有值集合；判断结果决定调用方的后续分支。
     *
     * @return 值集合条件成立时为 true，否则为 false
     */
    @JsonIgnore
    public boolean hasValues() {
        return valuesPresent;
    }

    /**
     * 判断是否具有范围；判断结果决定调用方的后续分支。
     *
     * @return 范围条件成立时为 true，否则为 false
     */
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

        /**
         * 读取启动；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的范围结果，供调用方继续处理
         */
        public Object getStart() {
            return start;
        }

        /**
         * 设置启动；后续读取或执行将使用更新后的状态。
         *
         * @param start 启动，供本方法设置启动时使用
         */
        public void setStart(Object start) {
            this.start = start;
            this.startPresent = true;
        }

        /**
         * 读取结束；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的范围结果，供调用方继续处理
         */
        public Object getEnd() {
            return end;
        }

        /**
         * 设置结束；后续读取或执行将使用更新后的状态。
         *
         * @param end 结束，供本方法设置结束时使用
         */
        public void setEnd(Object end) {
            this.end = end;
            this.endPresent = true;
        }

        /**
         * 处理{@code unexpected}，并将结果传给后续步骤。
         *
         * @param name 名称，后续用于处理{@code unexpected}时匹配或展示
         * @param suppliedValue {@code supplied}值，作为 {@code unexpected.put} 的输入影响后续处理
         */
        @JsonAnySetter
        public void unexpected(String name, Object suppliedValue) {
            unexpected.put(name, suppliedValue);
        }

        /**
         * 判断是否{@code shape}允许；判断结果决定调用方的后续分支。
         *
         * @return {@code shape}允许条件成立时为 true，否则为 false
         */
        @AssertTrue(message = "Runtime list filter range is invalid")
        public boolean isShapeAllowed() {
            return unexpected.isEmpty() && startPresent && endPresent;
        }
    }
}
