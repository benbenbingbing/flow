package com.workflow.contracts.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 为未显式声明制品摘要的存量 UI Provider 生成稳定身份。
 *
 * <p>默认实现对 Provider 自身 class 字节码、稳定类名和版本号计算 SHA-256。
 * 这样现有实现无需立即增加方法，也能在宿主发布时固定当前可执行制品；需要把
 * 多个类或外部资源视为同一制品时，实现方应显式覆盖 artifactDigest。</p>
 */
public final class UiProviderArtifactIdentity {

    private UiProviderArtifactIdentity() {
    }

    /**
     * 计算 Provider 当前实现的默认制品摘要。
     *
     * @param implementationType Provider 运行时实现类型
     * @param version            Provider 声明版本
     * @return 64 位小写十六进制 SHA-256
     */
    public static String defaultDigest(
            Class<?> implementationType,
            int version) {
        if (implementationType == null) {
            throw new IllegalStateException("Provider 实现类型不能为空");
        }
        if (version < 1) {
            throw new IllegalStateException("Provider 版本必须为正整数");
        }
        Class<?> stableType = stableType(implementationType);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(stableType.getName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(String.valueOf(version).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String resource = "/" + stableType.getName()
                    .replace('.', '/') + ".class";
            try (InputStream input = stableType.getResourceAsStream(resource)) {
                if (input != null) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Provider 制品摘要计算失败", exception);
        }
    }

    /** 避免把运行时生成的代理类名作为可持久化制品身份。 */
    private static Class<?> stableType(Class<?> value) {
        Class<?> current = value;
        while (current.getSuperclass() != null
                && current.getSuperclass() != Object.class
                && (current.isSynthetic()
                || current.getName().contains("$$"))) {
            current = current.getSuperclass();
        }
        return current;
    }
}
