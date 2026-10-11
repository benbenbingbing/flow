package com.workflow.excel.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 使用 Jakarta Bean Validation 执行 Excel DTO 字段校验，支持 Excel 注解、标准约束及校验分组。
 * 不负责检查业务唯一性、查询数据库或修改原始数据，可在监听器之外复用。
 */
public final class ExcelValidator {
    private ExcelValidator() {
    }

    /**
     * 校验一条 DTO，返回稳定排序的字段错误；注解类型或参数配置错误直接传播，不能当作数据错误忽略。
     *
     * @param data 待校验对象，不允许 null；必填字段应在 DTO 上声明对应约束
     * @param groups 本次启用的分组，不指定时使用 Default；指定分组时遵循 Jakarta 的分组规则
     * @return 同一对象的全部错误，无错误时返回空列表；返回列表不可修改
     */
    public static List<ExcelValidationError> validate(Object data, Class<?>... groups) {
        Objects.requireNonNull(data, "待校验数据不能为空");
        Objects.requireNonNull(groups, "校验分组不能为空");
        // 约束集合不保证顺序，按字段和消息排序，保证错误报告及调用方提示的顺序稳定。
        return ValidatorHolder.VALIDATOR.validate(data, groups).stream()
                .map(error -> new ExcelValidationError(error.getPropertyPath().toString(), error.getMessage()))
                .sorted(Comparator.comparing(ExcelValidationError::fieldName)
                        .thenComparing(ExcelValidationError::message))
                .toList();
    }

    /**
     * 在数据进入业务批处理前执行字段校验，失败时保留原始工作表和行位置。
     *
     * @param data 待校验的 DTO，不允许 null
     * @param sheetName 工作表名称，失败时用于错误报告
     * @param rowIndex 包含表头的原始行索引，从 0 开始
     * @param groups 本次启用的校验分组，不指定时使用 Default
     * @throws ExcelValidationException 当前行存在数据校验错误，包含该行全部字段错误
     */
    public static void validateOrThrow(Object data, String sheetName, int rowIndex, Class<?>... groups) {
        List<ExcelValidationError> errors = validate(data, groups);
        if (!errors.isEmpty()) {
            throw new ExcelValidationException(sheetName, rowIndex, errors);
        }
    }

    private static final class ValidatorHolder {
        // 按需初始化并复用线程安全的 Validator，避免每一行重建工厂及其注解元数据缓存。
        private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
        private static final Validator VALIDATOR = FACTORY.getValidator();
    }
}
