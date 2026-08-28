package com.workflow.embed.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 第三方嵌入运行时的安全默认配置。
 *
 * <p>所有时间均由服务端决定，浏览器不能通过 Launch 或 Runtime 请求覆盖这些上限。</p>
 */
@Validated
@ConfigurationProperties(prefix = "workflow.embed")
public class EmbedProperties {

    private boolean enabled;

    @NotBlank
    private String publicBaseUrl = "http://localhost:8080";

    @NotBlank
    @Pattern(regexp = "^/[A-Za-z0-9_./-]+$")
    private String entryAssetPath = "/embed-assets/embed-main.js";

    @NotBlank
    @Pattern(regexp = "^/[A-Za-z0-9_./-]+$")
    private String entryStylePath = "/embed-assets/embed-main.css";

    @Min(10)
    @Max(300)
    private int launchTtlSeconds = 60;

    @Min(60)
    @Max(3600)
    private int sessionIdleSeconds = 300;

    @Min(300)
    @Max(86400)
    private int sessionAbsoluteSeconds = 1800;

    @Min(32)
    @Max(64)
    private int secretBytes = 32;

    @Min(1)
    @Max(200)
    private int maxPageSize = 100;

    @Min(1024)
    @Max(8_388_608)
    private int maxPayloadBytes = 1_048_576;

    @Min(1)
    @Max(1000)
    private int maxSelectionSize = 100;

    /** 单个 Launch 每分钟允许的 code 兑换尝试数。 */
    @Min(1)
    @Max(1000)
    private int exchangeLaunchLimitPerMinute = 10;

    /** 同一直连对端地址每分钟允许的 code 兑换尝试数。 */
    @Min(1)
    @Max(100_000)
    private int exchangeAddressLimitPerMinute = 120;

    /** 单个 Embed Session 所有已认证请求的每分钟硬上限。 */
    @Min(1)
    @Max(100_000)
    private int runtimeSessionLimitPerMinute = 120;

    /** 单个 Embed Session 写请求的每分钟硬上限。 */
    @Min(1)
    @Max(100_000)
    private int writeSessionLimitPerMinute = 30;

    /** 单个 Embed Session Heartbeat 的每分钟硬上限。 */
    @Min(1)
    @Max(100_000)
    private int heartbeatSessionLimitPerMinute = 12;

    /**
     * Runtime 请求并发租约的故障回收 TTL。正常路径会在响应完成后立即释放；
     * TTL 只用于 Pod 崩溃时防止永久泄漏。Runtime 接口必须保持服务端超时小于此值。
     */
    @Min(60)
    @Max(3600)
    private int runtimeRequestLeaseSeconds = 300;

    /** 每个维护 SQL 的最大影响行数，避免定时任务形成大事务。 */
    @Min(1)
    @Max(1000)
    private int maintenanceBatchSize = 200;

    /** 维护批次之间的固定延迟，防止错误配置形成紧循环。 */
    @Min(1000)
    @Max(86_400_000)
    private int maintenanceScanMs = 60_000;

    /** Session 进入终态后保留 Context 密文的秒数。 */
    @Min(0)
    @Max(315_360_000)
    private int terminalContextRetentionSeconds = 3600;

    /** 幂等记录消失后，Embed 最小业务回执的独立保留秒数。 */
    @Min(3600)
    @Max(315_360_000)
    private int operationReceiptRetentionSeconds = 2_592_000;

    /** Session 进入终态后的完整记录保留秒数。 */
    @Min(3600)
    @Max(315_360_000)
    private int terminalSessionRetentionSeconds = 7_776_000;

    /** 无 Session 引用的终态 Launch 保留秒数。 */
    @Min(3600)
    @Max(315_360_000)
    private int terminalLaunchRetentionSeconds = 7_776_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getEntryAssetPath() {
        return entryAssetPath;
    }

    public void setEntryAssetPath(String entryAssetPath) {
        this.entryAssetPath = entryAssetPath;
    }

    public String getEntryStylePath() {
        return entryStylePath;
    }

    public void setEntryStylePath(String entryStylePath) {
        this.entryStylePath = entryStylePath;
    }

    public int getLaunchTtlSeconds() {
        return launchTtlSeconds;
    }

    public void setLaunchTtlSeconds(int launchTtlSeconds) {
        this.launchTtlSeconds = launchTtlSeconds;
    }

    public int getSessionIdleSeconds() {
        return sessionIdleSeconds;
    }

    public void setSessionIdleSeconds(int sessionIdleSeconds) {
        this.sessionIdleSeconds = sessionIdleSeconds;
    }

    public int getSessionAbsoluteSeconds() {
        return sessionAbsoluteSeconds;
    }

    public void setSessionAbsoluteSeconds(int sessionAbsoluteSeconds) {
        this.sessionAbsoluteSeconds = sessionAbsoluteSeconds;
    }

    public int getSecretBytes() {
        return secretBytes;
    }

