package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程发布版本与节点 UI 发布快照的规范化绑定。
 */
@Data
@TableName("process_ui_release_binding")
public class ProcessUiReleaseBinding {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String processVersionHistoryId;
    private String processConfigId;
    private String processKey;
    private Integer processVersion;
    private String deploymentId;
    private String nodeId;
    private String nodeName;
    private String configType;
    private String configId;
    private String pinnedReleaseId;
    private Integer pinnedReleaseVersion;
    private LocalDateTime createTime;
}
