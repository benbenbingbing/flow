package com.workflow.contracts.embed.runtime.port;

import java.util.List;

/**
 * 为 Embed iframe 提供当前映射 Flow 用户的最小实时 UI 权限快照。
 *
 * <p>返回值只用于原生组件显隐；真正授权仍由平台在每个请求中实时执行。</p>
 */
public interface EmbedNativeActorRuntimePort {

    /**
     * 从指定 Flow 用户的当前角色/菜单授权解析安全快照。
     *
     * @param flowUserId 流程用户ID，后续用于解析嵌入式原生操作人运行时时定位或关联目标
     * @param expectedUsername 预期用户名，后续用于解析嵌入式原生操作人运行时时匹配或展示
     * @param fallbackDisplayName 兜底展示名称，主值不可用时供后续处理兜底
     * @return 解析后的嵌入式原生操作人运行时结果，供调用方继续处理
     */
    ActorSnapshot resolve(
            String flowUserId,
            String expectedUsername,
            String fallbackDisplayName);

    /**
     * Embed 原生组件所需的最小用户与权限快照。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param nickname 用户昵称，供界面展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param roles 角色集合，保存在对象中供后续校验、查询或展示
     * @param isSuperAdmin 是否{@code super}{@code admin}，保存在对象中供后续校验、查询或展示
     * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
     */
    record ActorSnapshot(
            String username,
            String nickname,
            String displayName,
            List<String> roles,
            boolean isSuperAdmin,
            List<String> permissions) {
    }
}
