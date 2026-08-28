package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;

/**
 * 受控加载管理员登记的远程 JWK Set。
 *
 * <p>实现必须只访问 Provider 快照中的固定 HTTPS 地址，并按 Provider 安全版本隔离缓存。
 * {@code forceRefresh} 用于已缓存集合找不到 JWT {@code kid} 时的一次受控刷新；实现仍可合并
 * 并发刷新以防止刷新风暴。</p>
 */
public interface EmbedRemoteJwkSetPort {

    /**
     * 返回已验证、只含公开验签材料的 JWK Set JSON。
     *
     * @param provider 已加载的 Provider 安全快照
     * @param forceRefresh 是否绕过普通 TTL 缓存执行受控刷新
     * @return 可交给验签器解析的公开 JWK Set JSON
     * @throws IllegalArgumentException Provider 或远程地址不满足安全约束
     * @throws IllegalStateException 远程响应不可用或 JWK Set 不合法
     */
    String load(EmbedIdentityProviderSnapshot provider, boolean forceRefresh);
}
