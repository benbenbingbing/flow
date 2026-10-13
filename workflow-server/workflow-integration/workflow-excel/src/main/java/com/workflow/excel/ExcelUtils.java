package com.workflow.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.read.builder.ExcelReaderBuilder;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.workflow.excel.listener.ExcelReadListener;
import com.workflow.excel.listener.ExcelWriteListener;
import com.workflow.excel.listener.ExcelRawReadListener;
import com.workflow.excel.dynamic.ExcelRawRow;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.poi.ss.util.WorkbookUtil;

/**
 * 通用 Excel 导入导出入口，不依赖 Web 或业务模块，可直接使用静态方法。
 * 读取支持 EasyExcel 自动识别的 XLS/XLSX，导出统一生成 XLSX。
 * 所有方法保留调用方传入的流，调用方负责关闭；内部 reader/writer 会正常释放。
 * 转换和文件读写异常沿用 EasyExcel 异常，便于业务模块获取错误行列信息。
 * 默认 DTO 读取执行字段约束校验，失败时抛出 ExcelValidationException；导出不自动执行字段校验。
 */
public final class ExcelUtils {
    private static final String DEFAULT_SHEET_NAME = "Sheet1";

    private ExcelUtils() {
    }

    /**
     * 按 DTO 的 EasyExcel 注解读取第一个 Sheet，跳过一行表头。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param headClass 数据行类型，需提供无参构造器和可写属性
     * @return 全部数据行；大文件应使用 {@link #readBatches} 避免全量驻留内存
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> headClass) {
        return read(inputStream, headClass, ExcelReadOptions.defaults());
    }

    /**
     * 按指定配置将单个 Sheet 的全部数据映射为 DTO，并校验字段约束，空行沿用 EasyExcel 默认规则跳过。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param headClass 数据行类型，需提供无参构造器和可写属性
     * @param options Sheet、表头行数和批大小配置
     * @return 按文件顺序排列的全部数据行，无数据时返回空列表
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> headClass, ExcelReadOptions options) {
        List<T> rows = new ArrayList<>();
        readBatches(inputStream, headClass, options, rows::addAll);
        return rows;
    }

    /**
     * 分批读取并校验 DTO，随后同步交给业务模块处理，最后不足一批的数据也会回调。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param headClass 数据行类型，需提供无参构造器和可写属性
     * @param options Sheet、表头行数和批大小配置
     * @param consumer 仅接收通过 Default 分组校验的数据，各批列表独立，可保留；空批次不会回调
     * @return 完整读取并成功处理的数据行数，不包含表头和被跳过的空行
     * @throws RuntimeException 解析或回调失败时立即中止；已执行回调的业务副作用不会自动回滚
     */
    public static <T> long readBatches(InputStream inputStream, Class<T> headClass,
            ExcelReadOptions options, Consumer<List<T>> consumer) {
        Objects.requireNonNull(options, "读取配置不能为空");
        Objects.requireNonNull(consumer, "批处理回调不能为空");
        ExcelReadListener<T> listener = new ExcelReadListener<>(options.batchSize(), consumer);
        readWithListener(inputStream, headClass, options, listener);
        return listener.getRowCount();
    }

    /**
     * 使用业务自定义监听器读取 DTO，供需要校验表头、定位原始行号或定制错误处理的模块调用。
     * 此入口的校验由监听器负责，使用 ExcelReadListener 可启用字段校验及指定分组。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param headClass 数据行类型，需提供无参构造器和可写属性
     * @param options Sheet 和表头配置；批大小由自定义监听器自行处理
     * @param listener 本次读取专用的监听器，不应跨并发导入共享有状态的实例
     * @throws IllegalArgumentException 指定的 Sheet 不存在
     */
    public static <T> void readWithListener(InputStream inputStream, Class<T> headClass,
            ExcelReadOptions options, ReadListener<T> listener) {
        Objects.requireNonNull(headClass, "数据行类型不能为空");
        readSheet(reader(inputStream, options).head(headClass)
                .registerReadListener(Objects.requireNonNull(listener, "读取监听器不能为空")), options);
    }

    /**
     * 无 DTO 读取第一个 Sheet，跳过一行表头，供字段在运行时确定的模块使用。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @return 每行以从 0 开始的列索引映射文本值；空单元格可能缺少键或值为 null
     */
    public static List<Map<Integer, String>> readRows(InputStream inputStream) {
        return readRows(inputStream, ExcelReadOptions.defaults());
    }

    /**
     * 无 DTO 读取指定 Sheet，数值等单元格由 EasyExcel 转为文本供业务模块后续转换。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param options Sheet、表头行数和批大小配置
     * @return 全部数据行；多级表头文件应明确设置对应的表头行数
     */
    public static List<Map<Integer, String>> readRows(InputStream inputStream, ExcelReadOptions options) {
        List<Map<Integer, String>> rows = new ArrayList<>();
        readRowsBatches(inputStream, options, rows::addAll);
        return rows;
    }

