package com.workflow.contracts.embed;

import java.util.List;

/**
 * 为 Embed iframe 提供当前映射 Flow 用户的最小实时 UI 权限快照。
 *
 * <p>返回值只用于原生组件显隐；真正授权仍由 EndpointAuthorization、
 * 对象权限和 DataScope 在每个请求中实时执行。实现不得签发 JWT、Cookie
 * 或其他可用于普通登录的凭证。</p>
 */
public interface EmbedNativeActorRuntimePort {

    /** 从指定 Flow 用户的当前角色/菜单授权解析安全快照。 */
    ActorSnapshot resolve(
            String flowUserId,
            String expectedUsername,
            String fallbackDisplayName);

    record ActorSnapshot(
            String username,
            String nickname,
            String displayName,
            List<String> roles,
            boolean isSuperAdmin,
            List<String> permissions) {
    }
}
