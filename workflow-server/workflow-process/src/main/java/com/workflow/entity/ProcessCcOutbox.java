package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抄送消息发送发件箱实体
 * 用于异步可靠投递抄送通知，配合重试机制保证消息最终送达
 */
@Data
@TableName("process_cc_outbox")
public class ProcessCcOutbox {
    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 关联的抄送记录ID */
    private String ccRecordId;
    /** 发送渠道（如站内信、邮件、钉钉等） */
    private String channel;
    /** 发送内容载荷（JSON） */
    private String payload;
    /** 发送状态：PENDING-待发送，SENDING-发送中，SUCCESS-成功，FAILED-失败 */
    private String status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;
    /** 失败错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 实际发送成功时间 */
    private LocalDateTime sentTime;
}
