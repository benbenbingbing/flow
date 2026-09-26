package com.workflow.contracts.embed.error;

/**
 * 模块无关的 Embed 边界失败投影，使 Open API 可以保留业务状态码而无需依赖 Embed 实现模块。
 */
public interface EmbedBoundaryFailure {

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @return 处理后的状态结果，供调用方继续处理
     */
    int status();

    /**
     * 生成错误编码文本，供后续匹配或展示。
     *
     * @return 处理后的错误编码文本，供调用方比较或展示
     */
    String errorCode();

    /**
     * 处理重试之后秒数，并将结果传给后续步骤。
     *
     * @return 处理后的重试之后秒数结果，供调用方继续处理
     */
    Long retryAfterSeconds();
}
