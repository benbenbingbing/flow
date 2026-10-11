package com.workflow.excel;

/**
 * 一次导入的读取配置；不可变对象可在多个调用之间复用。
 *
 * @param sheetNo Sheet 索引，从 0 开始，用于选择本次要导入的工作表
 * @param headRowNumber 表头占用的行数，读取数据时跳过这些行；无表头文件应设为 0
 * @param batchSize 每批交给业务回调的最大数据行数，用于限制分批导入时的内存占用
 * @param autoTrim 是否让 EasyExcel 在转换前去除文本首尾空白；需保留原始空白时设为 false
 */
public record ExcelReadOptions(int sheetNo, int headRowNumber, int batchSize, boolean autoTrim) {
    private static final ExcelReadOptions DEFAULTS = new ExcelReadOptions(0, 1, 500);

    /** 保留原有三参数调用方式，默认启用 EasyExcel 的首尾空白清理。 */
    public ExcelReadOptions(int sheetNo, int headRowNumber, int batchSize) {
        this(sheetNo, headRowNumber, batchSize, true);
    }

    /**
     * 在读取文件前校验配置，避免错误配置导致表头被当作业务数据。
     *
     * @throws IllegalArgumentException Sheet 索引、表头行数为负数或批大小不是正数
     */
    public ExcelReadOptions {
        if (sheetNo < 0) {
            throw new IllegalArgumentException("Sheet 索引不能小于 0");
        }
        if (headRowNumber < 0) {
            throw new IllegalArgumentException("表头行数不能小于 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("每批数据行数必须大于 0");
        }
    }

    /**
     * 默认读取第一个 Sheet，跳过一行表头，每批处理 500 行。
     *
     * @return 可复用的默认配置
     */
    public static ExcelReadOptions defaults() {
        return DEFAULTS;
    }
}
