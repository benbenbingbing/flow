package com.workflow.contracts.ui;

/**
 * 平台内置 UI 数据源绑定位置编码。
 *
 * <p>扩展实现仍可使用自定义字符串；此类只集中平台已经定义的标准值，不构成封闭枚举。</p>
 */
public final class UiDataSourceUsages {

    /** 表单创建运行时模型时执行，结果可用于初始化表单字段。 */
    public static final String FORM_INIT = "FORM_INIT";

    /** 字段需要动态候选项时执行，结果应为选项集合。 */
    public static final String FIELD_OPTIONS = "FIELD_OPTIONS";

    /** 新建表单计算字段初始值时执行，结果作为字段默认值。 */
    public static final String FIELD_DEFAULT = "FIELD_DEFAULT";

    /** 字段依赖值变化后重新计算时执行，结果作为新的字段值。 */
    public static final String FIELD_COMPUTE = "FIELD_COMPUTE";

    /** 子表单需要动态装载行数据时执行，结果应为行集合。 */
    public static final String SUBFORM_ROWS = "SUBFORM_ROWS";

    /** 列表查询时执行，Provider 结果替代平台默认分页查询结果。 */
    public static final String LIST_QUERY = "LIST_QUERY";

    /** 列表渲染扩展列时执行，结果按记录 ID 提供对应列值。 */
    public static final String LIST_COLUMN = "LIST_COLUMN";

    /** 表单记录及初始字段准备完成后执行，结果可继续回填字段。 */
    public static final String AFTER_LOAD = "AFTER_LOAD";

    /** 表单提交落库前执行，用于服务端校验、转换或补充提交数据。 */
    public static final String BEFORE_SUBMIT = "BEFORE_SUBMIT";

    /** 列表页面完成数据装载时触发的标准事件。 */
    public static final String LIST_LOAD = "LIST_LOAD";

    /** 用户发起列表导出时触发的标准事件。 */
    public static final String LIST_EXPORT = "LIST_EXPORT";

    /** 实体详情数据装载时触发的标准事件。 */
    public static final String DETAIL_LOAD = "DETAIL_LOAD";

    /** 实体记录创建流程触发的标准写事件。 */
    public static final String DATA_CREATE = "DATA_CREATE";

    /** 实体记录更新流程触发的标准写事件。 */
    public static final String DATA_UPDATE = "DATA_UPDATE";

    /** 单条实体记录删除流程触发的标准写事件。 */
    public static final String DATA_DELETE = "DATA_DELETE";

    /** 多条实体记录批量删除流程触发的标准写事件。 */
    public static final String DATA_BATCH_DELETE =
            "DATA_BATCH_DELETE";

    /** 表单被打开时触发的界面标准事件。 */
    public static final String FORM_OPEN = "FORM_OPEN";

    /** 用户发起表单保存时触发的界面标准事件。 */
    public static final String FORM_SAVE = "FORM_SAVE";

    /** 用户重置表单时触发的界面标准事件。 */
    public static final String FORM_RESET = "FORM_RESET";

    /** 字段值发生变化时触发，可用于联动校验或字段回填。 */
    public static final String FIELD_CHANGE = "FIELD_CHANGE";

    /** 实体选择组件确认记录时触发，可用于读取并回填关联数据。 */
    public static final String ENTITY_SELECTED =
            "ENTITY_SELECTED";

    /** 字段旁自定义按钮被点击时触发。 */
    public static final String FIELD_BUTTON_CLICK =
            "FIELD_BUTTON_CLICK";

    /** 子表单开始装载时触发的标准事件。 */
    public static final String SUBFORM_LOAD = "SUBFORM_LOAD";

    /** 子表单行保存时触发的标准写事件。 */
    public static final String SUBFORM_SAVE = "SUBFORM_SAVE";

    /** 列表工具栏自定义按钮被点击时触发。 */
    public static final String TOOLBAR_BUTTON_CLICK =
            "TOOLBAR_BUTTON_CLICK";

    /** 列表行级自定义按钮被点击时触发。 */
    public static final String ROW_BUTTON_CLICK =
            "ROW_BUTTON_CLICK";

    /** 表单操作栏自定义按钮被点击时触发。 */
    public static final String FORM_BUTTON_CLICK =
            "FORM_BUTTON_CLICK";

    /**
     * 实体变更写入前的服务端内部阶段，用于执行受管理的预处理操作。
     *
     * <p>该 usage 不代表浏览器事件，运行时会按实体变更权限和作用域单独鉴权。</p>
     */
    public static final String ENTITY_MUTATION_PREPARE =
            "ENTITY_MUTATION_PREPARE";

    private UiDataSourceUsages() {
    }
}
