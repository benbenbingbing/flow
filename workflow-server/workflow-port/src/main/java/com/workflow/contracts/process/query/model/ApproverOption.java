package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 最小审批人展示信息，不暴露用户敏感字段。
 * @param userId 用户 ID
 * @param username 平台兼容身份名称
 * @param displayName 用于展示的名称
 */
public record ApproverOption(
        String userId,
        String username,
        String displayName) {

}
