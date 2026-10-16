package com.workflow.contracts.process.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 一次业务意图的标识；重试复用，同一意图的不同步骤使用不同 ID。
 * @param requestId 幂等请求 ID，不是当前操作人或授权凭据
 */
public record CommandMeta(
        String requestId) {
    public CommandMeta {
        requestId = ProcessValues.required(requestId, "requestId");
        if (requestId.length() > 200) throw new IllegalArgumentException("requestId 最长 200 字符"); }

}
