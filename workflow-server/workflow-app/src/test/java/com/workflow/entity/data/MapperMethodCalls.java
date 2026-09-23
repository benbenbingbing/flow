package com.workflow.entity.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSession;

/** 让数据库测试调用真实 Mapper 入口，包括包内接口和委托给 Wrapper/Page 的默认方法。 */
final class MapperMethodCalls {
    private MapperMethodCalls() {
    }

    /**
     * 根据完整 Mapper 方法名调用代理；参数优先使用 {@code @Param}，否则使用编译保留的参数名。
     * 缺少参数或方法重载不明确时直接失败，避免测试绕过默认方法或把遗漏参数静默当成 null。
     * 被调用方法的运行时异常原样抛出，保留数据库异常翻译与事务断言的类型。
     */
    @SuppressWarnings("unchecked")
    static <T> T call(SqlSession session, String qualifiedMethod, Map<String, ?> args) {
        int separator = qualifiedMethod.lastIndexOf('.');
        if (separator <= 0 || separator == qualifiedMethod.length() - 1) {
            throw new IllegalArgumentException("需要完整的 Mapper 类名和方法名");
        }
        try {
            Class<?> mapperType = Class.forName(qualifiedMethod.substring(0, separator));
            String methodName = qualifiedMethod.substring(separator + 1);
            List<Method> candidates = Arrays.stream(mapperType.getMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .filter(method -> Arrays.stream(method.getParameters())
                            .allMatch(parameter -> args.containsKey(parameterName(parameter))))
                    .toList();
            if (candidates.size() != 1) {
                throw new IllegalArgumentException("Mapper 方法参数缺失或重载不明确: " + qualifiedMethod);
            }
            Method method = candidates.get(0);
            Object[] values = Arrays.stream(method.getParameters())
                    .map(parameter -> args.get(parameterName(parameter))).toArray();
            method.setAccessible(true);
            return (T) method.invoke(session.getMapper(mapperType), values);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Mapper 方法调用失败: " + qualifiedMethod, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("无法调用 Mapper 方法: " + qualifiedMethod, exception);
        }
    }

    private static String parameterName(Parameter parameter) {
        Param annotation = parameter.getAnnotation(Param.class);
        if (annotation != null) {
            return annotation.value();
        }
        if (!parameter.isNamePresent()) {
            throw new IllegalArgumentException("Mapper 参数需要 @Param 或编译参数名");
        }
        return parameter.getName();
    }
}
