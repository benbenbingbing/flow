package com.workflow.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.validation.ExcelValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可复用的读取监听器：收集表头、逐行校验、分批回调并统计数据行数。
 * 一次导入创建一个实例，不可跨文件或并发读取共享；任何校验或回调异常都立即中止读取。
 * 只有当前批次和表头留在监听器中，业务入库及其事务由调用方负责。
 *
 * @param <T> EasyExcel 解析后的数据行类型；动态列 Map 没有 DTO 字段约束
 */
public class ExcelReadListener<T> extends AnalysisEventListener<T> {
    private final int batchSize;
    private final Consumer<List<T>> consumer;
    private final Class<?>[] groups;
    private final List<Map<Integer, String>> headRows = new ArrayList<>();
    private List<T> buffer = new ArrayList<>();
    private long rowCount;

    /**
     * 使用默认批大小和 Default 校验分组创建监听器。
     *
     * @param consumer 同步批处理回调，仅收到通过字段校验的数据，空批次不会回调
     */
    public ExcelReadListener(Consumer<List<T>> consumer) {
        this(ExcelReadOptions.defaults().batchSize(), consumer);
    }

    /**
     * 配置分批处理及校验分组，批次列表彼此独立，调用方可以保留或修改。
     *
     * @param batchSize 每批最大行数，必须大于 0，最后不足一批的数据也会回调
     * @param consumer 同步业务回调，失败时已完成的业务副作用不会自动回滚
     * @param groups 本次启用的分组，不指定时使用 Default；指定自定义分组后，需要默认规则时须同时包含 Default
     */
    public ExcelReadListener(int batchSize, Consumer<List<T>> consumer, Class<?>... groups) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("每批数据行数必须大于 0");
        }
        this.batchSize = batchSize;
        this.consumer = Objects.requireNonNull(consumer, "批处理回调不能为空");
        this.groups = Objects.requireNonNull(groups, "校验分组不能为空").clone();
        for (Class<?> group : this.groups) {
            Objects.requireNonNull(group, "校验分组不能包含 null");
        }
    }

    /** 保存每一级表头的独立快照；子类可覆盖此方法检查业务要求的表头。 */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        // 空表头单元格可能为 null，使用可容纳 null 值的快照而非 Map.copyOf。
        headRows.add(Collections.unmodifiableMap(new LinkedHashMap<>(headMap)));
    }

    /** 在数据加入批次前校验，使用原始行索引定位错误，避免过滤空行后行号偏移。 */
    @Override
    public void invoke(T data, AnalysisContext context) {
        ExcelValidator.validateOrThrow(data, context.readSheetHolder().getSheetName(),
                context.readRowHolder().getRowIndex(), groups);
        buffer.add(data);
        rowCount++;
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /** 读取成功结束时交付尾批；解析失败时不会把未交付的数据当作成功批次处理。 */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    /** @return 已通过校验并加入批次的数据行数；读取异常时未必已全部执行业务回调 */
    public long getRowCount() {
        return rowCount;
    }

    /** @return 按文件顺序保存的多级表头快照，列表和 Map 均不可修改 */
    public List<Map<Integer, String>> getHeadRows() {
        return List.copyOf(headRows);
    }

    private void flush() {
        if (!buffer.isEmpty()) {
            // 更换缓冲区而不是 clear，防止后续读取改写业务方保留的批次。
            List<T> batch = buffer;
            buffer = new ArrayList<>();
            consumer.accept(batch);
        }
    }
}
