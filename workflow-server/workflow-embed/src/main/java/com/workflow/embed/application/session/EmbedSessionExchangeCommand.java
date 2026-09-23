package com.workflow.embed.application.session;

/**
 * Browser exchange input bound to the one-time Launch and postMessage channel.
 *
 * @param launchId 启动记录ID，后续用于处理嵌入式会话交换命令时定位或关联目标
 * @param launchCode 启动记录编码，后续用于处理嵌入式会话交换命令时定位或关联目标
 * @param channelId 通道ID，后续用于处理嵌入式会话交换命令时定位或关联目标
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param parentNonce 父级{@code nonce}，保存在对象中供后续校验、查询或展示
 * @param childNonce 子级{@code nonce}，保存在对象中供后续校验、查询或展示
 * @param sdkVersion {@code sdk}版本，保存在对象中供后续校验、查询或展示
 * @param peerAddress {@code peer}地址，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionExchangeCommand(
        String launchId,
        String launchCode,
        String channelId,
        String parentOrigin,
        String parentNonce,
        String childNonce,
        String sdkVersion,
        String peerAddress) {

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "EmbedSessionExchangeCommand[launchId=" + launchId
                + ", launchCode=<redacted>, channelId=" + channelId
                + ", parentOrigin=" + parentOrigin
                + ", parentNonce=<redacted>, childNonce=<redacted>"
                + ", sdkVersion=" + sdkVersion + ", peerAddress=<redacted>]";
    }
}
