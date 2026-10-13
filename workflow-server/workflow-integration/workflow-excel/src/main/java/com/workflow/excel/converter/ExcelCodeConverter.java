package com.workflow.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.workflow.excel.annotation.validation.ExcelCode;
import com.workflow.excel.annotation.converter.ExcelCodeMapping;
import java.lang.reflect.Field;
import com.workflow.excel.dynamic.ExcelRawCell;
import com.workflow.excel.dynamic.ExcelValueCodec;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 通过字段上的 ExcelCodeMapping 执行代码与名称的双向转换，必须通过 ExcelProperty.converter 绑定。
 * 支持 String、整数包装类型/基本类型、BigInteger、Boolean 和枚举，不查询业务字典或依赖 Spring。
 * 空文本返回 null；未知值默认报错，转换错误由 EasyExcel 保留原始行列信息。
 */
public final class ExcelCodeConverter implements Converter<Object> {
    private static final ClassValue<ConcurrentMap<String, Mapping>> MAPPINGS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<String, Mapping> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    @Override
    public Class<?> supportJavaTypeKey() {
        return Object.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /** 优先按名称还原代码，可配置允许直接填写代码；禁止模糊匹配或将未知名称映射成默认代码。 */
    @Override
    public Object convertToJavaData(ReadCellData<?> cell, ExcelContentProperty property, GlobalConfiguration global) {
        ExcelValueCodec.requireLiteral(ExcelRawCell.from(cell, 0, false));
        Mapping mapping = mapping(property);
        // 整数代码按底层数值读取；String 代码仍遵循显示格式，以保留 000 等格式承载的前导零。
        String text = cell.getType() == CellDataTypeEnum.NUMBER && mapping.type() != String.class
                ? cell.getNumberValue().stripTrailingZeros().toPlainString()
                : ConverterSupport.text(cell, property, global);
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.strip();
        // 通用核心决定名称/代码含义，强类型适配只负责数值别名和 Java 类型还原。
        if (mapping.byLabel().containsKey(text) || mapping.allowCode() && mapping.byCodeText().containsKey(text)) {
            return parseCode(mapping.shared().readText(text), mapping.type());
        }
        Object code = null;
        if (code == null && mapping.allowCode()) {
            code = mapping.byCodeText().get(text);
            if (code == null && cell.getType() == CellDataTypeEnum.NUMBER && isInteger(mapping.type())) {
                Object numeric = parseCode(text, mapping.type());
                if (mapping.byValue().containsKey(numeric)) {
                    code = numeric;
                }
            }
        }
        if (code != null) {
            return code;
        }
        return parseCode(mapping.shared().readText(text), mapping.type());
    }

    /** 按 DTO 的实际代码值输出显示名称，严格模式下未知代码会中止导出。 */
    @Override
    public WriteCellData<?> convertToExcelData(Object value, ExcelContentProperty property, GlobalConfiguration global) {
        if (value == null) {
            return ConverterSupport.textCell(null);
        }
        Mapping mapping = mapping(property);
        String label = mapping.byValue().get(value);
        if (label == null) {
            if (mapping.strict()) {
                throw new IllegalArgumentException("导出的代码不在配置的映射中");
            }
            label = value instanceof Enum<?> item ? item.name() : value.toString();
        }
        return ConverterSupport.textCell(label);
    }

    private static Mapping mapping(ExcelContentProperty property) {
        if (property == null || property.getField() == null) {
            throw new IllegalArgumentException("代码转换器必须绑定声明 ExcelCodeMapping 的字段");
        }
        Field field = property.getField();
        return MAPPINGS.get(field.getDeclaringClass()).computeIfAbsent(field.getName(), name -> Mapping.create(field));
    }

    private static boolean isInteger(Class<?> type) {
        return type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class || type == Byte.class || type == byte.class
                || type == BigInteger.class;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object parseCode(String code, Class<?> type) {
        if (type == String.class) return code;
        if (type == Integer.class || type == int.class) return Integer.valueOf(code);
        if (type == Long.class || type == long.class) return Long.valueOf(code);
        if (type == Short.class || type == short.class) return Short.valueOf(code);
        if (type == Byte.class || type == byte.class) return Byte.valueOf(code);
        if (type == BigInteger.class) return new BigInteger(code);
        if (type == Boolean.class || type == boolean.class) {
            if ("true".equalsIgnoreCase(code)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(code)) return Boolean.FALSE;
            throw new IllegalArgumentException("Boolean 代码必须为 true 或 false");
        }
        if (type.isEnum()) return Enum.valueOf((Class<? extends Enum>) type, code);
        throw new IllegalArgumentException("不支持的代码字段类型：" + type.getName());
    }

    private record Mapping(Class<?> type, Map<Object, String> byValue, Map<String, Object> byLabel,
            Map<String, Object> byCodeText, boolean allowCode, boolean strict, ExcelValueCodec.CodeMapping shared) {
        private static Mapping create(Field field) {
            ExcelCodeMapping config = field.getAnnotation(ExcelCodeMapping.class);
            if (config == null || config.value().length == 0) {
                throw new IllegalArgumentException("代码字段必须声明非空的 ExcelCodeMapping");
            }
            Map<Object, String> values = new HashMap<>();
            Map<String, Object> labels = new HashMap<>();
            Map<String, Object> codes = new HashMap<>();
            for (ExcelCode item : config.value()) {
                String code = item.code();
                String label = item.label();
                if (code.isBlank() || label.isBlank() || !code.equals(code.strip()) || !label.equals(label.strip())) {
                    throw new IllegalArgumentException("映射的代码和名称不能空白或带有首尾空白");
                }
                Object value = parseCode(code, field.getType());
                // 例如 Integer 的「01」与「1」会成为相同 Java 值，必须拒绝以保证导出可逆。
                if (values.putIfAbsent(value, label) != null || labels.putIfAbsent(label, value) != null
                        || codes.putIfAbsent(code, value) != null) {
                    throw new IllegalArgumentException("映射的代码、名称或对应 Java 值不能重复");
                }
            }
            Map<String, String> sharedCodes = new HashMap<>();
            for (ExcelCode item : config.value()) sharedCodes.put(item.code(), item.label());
            ExcelValueCodec.CodeMapping shared = new ExcelValueCodec.CodeMapping(sharedCodes, config.allowCode(), config.strict());
            if (config.allowCode()) {
                for (Map.Entry<String, Object> label : labels.entrySet()) {
                    if (codes.containsKey(label.getKey()) && !codes.get(label.getKey()).equals(label.getValue())) {
                        throw new IllegalArgumentException("显示名称与另一项代码冲突，无法确定导入含义");
                    }
                }
            }
            return new Mapping(field.getType(), Map.copyOf(values), Map.copyOf(labels), Map.copyOf(codes),
                    config.allowCode(), config.strict(), shared);
        }
    }
}
