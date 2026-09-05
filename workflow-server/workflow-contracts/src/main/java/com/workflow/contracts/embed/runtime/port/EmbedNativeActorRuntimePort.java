package com.workflow.contracts.embed.runtime.port;

import java.util.List;

/**
 * 为 Embed iframe 提供当前映射 Flow 用户的最小实时 UI 权限快照。
 *
 * <p>返回值只用于原生组件显隐；真正授权仍由平台在每个请求中实时执行。</p>
 */
public interface EmbedNativeActorRuntimePort {

    /** 从指定 Flow 用户的当前角色/菜单授权解析安全快照。 */
    ActorSnapshot resolve(
            String flowUserId,
            String expectedUsername,
            String fallbackDisplayName);

    /** Embed 原生组件所需的最小用户与权限快照。 */
    record ActorSnapshot(
            String username,
            String nickname,
            String displayName,
            List<String> roles,
            boolean isSuperAdmin,
            List<String> permissions) {
    }
}