    /**
     * 分批读取动态列，便于业务模块按自己的字段定义转换和保存数据。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param options Sheet、表头行数和批大小配置
     * @param consumer 同步批处理回调，各批列表独立；空批次不会回调
     * @return 完整读取并成功处理的数据行数
     * @throws RuntimeException 解析或回调失败时立即中止，已执行的业务副作用不会自动回滚
     */
    public static long readRowsBatches(InputStream inputStream, ExcelReadOptions options,
            Consumer<List<Map<Integer, String>>> consumer) {
        Objects.requireNonNull(options, "读取配置不能为空");
        Objects.requireNonNull(consumer, "批处理回调不能为空");
        ExcelReadListener<Map<Integer, String>> listener = new ExcelReadListener<>(options.batchSize(), consumer);
        readRowsWithListener(inputStream, options, listener);
        return listener.getRowCount();
    }

    /**
     * 使用自定义监听器读取动态列，供需要处理原始表头或逐行校验动态字段的模块调用。
     *
     * @param inputStream 文件输入流，不由本方法关闭
     * @param options Sheet 和表头配置，批大小由监听器负责
     * @param listener 本次读取专用的监听器，接收以列索引映射的文本值
     * @throws IllegalArgumentException 指定的 Sheet 不存在
     */
    public static void readRowsWithListener(InputStream inputStream, ExcelReadOptions options,
            ReadListener<Map<Integer, String>> listener) {
        readSheet(reader(inputStream, options)
                .registerReadListener(Objects.requireNonNull(listener, "读取监听器不能为空")), options);
    }

    /**
     * 分批读取原始单元格，保留类型、数值格式、公式标记、实际行号及工作簿日期基准。
     * 此入口固定保留文本空白，不使用 options.autoTrim；清理规则由后续字段转换决定。
     * 表头单独逐行回调，中间空行交给业务判定是否跳过；不执行 DTO 注解校验。
     *
     * @param maxRows 数据行回调上限，含中间空行但不含表头；超限立即中止，已回调数据不自动回滚
     * @param headers 每个表头行的同步回调，headRowNumber 为 0 时不调用
     * @param batches 不可变的独立批次，调用方应及时消费以维持有界内存
     * @return 已读取的数据行数；文件异常、超限或回调异常时抛出异常
     */
    public static long readRawRows(InputStream inputStream, ExcelReadOptions options, long maxRows,
            Consumer<ExcelRawRow> headers, Consumer<List<ExcelRawRow>> batches) {
        Objects.requireNonNull(options, "读取配置不能为空");
        ExcelRawReadListener listener = new ExcelRawReadListener(options.batchSize(), maxRows, headers, batches);
        readSheet(reader(inputStream, options).autoTrim(false).ignoreEmptyRow(false)
                .useDefaultListener(false).registerReadListener(listener), options,
                excelReader -> listener.initialize(excelReader.analysisContext().readWorkbookHolder(), options));
        return listener.getRowCount();
    }

    /** 按配置 Sheet 名称读取，避免说明页或用户调整 Sheet 顺序后误将另一张表导入。 */
    public static long readRawRows(InputStream inputStream, String sheetName, ExcelReadOptions options, long maxRows,
            Consumer<ExcelRawRow> headers, Consumer<List<ExcelRawRow>> batches) {
        Objects.requireNonNull(sheetName, "Sheet 名称不能为空");
        Objects.requireNonNull(options, "读取配置不能为空");
        ExcelRawReadListener listener = new ExcelRawReadListener(options.batchSize(), maxRows, headers, batches);
        try (ExcelReader excelReader = reader(inputStream, options).autoTrim(false).ignoreEmptyRow(false)
                .useDefaultListener(false).registerReadListener(listener).build()) {
            var sheet = excelReader.excelExecutor().sheetList().stream()
                    .filter(item -> sheetName.equals(item.getSheetName())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("指定的 Sheet 不存在：" + sheetName));
            var resolved = new ExcelReadOptions(sheet.getSheetNo(), options.headRowNumber(), options.batchSize(), false);
            listener.initialize(excelReader.analysisContext().readWorkbookHolder(), resolved);
            excelReader.read(EasyExcel.readSheet(sheet.getSheetNo()).build());
        }
        return listener.getRowCount();
    }

