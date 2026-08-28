package com.workflow.contracts.embed;

/**
 * 模块无关的 Embed 边界失败投影，使 Open API 可以保留业务状态码而无需依赖 Embed 实现模块。
 */
public interface EmbedBoundaryFailure {

    int status();

    String errorCode();

    Long retryAfterSeconds();
}
