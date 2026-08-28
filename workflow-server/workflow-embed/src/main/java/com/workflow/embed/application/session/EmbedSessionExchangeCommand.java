package com.workflow.embed.application.session;

/** Browser exchange input bound to the one-time Launch and postMessage channel. */
public record EmbedSessionExchangeCommand(
        String launchId,
        String launchCode,
        String channelId,
        String parentOrigin,
        String parentNonce,
        String childNonce,
        String sdkVersion,
        String peerAddress) {

    @Override
    public String toString() {
        return "EmbedSessionExchangeCommand[launchId=" + launchId
                + ", launchCode=<redacted>, channelId=" + channelId
                + ", parentOrigin=" + parentOrigin
                + ", parentNonce=<redacted>, childNonce=<redacted>"
                + ", sdkVersion=" + sdkVersion + ", peerAddress=<redacted>]";
    }
}
