package com.workflow.admin.identity.group.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 组内标签目录；流程持久化 id，名称仅用于展示，可以安全改名。 */
@Data
@TableName("sys_group_tag")
public class SysGroupTag {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 标签仅在此组内可用，不影响用户在其他组的审批身份。 */
    private String groupId;
    private String tagName;
    private LocalDateTime createTime;
}
