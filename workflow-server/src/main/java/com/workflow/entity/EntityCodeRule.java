package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体编码规则配置
 * 定义实体数据编码的生成规则
 */
@Data
@TableName("entity_code_rule")
public class EntityCodeRule {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 实体编码
     */
    @TableField("entity_code")
    private String entityCode;
    
    /**
     * 编码前缀，如：CG、DD
     */
    @TableField("prefix")
    private String prefix;
    
    /**
     * 日期格式，如：yyyyMMdd、yyyy-MM-dd
     */
    @TableField("date_format")
    private String dateFormat;
    
    /**
     * 序列号位数，如：6表示000001
     */
    @TableField("seq_length")
    private Integer seqLength;
    
    /**
     * 序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置
     */
    @TableField("seq_type")
    private String seqType;
    
    /**
     * 当前序列号值
     */
    @TableField("current_seq")
    private Integer currentSeq;
    
    /**
     * 当前序列号对应的日期（用于判断重置）
     */
    @TableField("seq_date")
    private String seqDate;
    
    /**
     * 编码示例
     */
    @TableField("example")
    private String example;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 序列号重置周期枚举
     */
    public enum SeqType {
        DAY,    // 按天重置
        MONTH,  // 按月重置
        YEAR,   // 按年重置
        NEVER   // 不重置
    }
    
    /**
     * 获取默认编码规则
     */
    public static EntityCodeRule getDefault(String entityCode) {
        EntityCodeRule rule = new EntityCodeRule();
        rule.setEntityCode(entityCode);
        rule.setPrefix(entityCode.toUpperCase());
        rule.setDateFormat("yyyyMMdd");
        rule.setSeqLength(6);
        rule.setSeqType(SeqType.DAY.name());
        rule.setCurrentSeq(0);
        rule.setSeqDate("");
        rule.setExample(rule.getPrefix() + "20240101" + "000001");
        return rule;
    }
}
