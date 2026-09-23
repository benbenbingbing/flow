package com.workflow.admin.dictionary.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典明细实体
 * <p>
 * 对应 sys_dict_item 表，存储字典项的编码、标签、值及树形结构（parentId），支持状态启用/禁用。
 * </p>
 */
@Data
@TableName("sys_dict_item")
public class SysDictItem {

    /** 主键ID（雪花算法分配） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属字典ID
     */
    private String dictId;

    /**
     * 冗余：字典编码（便于直接查询）
     */
    private String dictCode;

    /**
     * 父项ID，0表示顶级
     */
    private String parentId;

    /**
     * 项编码
     */
    private String itemCode;

    /**
     * 项标签/显示文本
     */
    private String itemLabel;

    /**
     * 项值
     */
    private String itemValue;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态：0-启用 1-禁用
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 子项列表（非数据库字段，树形回显用）
     */
    @TableField(exist = false)
    private List<SysDictItem> children;

    /**
     * 定义状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum Status {
        /** 启用 */
        ENABLED("0"),
        /** 禁用 */
        DISABLED("1");

        private final String value;

        /**
         * 初始化状态，保存构造参数供后续方法使用。
         *
         * @param value 值依赖，保存到当前对象供后续业务方法调用
         */
        Status(String value) {
            this.value = value;
        }

        /**
         * 读取值；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的值文本，供调用方比较或展示
         */
        public String getValue() {
            return value;
        }
    }
}
