package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * 实体编码规则配置
 * 定义实体数据编码的生成规则
 */
@Data
@TableName("entity_code_rule")
public class EntityCodeRule {

    /** 编码前缀最大长度 */
    public static final int MAX_PREFIX_LENGTH = 20;

    /** 主键ID */
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
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
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
        rule.setPrefix(defaultPrefix(entityCode));
        rule.setDateFormat("yyyyMMdd");
        rule.setSeqLength(6);
        rule.setSeqType(SeqType.DAY.name());
        rule.setCurrentSeq(0);
        rule.setSeqDate("");
        rule.setExample(rule.getPrefix() + "20240101" + "000001");
        return rule;
    }

    /**
     * 根据实体编码生成默认前缀。
     * 超过最大长度时截取并追加 CRC32 校验后缀以保证唯一性。
     *
     * @param entityCode 实体编码
     * @return 生成的默认前缀
     */
    public static String defaultPrefix(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return "";
        }

        String normalized = entityCode.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");
        if (normalized.length() <= MAX_PREFIX_LENGTH) {
            return normalized;
        }

        CRC32 checksum = new CRC32();
        checksum.update(entityCode.getBytes(StandardCharsets.UTF_8));
        String suffix = String.format(Locale.ROOT, "%08X", checksum.getValue());
        int readableLength = MAX_PREFIX_LENGTH - suffix.length() - 1;
        return normalized.substring(0, readableLength) + "_" + suffix;
    }
}
