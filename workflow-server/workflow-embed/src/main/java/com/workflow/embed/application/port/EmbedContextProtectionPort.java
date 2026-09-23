package com.workflow.embed.application.port;

import com.workflow.embed.domain.ProtectedContext;
import java.util.Map;

/** Encrypts trusted launch/session context and computes its versioned equality-safe digest. */
public interface EmbedContextProtectionPort {

    /**
     * 处理保护启动记录，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理保护启动记录时定位或关联目标
     * @param launchId 启动记录ID，后续用于处理保护启动记录时定位或关联目标
     * @param context 执行上下文，向后续保护启动记录步骤传递身份、配置或状态
     * @return 处理后的保护启动记录结果，供调用方继续处理
     */
    ProtectedContext protectLaunch(
            String applicationId,
            String launchId,
            Map<String, Object> context);

    /**
     * 整理解除保护启动记录数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理解除保护启动记录时定位或关联目标
     * @param launchId 启动记录ID，后续用于处理解除保护启动记录时定位或关联目标
     * @param context 执行上下文，向后续解除保护启动记录步骤传递身份、配置或状态
     * @return 解除保护启动记录键值结果，供调用方继续处理
     */
    Map<String, Object> unprotectLaunch(
            String applicationId,
            String launchId,
            ProtectedContext context);

    /**
     * 处理保护会话，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理保护会话时定位或关联目标
     * @param sessionId 会话ID，后续用于处理保护会话时定位或关联目标
     * @param context 执行上下文，向后续保护会话步骤传递身份、配置或状态
     * @return 处理后的保护会话结果，供调用方继续处理
     */
    ProtectedContext protectSession(
            String applicationId,
            String sessionId,
            Map<String, Object> context);

    /**
     * 整理解除保护会话数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理解除保护会话时定位或关联目标
     * @param sessionId 会话ID，后续用于处理解除保护会话时定位或关联目标
     * @param ciphertext {@code ciphertext}，供本方法处理解除保护会话时使用
     * @param keyVersion 键版本，供本方法处理解除保护会话时使用
     * @return 解除保护会话键值结果，供调用方继续处理
     */
    Map<String, Object> unprotectSession(
            String applicationId,
            String sessionId,
            String ciphertext,
            String keyVersion);
}