    public void setSecretBytes(int secretBytes) {
        this.secretBytes = secretBytes;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getMaxSelectionSize() {
        return maxSelectionSize;
    }

    public void setMaxSelectionSize(int maxSelectionSize) {
        this.maxSelectionSize = maxSelectionSize;
    }

    public int getExchangeLaunchLimitPerMinute() {
        return exchangeLaunchLimitPerMinute;
    }

    public void setExchangeLaunchLimitPerMinute(int value) {
        this.exchangeLaunchLimitPerMinute = value;
    }

    public int getExchangeAddressLimitPerMinute() {
        return exchangeAddressLimitPerMinute;
    }

    public void setExchangeAddressLimitPerMinute(int value) {
        this.exchangeAddressLimitPerMinute = value;
    }

    public int getRuntimeSessionLimitPerMinute() {
        return runtimeSessionLimitPerMinute;
    }

    public void setRuntimeSessionLimitPerMinute(int value) {
        this.runtimeSessionLimitPerMinute = value;
    }

    public int getWriteSessionLimitPerMinute() {
        return writeSessionLimitPerMinute;
    }

    public void setWriteSessionLimitPerMinute(int value) {
        this.writeSessionLimitPerMinute = value;
    }

    public int getHeartbeatSessionLimitPerMinute() {
        return heartbeatSessionLimitPerMinute;
    }

    public void setHeartbeatSessionLimitPerMinute(int value) {
        this.heartbeatSessionLimitPerMinute = value;
    }

    public int getRuntimeRequestLeaseSeconds() {
        return runtimeRequestLeaseSeconds;
    }

    public void setRuntimeRequestLeaseSeconds(int runtimeRequestLeaseSeconds) {
        this.runtimeRequestLeaseSeconds = runtimeRequestLeaseSeconds;
    }

    public int getMaintenanceBatchSize() {
        return maintenanceBatchSize;
    }

    public void setMaintenanceBatchSize(int maintenanceBatchSize) {
        this.maintenanceBatchSize = maintenanceBatchSize;
    }

    public int getMaintenanceScanMs() {
        return maintenanceScanMs;
    }

    public void setMaintenanceScanMs(int maintenanceScanMs) {
        this.maintenanceScanMs = maintenanceScanMs;
    }

    public int getTerminalContextRetentionSeconds() {
        return terminalContextRetentionSeconds;
    }

    public void setTerminalContextRetentionSeconds(int value) {
        this.terminalContextRetentionSeconds = value;
    }

    public int getOperationReceiptRetentionSeconds() {
        return operationReceiptRetentionSeconds;
    }

    public void setOperationReceiptRetentionSeconds(int value) {
        this.operationReceiptRetentionSeconds = value;
    }

    public int getTerminalSessionRetentionSeconds() {
        return terminalSessionRetentionSeconds;
    }

    public void setTerminalSessionRetentionSeconds(int value) {
        this.terminalSessionRetentionSeconds = value;
    }

    public int getTerminalLaunchRetentionSeconds() {
        return terminalLaunchRetentionSeconds;
    }

    public void setTerminalLaunchRetentionSeconds(int value) {
        this.terminalLaunchRetentionSeconds = value;
    }

    /** Context 必须先于完整 Session 删除，确保“提前擦除”的配置语义不会被反转。 */
    @AssertTrue(message = "terminalContextRetentionSeconds must not exceed terminalSessionRetentionSeconds")
    public boolean isTerminalContextErasureOrdered() {
        return terminalContextRetentionSeconds <= terminalSessionRetentionSeconds;
    }

    /**
     * 生产 Embed 入口只能使用 HTTPS origin；HTTP 仅用于本机开发回环地址。
     * user-info、path、query 和 fragment 会改变边界语义，因此全部拒绝。
     */
    @AssertTrue(message = "publicBaseUrl must be an HTTPS origin (HTTP is loopback-only)")
    public boolean isPublicBaseUrlSecureOrigin() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()
                || !publicBaseUrl.equals(publicBaseUrl.trim())) {
            return false;
        }
        try {
            URI uri = new URI(publicBaseUrl);
            if (uri.isOpaque()
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getRawAuthority().endsWith(":")
                    || uri.getPort() == 0
                    || uri.getPort() > 65_535) {
                return false;
            }
            String scheme = uri.getScheme() == null
                    ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if ("https".equals(scheme)) {
                return true;
            }
            if (!"http".equals(scheme)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return "localhost".equals(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "[::1]".equals(host);
        } catch (URISyntaxException error) {
            return false;
        }
    }

    @AssertTrue(message = "entryAssetPath must be a safe /embed-assets/ path")
    public boolean isEntryAssetPathSafe() {
        return safeEmbedAssetPath(entryAssetPath);
    }

    @AssertTrue(message = "entryStylePath must be a safe /embed-assets/ path")
    public boolean isEntryStylePathSafe() {
        return safeEmbedAssetPath(entryStylePath);
    }

    /**
     * 点号可用于文件扩展名，但禁止独立的 {@code .}/{@code ..} 段、空段和反斜杠，
     * 防止稳定 Embed 资产路径逃逸专用静态目录。
     */
    private static boolean safeEmbedAssetPath(String path) {
        if (path == null
                || !path.startsWith("/embed-assets/")
                || path.length() == "/embed-assets/".length()
                || path.contains("\\")
                || path.contains("//")
                || !path.matches("/[A-Za-z0-9_./-]+")) {
            return false;
        }
        for (String segment : path.substring(1).split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
