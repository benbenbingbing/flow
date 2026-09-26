package com.workflow.embed.infrastructure.config;

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

    /**
     * 判断是否启用；判断结果决定调用方的后续分支。
     *
     * @return 启用条件成立时为 true，否则为 false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用；后续读取或执行将使用更新后的状态。
     *
     * @param enabled 启用，供本方法设置启用时使用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取公开基础URL；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的公开基础URL文本，供调用方比较或展示
     */
    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    /**
     * 设置公开基础URL；后续读取或执行将使用更新后的状态。
     *
     * @param publicBaseUrl 公开基础URL，供本方法设置公开基础URL时使用
     */
    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 读取入口资产路径；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的入口资产路径文本，供调用方比较或展示
     */
    public String getEntryAssetPath() {
        return entryAssetPath;
    }

    /**
     * 设置入口资产路径；后续读取或执行将使用更新后的状态。
     *
     * @param entryAssetPath 入口资产路径，供本方法设置入口资产路径时使用
     */
    public void setEntryAssetPath(String entryAssetPath) {
        this.entryAssetPath = entryAssetPath;
    }

    /**
     * 读取入口{@code style}路径；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的入口{@code style}路径文本，供调用方比较或展示
     */
    public String getEntryStylePath() {
        return entryStylePath;
    }

    /**
     * 设置入口{@code style}路径；后续读取或执行将使用更新后的状态。
     *
     * @param entryStylePath 入口{@code style}路径，供本方法设置入口{@code style}路径时使用
     */
    public void setEntryStylePath(String entryStylePath) {
        this.entryStylePath = entryStylePath;
    }

    /**
     * 读取启动记录{@code ttl}秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getLaunchTtlSeconds() {
        return launchTtlSeconds;
    }

    /**
     * 设置启动记录{@code ttl}秒数；后续读取或执行将使用更新后的状态。
     *
     * @param launchTtlSeconds 启动记录{@code ttl}秒数，供本方法设置启动记录{@code ttl}秒数时使用
     */
    public void setLaunchTtlSeconds(int launchTtlSeconds) {
        this.launchTtlSeconds = launchTtlSeconds;
    }

    /**
     * 读取会话空闲秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getSessionIdleSeconds() {
        return sessionIdleSeconds;
    }

    /**
     * 设置会话空闲秒数；后续读取或执行将使用更新后的状态。
     *
     * @param sessionIdleSeconds 会话空闲秒数，供本方法设置会话空闲秒数时使用
     */
    public void setSessionIdleSeconds(int sessionIdleSeconds) {
        this.sessionIdleSeconds = sessionIdleSeconds;
    }

    /**
     * 读取会话绝对秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getSessionAbsoluteSeconds() {
        return sessionAbsoluteSeconds;
    }

    /**
     * 设置会话绝对秒数；后续读取或执行将使用更新后的状态。
     *
     * @param sessionAbsoluteSeconds 会话绝对秒数，供本方法设置会话绝对秒数时使用
     */
    public void setSessionAbsoluteSeconds(int sessionAbsoluteSeconds) {
        this.sessionAbsoluteSeconds = sessionAbsoluteSeconds;
    }

    /**
     * 读取密钥字节；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getSecretBytes() {
        return secretBytes;
    }

    /**
     * 设置密钥字节；后续读取或执行将使用更新后的状态。
     *
     * @param secretBytes 密钥字节，供本方法设置密钥字节时使用
     */
    public void setSecretBytes(int secretBytes) {
        this.secretBytes = secretBytes;
    }

    /**
     * 按筛选条件分页查询嵌入式属性集合；结果供列表展示。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置最大分页大小；后续读取或执行将使用更新后的状态。
     *
     * @param maxPageSize 最大分页大小，供本方法设置最大分页大小时使用
     */
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * 读取最大载荷字节；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * 设置最大载荷字节；后续读取或执行将使用更新后的状态。
     *
     * @param maxPayloadBytes 最大载荷字节，供本方法设置最大载荷字节时使用
     */
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * 读取最大选择大小；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getMaxSelectionSize() {
        return maxSelectionSize;
    }

    /**
     * 设置最大选择大小；后续读取或执行将使用更新后的状态。
     *
     * @param maxSelectionSize 最大选择大小，供本方法设置最大选择大小时使用
     */
    public void setMaxSelectionSize(int maxSelectionSize) {
        this.maxSelectionSize = maxSelectionSize;
    }

    /**
     * 读取交换启动记录上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getExchangeLaunchLimitPerMinute() {
        return exchangeLaunchLimitPerMinute;
    }

    /**
     * 设置交换启动记录上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置交换启动记录上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setExchangeLaunchLimitPerMinute(int value) {
        this.exchangeLaunchLimitPerMinute = value;
    }

    /**
     * 读取交换地址上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getExchangeAddressLimitPerMinute() {
        return exchangeAddressLimitPerMinute;
    }

    /**
     * 设置交换地址上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置交换地址上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setExchangeAddressLimitPerMinute(int value) {
        this.exchangeAddressLimitPerMinute = value;
    }

    /**
     * 读取运行时会话上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getRuntimeSessionLimitPerMinute() {
        return runtimeSessionLimitPerMinute;
    }

    /**
     * 设置运行时会话上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置运行时会话上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setRuntimeSessionLimitPerMinute(int value) {
        this.runtimeSessionLimitPerMinute = value;
    }

    /**
     * 读取写入会话上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getWriteSessionLimitPerMinute() {
        return writeSessionLimitPerMinute;
    }

    /**
     * 设置写入会话上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置写入会话上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setWriteSessionLimitPerMinute(int value) {
        this.writeSessionLimitPerMinute = value;
    }

    /**
     * 读取心跳会话上限每分钟；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getHeartbeatSessionLimitPerMinute() {
        return heartbeatSessionLimitPerMinute;
    }

    /**
     * 设置心跳会话上限每分钟；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置心跳会话上限每分钟的原始输入，结果供调用方继续使用
     */
    public void setHeartbeatSessionLimitPerMinute(int value) {
        this.heartbeatSessionLimitPerMinute = value;
    }

    /**
     * 读取运行时请求租约秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getRuntimeRequestLeaseSeconds() {
        return runtimeRequestLeaseSeconds;
    }

    /**
     * 设置运行时请求租约秒数；后续读取或执行将使用更新后的状态。
     *
     * @param runtimeRequestLeaseSeconds 运行时请求租约秒数，供本方法设置运行时请求租约秒数时使用
     */
    public void setRuntimeRequestLeaseSeconds(int runtimeRequestLeaseSeconds) {
        this.runtimeRequestLeaseSeconds = runtimeRequestLeaseSeconds;
    }

    /**
     * 读取维护批次大小；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getMaintenanceBatchSize() {
        return maintenanceBatchSize;
    }

    /**
     * 设置维护批次大小；后续读取或执行将使用更新后的状态。
     *
     * @param maintenanceBatchSize 维护批次大小，供本方法设置维护批次大小时使用
     */
    public void setMaintenanceBatchSize(int maintenanceBatchSize) {
        this.maintenanceBatchSize = maintenanceBatchSize;
    }

    /**
     * 读取维护{@code scan}{@code ms}；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getMaintenanceScanMs() {
        return maintenanceScanMs;
    }

    /**
     * 设置维护{@code scan}{@code ms}；后续读取或执行将使用更新后的状态。
     *
     * @param maintenanceScanMs 维护{@code scan}{@code ms}，供本方法设置维护{@code scan}{@code ms}时使用
     */
    public void setMaintenanceScanMs(int maintenanceScanMs) {
        this.maintenanceScanMs = maintenanceScanMs;
    }

    /**
     * 读取终态上下文{@code retention}秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getTerminalContextRetentionSeconds() {
        return terminalContextRetentionSeconds;
    }

    /**
     * 设置终态上下文{@code retention}秒数；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置终态上下文{@code retention}秒数的原始输入，结果供调用方继续使用
     */
    public void setTerminalContextRetentionSeconds(int value) {
        this.terminalContextRetentionSeconds = value;
    }

    /**
     * 读取操作回执{@code retention}秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getOperationReceiptRetentionSeconds() {
        return operationReceiptRetentionSeconds;
    }

    /**
     * 设置操作回执{@code retention}秒数；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作回执{@code retention}秒数的原始输入，结果供调用方继续使用
     */
    public void setOperationReceiptRetentionSeconds(int value) {
        this.operationReceiptRetentionSeconds = value;
    }

    /**
     * 读取终态会话{@code retention}秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getTerminalSessionRetentionSeconds() {
        return terminalSessionRetentionSeconds;
    }

    /**
     * 设置终态会话{@code retention}秒数；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置终态会话{@code retention}秒数的原始输入，结果供调用方继续使用
     */
    public void setTerminalSessionRetentionSeconds(int value) {
        this.terminalSessionRetentionSeconds = value;
    }

    /**
     * 读取终态启动记录{@code retention}秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式属性集合结果，供调用方继续处理
     */
    public int getTerminalLaunchRetentionSeconds() {
        return terminalLaunchRetentionSeconds;
    }

    /**
     * 设置终态启动记录{@code retention}秒数；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置终态启动记录{@code retention}秒数的原始输入，结果供调用方继续使用
     */
    public void setTerminalLaunchRetentionSeconds(int value) {
        this.terminalLaunchRetentionSeconds = value;
    }

    /**
     * Context 必须先于完整 Session 删除，确保“提前擦除”的配置语义不会被反转。
     *
     * @return 终态上下文{@code erasure}{@code ordered}条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "terminalContextRetentionSeconds must not exceed terminalSessionRetentionSeconds")
    public boolean isTerminalContextErasureOrdered() {
        return terminalContextRetentionSeconds <= terminalSessionRetentionSeconds;
    }

    /**
     * 生产 Embed 入口只能使用 HTTPS origin；HTTP 仅用于本机开发回环地址。
     * user-info、path、query 和 fragment 会改变边界语义，因此全部拒绝。
     *
     * @return 公开基础URL{@code secure}来源条件成立时为 true，否则为 false
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

    /**
     * 判断是否入口资产路径安全；判断结果决定调用方的后续分支。
     *
     * @return 入口资产路径安全条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "entryAssetPath must be a safe /embed-assets/ path")
    public boolean isEntryAssetPathSafe() {
        return safeEmbedAssetPath(entryAssetPath);
    }

    /**
     * 判断是否入口{@code style}路径安全；判断结果决定调用方的后续分支。
     *
     * @return 入口{@code style}路径安全条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "entryStylePath must be a safe /embed-assets/ path")
    public boolean isEntryStylePathSafe() {
        return safeEmbedAssetPath(entryStylePath);
    }

    /**
     * 点号可用于文件扩展名，但禁止独立的 {@code .}/{@code ..} 段、空段和反斜杠，
     * 防止稳定 Embed 资产路径逃逸专用静态目录。
     *
     * @param path 路径，供本方法处理安全嵌入式资产路径时使用
     * @return 安全嵌入式资产路径条件成立时为 true，否则为 false
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
