package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchConfiguration;
import java.time.Instant;
import java.util.Optional;

/** Reads the application/view/grant plus the View's current stable resource config. */
public interface EmbedLaunchConfigurationPort {

    /**
     * Performs the non-locking preflight read used before the independent Launch quota transaction.
     *
     * @param applicationId 应用ID，后续用于查询嵌入式启动记录配置时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法查询嵌入式启动记录配置时使用
     * @return 匹配的嵌入式启动记录配置；未找到时为空
     */
    Optional<EmbedLaunchConfiguration> find(
            String applicationId,
            String viewKey,
            Instant now);

    /**
     * Locks and reloads the complete security configuration used by the final Launch transaction.
     *
     * @param applicationId 应用ID，后续用于锁定更新时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法锁定更新时使用
     * @return 匹配的更新；未找到时为空
     */
    Optional<EmbedLaunchConfiguration> lockForUpdate(
            String applicationId,
            String viewKey,
            Instant now);
}
