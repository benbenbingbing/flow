package com.workflow.entity.permission.api.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表在没有绑定 ALLOW 规则时使用的安全默认策略。
 */
@Data
public class EntityListScopeDefaultDTO {

    /** 列表稳定标识。 */
    private String listKey;

    /** DENY_ALL/PERSONAL/EXPLICIT_ALL。 */
    private String unboundPolicy;

    /** OBSERVE/ENFORCE；观察期只用于平滑迁移存量列表。 */
    private String enforcementMode;

    /** EXPLICIT_ALL 是否经过管理员显式确认。 */
    private Boolean confirmed;

    /** 确认人。 */
    private String confirmedBy;

    /** 确认时间。 */
    private LocalDateTime confirmedAt;

    /** 选择全量可见的业务原因。 */
    private String confirmationNote;
}
