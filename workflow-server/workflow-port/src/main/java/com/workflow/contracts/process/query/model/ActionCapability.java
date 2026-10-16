package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 操作可用性提示；不能缓存后作为命令授权凭据。
 * @param allowed 当前上下文是否允许
 * @param reasonCode 不可用原因编码
 * @param message 可读说明
 */
public record ActionCapability(
        boolean allowed,
        String reasonCode,
        String message) {

}