    /**
     * 将 DTO 数据导出到名为 Sheet1 的 XLSX 工作表。
     *
     * @param outputStream 文件或下载响应的输出流，不由本方法关闭
     * @param headClass 表头和列映射类型，通过 EasyExcel 注解定义导出字段
     * @param rows 待导出数据，空集合用于生成只有表头的导入模板
     */
    public static <T> void write(OutputStream outputStream, Class<T> headClass,
            Collection<? extends T> rows) {
        write(outputStream, headClass, rows, DEFAULT_SHEET_NAME);
    }

    /**
     * 将 DTO 数据导出到指定名称的 XLSX 工作表，保留模型注解定义的格式。
     *
     * @param outputStream 文件或下载响应的输出流，不由本方法关闭
     * @param headClass 表头和列映射类型
     * @param rows 待导出数据，允许空集合但不允许 null
     * @param sheetName Excel 合法的工作表名称，最长 31 个字符
     * @throws IllegalArgumentException 工作表名称不符合 Excel 规则
     */
    public static <T> void write(OutputStream outputStream, Class<T> headClass,
            Collection<? extends T> rows, String sheetName) {
        write(outputStream, headClass, rows, sheetName, new ExcelWriteListener());
    }

    /**
     * 导出 DTO 并注册调用方的写监听器或样式处理器，处理器在本次写入过程中同步执行。
     *
     * @param outputStream 输出流，不由本方法关闭
     * @param headClass 表头和列映射类型
     * @param rows 待导出数据，空集合生成表头模板
     * @param sheetName Excel 合法的工作表名称
     * @param handlers 本次导出专用的处理器，例如 ExcelWriteListener 或 EasyExcel 样式处理器
     */
    public static <T> void write(OutputStream outputStream, Class<T> headClass,
            Collection<? extends T> rows, String sheetName, WriteHandler... handlers) {
        Objects.requireNonNull(rows, "导出数据不能为空");
        writeBatches(outputStream, headClass, List.of(rows), sheetName, handlers);
    }

    /**
     * 逐批追加 DTO 到同一工作表；可传入惰性 Iterable，避免先将全部查询结果加载到内存。
     *
     * @param outputStream 文件或下载响应的输出流，不由本方法关闭
     * @param headClass 表头和列映射类型
     * @param batches 按导出顺序提供各批数据，批次不可为 null；没有批次时仍输出表头
     * @param sheetName Excel 合法的工作表名称，最长 31 个字符
     * @throws RuntimeException 数据获取或写入失败时中止，输出流中可能已有部分内容，应由调用方丢弃
     */
    public static <T> void writeBatches(OutputStream outputStream, Class<T> headClass,
            Iterable<? extends Collection<? extends T>> batches, String sheetName) {
        writeBatches(outputStream, headClass, batches, sheetName, new ExcelWriteListener());
    }

    /**
     * 分批导出 DTO，在所有批次间复用同一组写处理器，便于统计整次导出的进度。
     *
     * @param outputStream 输出流，不由本方法关闭
     * @param headClass 表头和列映射类型
     * @param batches 按导出顺序提供的数据批次，可惰性获取，批次不可为 null
     * @param sheetName Excel 合法的工作表名称
     * @param handlers 本次导出专用的写处理器，不应跨并发导出共享有状态实例
     * @throws RuntimeException 数据获取或写处理器回调失败时中止，调用方应丢弃部分输出
     */
    public static <T> void writeBatches(OutputStream outputStream, Class<T> headClass,
            Iterable<? extends Collection<? extends T>> batches, String sheetName, WriteHandler... handlers) {
        Objects.requireNonNull(outputStream, "输出流不能为空");
        Objects.requireNonNull(headClass, "数据行类型不能为空");
        Objects.requireNonNull(batches, "导出批次不能为空");
        WorkbookUtil.validateSheetName(sheetName);
        try (ExcelWriter writer = writer(outputStream, handlers).head(headClass).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();
            // 先创建表头，使空查询结果仍能导出模板；复用同一 WriteSheet 避免每批重复表头。
            writer.write(List.of(), sheet);
            for (Collection<? extends T> batch : batches) {
                writer.write(Objects.requireNonNull(batch, "导出批次数据不能为空"), sheet);
            }
        }
    }

    /**
     * 使用动态表头导出 XLSX，供没有固定 DTO 的业务数据或导入模板使用。
     *
     * @param outputStream 文件或下载响应的输出流，不由本方法关闭
     * @param head 按列排列的表头，每列的列表表示从上到下的多级标题，不能为空
     * @param rows 按列排列的行数据，列顺序必须与 head 一致；空集合只输出表头
     * @param sheetName Excel 合法的工作表名称，最长 31 个字符
     * @throws IllegalArgumentException 表头为空或工作表名称不合法
     */
    public static void writeRows(OutputStream outputStream, List<List<String>> head,
            Collection<? extends List<?>> rows, String sheetName) {
        writeRows(outputStream, head, rows, sheetName, new ExcelWriteListener());
    }

