package com.workflow.excel.validation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 单行数据未通过字段校验时中止读取，保存同一行的全部错误供业务模块生成提示。 */
public final class ExcelValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String sheetName;
    private final int rowIndex;
    private final List<ExcelValidationError> errors;

    /**
     * 构造数据校验失败结果，异常消息使用用户看到的行号，结构化索引仍从 0 开始。
     *
     * @param sheetName 出错工作表名称，用于区分多工作表的导入来源
     * @param rowIndex 文件中的行索引，从 0 开始，包含表头占用的行
     * @param errors 同一行的字段错误，必须非空，会复制为不可修改的列表
     */
    public ExcelValidationException(String sheetName, int rowIndex, List<ExcelValidationError> errors) {
        super(message(sheetName, rowIndex, errors));
        this.sheetName = sheetName;
        this.rowIndex = rowIndex;
        this.errors = List.copyOf(errors);
    }

    /** @return 出错工作表名称，供导入错误报告标识数据来源 */
    public String getSheetName() {
        return sheetName;
    }

    /** @return 包含表头的原始行索引，从 0 开始；展示时加 1 */
    public int getRowIndex() {
        return rowIndex;
    }

    /** @return 同一行的全部字段错误，返回列表不可修改 */
    public List<ExcelValidationError> getErrors() {
        return errors;
    }

    private static String message(String sheetName, int rowIndex, List<ExcelValidationError> errors) {
        Objects.requireNonNull(sheetName, "工作表名称不能为空");
        Objects.requireNonNull(errors, "校验错误不能为空");
        if (rowIndex < 0 || errors.isEmpty()) {
            throw new IllegalArgumentException("校验异常必须包含合法行索引和至少一个字段错误");
        }
        return "Sheet「" + sheetName + "」第 " + (rowIndex + 1) + " 行校验失败："
                + errors.stream().map(error -> error.fieldName() + " " + error.message())
                        .collect(Collectors.joining("；"));
    }
}
