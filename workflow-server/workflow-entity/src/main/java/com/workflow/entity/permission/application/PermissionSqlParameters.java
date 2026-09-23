package com.workflow.entity.permission.application;

import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.entity.data.application.EntityQueryScalarValues;
import java.util.Map;
import java.util.Objects;

/** 权限规则共享的参数命名空间；允许/拒绝/委托规则合并时不能覆盖先前绑定值。 */
public final class PermissionSqlParameters {
    /**
     * 初始化权限SQL参数集合，保存构造参数供后续方法使用。
     */
    private PermissionSqlParameters() { }

    /**
     * 为文本值（包括 NULL）分配独立参数，返回动态实体 Mapper 使用的占位符。
     * 显式 VARCHAR 避免 NULL 被 MyBatis 当作 JDBC OTHER；内容不会拼入 SQL。
     *
     * @param parameters 参数集合，作为 {@code bind} 的输入影响后续处理
     * @param value 待处理绑定文本的原始输入，结果供调用方继续使用
     * @return 处理后的绑定文本文本，供调用方比较或展示
     */
    public static String bindText(Map<String, Object> parameters, String value) {
        return bind(parameters, value, "VARCHAR");
    }

    /**
     * 按发布字段的存储类型绑定标量，包含 IN 列表中的 NULL。
     * 不依赖 MySQL 的字符串转数值或驱动对未知 NULL 的推断；不合法值抛出异常，
     * 由权限引擎按整条允许/拒绝规则失败处理，不能把损坏的拒绝规则变成不匹配。
     *
     * @param parameters 参数集合，作为 {@code bind} 的输入影响后续处理
     * @param value 待处理绑定标量的原始输入，结果供调用方继续使用
     * @param kind 类型，作为 {@code scalarValue} 的输入影响后续处理
     * @return 处理后的绑定标量文本，供调用方比较或展示
     */
    public static String bindScalar(Map<String, Object> parameters, Object value, SchemaType.Kind kind) {
        Object converted = scalarValue(value, kind);
        return bind(parameters, converted, EntityQueryScalarValues.jdbcType(kind));
    }

    /**
     * 比较参数按方言选择 JDBC 类型；CLOB 仍保持 Java String，由 MyBatis 标准处理器完整流式绑定。
     *
     * @param parameters 参数集合，作为 {@code bind} 的输入影响后续处理
     * @param value 待处理绑定标量的原始输入，结果供调用方继续使用
     * @param kind 类型，作为 {@code bind} 的输入影响后续处理
     * @param dialect 方言，供本方法处理绑定标量时使用
     * @return 处理后的绑定标量文本，供调用方比较或展示
     */
    public static String bindScalar(Map<String, Object> parameters, Object value, SchemaType.Kind kind,
                                    DatabaseQueryDialect dialect) {
        return bind(parameters, scalarValue(value, kind), Objects.requireNonNull(dialect, "dialect").comparisonJdbcType(kind));
    }

    /**
     * 校验与绑定使用同一转换；日期按无时区业务字段处理，拒绝静默丢弃输入中的时区。
     *
     * @param value 待处理标量值的原始输入，结果供调用方继续使用
     * @param kind 类型，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的标量值结果，供调用方继续处理
     */
    public static Object scalarValue(Object value, SchemaType.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("权限字段缺少存储类型");
        try {
            return EntityQueryScalarValues.scalarValue(value, kind);
        } catch (RuntimeException invalid) {
            // 不把原始值写进错误信息，权限配置可能包含用户或组织标识。
            throw new IllegalArgumentException("权限比较值不符合字段类型 " + kind, invalid);
        }
    }

    /**
     * 生成绑定文本，供后续匹配或展示。
     *
     * @param parameters 参数集合，供本方法处理绑定时使用
     * @param value 待处理绑定的原始输入，结果供调用方继续使用
     * @param jdbcType JDBC类型标识，决定后续绑定采用的处理分支
     * @return 处理后的绑定文本，供调用方比较或展示
     */
    private static String bind(Map<String, Object> parameters, Object value, String jdbcType) {
        Objects.requireNonNull(parameters, "权限 SQL 参数容器不能为空");
        int index = parameters.size();
        String key = "permissionValue" + index;
        while (parameters.containsKey(key)) key = "permissionValue" + ++index;
        parameters.put(key, value);
        String handler = "CLOB".equals(jdbcType) ? ",typeHandler=org.apache.ibatis.type.ClobTypeHandler" : "";
        return "#{permissionParameters." + key + ",jdbcType=" + jdbcType + handler + "}";
    }
}
