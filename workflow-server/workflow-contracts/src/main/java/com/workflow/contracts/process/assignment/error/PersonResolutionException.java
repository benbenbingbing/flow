package com.workflow.contracts.process.assignment.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人员解析的结构化运行时失败。
 *
 * <p>与普通编程异常不同，该异常表示可预期、可处置的人员目录结果，
 * 调用方应将 {@code reasonCode} 传递给空办理人策略和 incident。</p>
 */
public class PersonResolutionException extends RuntimeException {

    private final String reasonCode;
    private final Map<String, Object> details;

    /**
     * 初始化人员解析异常，保存构造参数供后续方法使用。
     *
     * @param reasonCode 原因编码，后续用于初始化人员解析异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public PersonResolutionException(String reasonCode, String message) {
        this(reasonCode, message, Map.of(), null);
    }

    /**
     * 初始化人员解析异常，保存构造参数供后续方法使用。
     *
     * @param reasonCode 原因编码，后续用于初始化人员解析异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param details 详情，保存在对象中供后续校验、查询或展示
     */
    public PersonResolutionException(
            String reasonCode,
            String message,
            Map<String, Object> details) {
        this(reasonCode, message, details, null);
    }

    /**
     * 初始化人员解析异常，保存构造参数供后续方法使用。
     *
     * @param reasonCode 原因编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param details 详情依赖，保存到当前对象供后续业务方法调用
     * @param cause 原因，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PersonResolutionException(
            String reasonCode,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        super(message, cause);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("人员解析失败码不能为空");
        }
        this.reasonCode = reasonCode.trim();
        this.details = details == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(details));
    }

    /**
     * 生成原因编码文本，供后续匹配或展示。
     *
     * @return 处理后的原因编码文本，供调用方比较或展示
     */
    public String reasonCode() {
        return reasonCode;
    }

    /**
     * 整理详情数据，供调用方遍历或继续处理。
     *
     * @return 详情键值结果，供调用方继续处理
     */
    public Map<String, Object> details() {
        return details;
    }
}
