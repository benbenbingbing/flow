package com.workflow.contracts.integration;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 集成调用结果。
 * 描述集成调用的成功与否、状态码、消息及返回数据。
 */
@Value
@Builder
public class IntegrationResult {

    /** 是否调用成功 */
    boolean success;
    /** 结果状态码 */
    String code;
    /** 结果消息 */
    String message;
    /** 返回数据 */
    Map<String, Object> data;
}
