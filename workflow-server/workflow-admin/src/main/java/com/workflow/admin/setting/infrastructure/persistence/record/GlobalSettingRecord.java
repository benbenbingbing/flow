package com.workflow.admin.setting.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 全局设置持久化记录；值始终按普通文本存取，恢复默认时物理删除记录。 */
@Data
@TableName("sys_global_setting")
public class GlobalSettingRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String scopeType;
    private String ownerId;
    private String settingKey;
    private String name;
    /** 用于格式校验的业务类型：BOOLEAN、NUMBER、STRING、JSON。 */
    private String settingValueType;
    /** JSON 仅为应用序列化协议，不依赖数据库 JSON 类型或类型处理器。 */
    @ToString.Exclude // 设置值可能包含密钥，禁止实体字符串表示意外泄漏明文。
    private String settingValue;
    private String remark;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
