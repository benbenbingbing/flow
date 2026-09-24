package com.workflow.admin.identity.user.infrastructure.persistence.record;

import lombok.Data;

/** 显示名称的最小投影，lookupKey 保留请求别名，使数据库排序规则下的匹配结果能准确回填。 */
@Data
public class UserDisplayNameRow {
    private String lookupKey;
    private String username;
    private String nickname;
    /** 同一输入同时匹配用户名和另一人的 ID 时，沿用单条查询的用户名优先规则。 */
    private Integer matchRank;
}