    /**
     * 导出动态表头和行数据，并注册写监听器；动态字段格式校验由调用方负责。
     *
     * @param outputStream 输出流，不由本方法关闭
     * @param head 按列排列的多级标题，每列至少一个标题
     * @param rows 按列排列的行数据，顺序与 head 一致
     * @param sheetName Excel 合法的工作表名称
     * @param handlers 本次导出专用的写监听器或样式处理器
     */
    public static void writeRows(OutputStream outputStream, List<List<String>> head,
            Collection<? extends List<?>> rows, String sheetName, WriteHandler... handlers) {
        Objects.requireNonNull(outputStream, "输出流不能为空");
        Objects.requireNonNull(head, "动态表头不能为空");
        Objects.requireNonNull(rows, "导出数据不能为空");
        if (head.isEmpty() || head.stream().anyMatch(column -> column == null || column.isEmpty())) {
            throw new IllegalArgumentException("动态表头必须包含列，且每列至少有一级标题");
        }
        WorkbookUtil.validateSheetName(sheetName);
        writer(outputStream, handlers).head(head).sheet(sheetName).doWrite(rows);
    }

    private static ExcelWriterBuilder writer(OutputStream outputStream, WriteHandler... handlers) {
        Objects.requireNonNull(handlers, "写处理器列表不能为空");
        ExcelWriterBuilder builder = EasyExcel.write(outputStream)
                .excelType(ExcelTypeEnum.XLSX).autoCloseStream(false);
        for (WriteHandler handler : handlers) {
            builder.registerWriteHandler(Objects.requireNonNull(handler, "写处理器不能包含 null"));
        }
        return builder;
    }

    /**
     * 动态表头的惰性批写，复用同一 writer/Sheet，空结果仍输出表头。
     * 每批处理完再请求下一批，可直接对接数据库游标；内部不累积所有记录。
     * 输出流由调用方关闭；迭代、转换或写入失败时需丢弃已经生成的部分文件。
     */
    public static void writeRowsBatches(OutputStream outputStream, List<List<String>> head,
            Iterable<? extends Collection<? extends List<?>>> batches, String sheetName,
            WriteHandler... handlers) {
        Objects.requireNonNull(outputStream, "输出流不能为空");
        Objects.requireNonNull(head, "动态表头不能为空");
        Objects.requireNonNull(batches, "导出批次不能为空");
        if (head.isEmpty() || head.stream().anyMatch(column -> column == null || column.isEmpty())) {
            throw new IllegalArgumentException("动态表头必须包含列，且每列至少有一级标题");
        }
        WorkbookUtil.validateSheetName(sheetName);
        try (ExcelWriter writer = writer(outputStream, handlers).head(head).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();
            writer.write(List.of(), sheet);
            for (Collection<? extends List<?>> batch : batches) {
                writer.write(Objects.requireNonNull(batch, "导出批次数据不能为空"), sheet);
            }
        }
    }

    private static ExcelReaderBuilder reader(InputStream inputStream, ExcelReadOptions options) {
        Objects.requireNonNull(inputStream, "输入流不能为空");
        Objects.requireNonNull(options, "读取配置不能为空");
        // 读取器可释放自己的缓存和临时文件，但上传文件流的生命周期仍由调用方管理。
        // XLS 的 POIFS 解析即使 autoCloseStream=false 仍会关闭传入流；只屏蔽底层 close，保持统一生命周期约定。
        InputStream callerOwned = new FilterInputStream(inputStream) {
            @Override
            public void close() { }
        };
        return EasyExcel.read(callerOwned).autoCloseStream(false).headRowNumber(options.headRowNumber())
                .autoTrim(options.autoTrim());
    }

    /** 校验工作表存在后再读取，避免错误 Sheet 索引被当作一次成功的空数据导入。 */
    private static void readSheet(ExcelReaderBuilder builder, ExcelReadOptions options) {
        readSheet(builder, options, ignored -> { });
    }

    private static void readSheet(ExcelReaderBuilder builder, ExcelReadOptions options, Consumer<ExcelReader> beforeRead) {
        try (ExcelReader excelReader = builder.build()) {
            // EasyExcel 会跳过不匹配的 Sheet；显式检查使配置错误和真正的空表可以区分。
            boolean sheetExists = excelReader.excelExecutor().sheetList().stream()
                    .anyMatch(sheet -> sheet.getSheetNo() == options.sheetNo());
            if (!sheetExists) {
                throw new IllegalArgumentException("指定的 Sheet 不存在，索引：" + options.sheetNo());
            }
            beforeRead.accept(excelReader);
            excelReader.read(EasyExcel.readSheet(options.sheetNo()).build());
        }
    }
}
