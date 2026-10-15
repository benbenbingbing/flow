package com.workflow.contracts.entity.list.model;

/**
 * {@code EntityListDataProvider} 查询 Map 中的平台固定字段名。
 *
 * <p>{@link #FILTERS} 对应的过滤内容仍由实体和列表配置动态定义。</p>
 */
public final class EntityListQueryFields {

    /**
     * 从 1 开始的目标页码；Provider 用它决定返回哪一页记录。
     */
    public static final String PAGE_NUM = "pageNum";

    /**
     * 单页记录数；Provider 应结合自身上限控制实际查询规模。
     */
    public static final String PAGE_SIZE = "pageSize";

    /**
     * 列表运行时传入的过滤条件集合。
     *
     * <p>这里只固定外层字段名，内部字段、操作符和值由实体与列表配置决定，
     * 因此继续保持动态结构。</p>
     */
    public static final String FILTERS = "filters";

    /** 已验证可排序的实体字段编码；Provider 应在分页前排序，不能对单页结果重排。 */
    public static final String SORT_FIELD = "sortField";

    /** SORT_FIELD 对应的 ASC/DESC 方向；两项未提供时保持 Provider 原有默认顺序。 */
    public static final String SORT_DIRECTION = "sortDirection";

    /**
     * 初始化实体列表查询字段，保存构造参数供后续方法使用。
     */
    private EntityListQueryFields() {
    }
}
