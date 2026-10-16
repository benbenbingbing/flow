package com.workflow.process.command.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 与实际流程变更一起提交的回执；PENDING 不得独立提交，SUCCESS 才能重放。 */
@Data
@TableName("process_command_receipt")
public class ProcessCommandRecord {
    /** 由调用范围和 requestId 计算的摘要主键，用数据库唯一约束协调并发重试。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 调用方持久化的业务意图键；新意图换键，失败重试复用原值。 */
    private String requestId;
    /** 用户身份或平台签发的动作范围，防止不同调用者读到彼此的结果。 */
    private String invocationScope;
    /** 稳定操作编码，参与载荷摘要并供历史查询辨别命令种类。 */
    private String commandType;
    /** 同一个请求键不能用于不同命令或载荷。 */
    private String commandHash;
    /** PENDING 与业务写入处于同一未提交事务；只有 SUCCESS 可返回给重试请求。 */
    private String status;
    private String processInstanceId;
    private String taskId;
    /** 首次执行是否实际变更；重放保留该值，由 replayed 字段另行标识重试。 */
    private Boolean changed;
    /** 固定公共结果的 JSON 快照；重放不再次调用引擎，也不从此字段反射加载任意类型。 */
    private String resultDocument;
    /** 统一以 UTC 保存，用于排序与保留期治理；不采用业务旧表的本地时区约定。 */
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
