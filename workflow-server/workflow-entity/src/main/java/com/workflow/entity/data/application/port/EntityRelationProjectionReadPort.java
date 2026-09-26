package com.workflow.entity.data.application.port;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;

import java.util.List;
import java.util.Map;

/**
 * 关系图专用的最小只读投影端口。
 *
 * <p>端口只允许读取记录 ID 与调用方从钉定实体发布快照解析出的链接字段，
 * 不返回完整业务记录，也不读取当前字段定义。所有查询必须携带服务端计算的
 * {@link DataScopePlan}。</p>
 */
public interface EntityRelationProjectionReadPort {

    /**
     * 执行一页最小投影查询。
     *
     * @param query 查询，供本方法读取实体关系投影读取分页时使用
     * @return 读取后的实体关系投影读取分页结果，供调用方继续处理
     */
    ProjectionPage readPage(ProjectionQuery query);

    /** 查询谓词类型。 */
    enum PredicateType {
        ID_IN,
        LINK_IN
    }

    /**
     * 关系图投影查询。
     *
     * @param entityCode        钉定实体编码
     * @param projectedFields   需要返回的链接字段，通常最多两个
     * @param predicateType     按 ID 或链接字段筛选
     * @param predicateField    LINK_IN 时必填的钉定链接字段
     * @param predicateValues   服务端遍历得到的记录 ID
     * @param dataScopePlan     当前用户逐跳数据范围
     * @param pageNum           页码
     * @param pageSize          每页记录数
     * @param maxMultiValues    本次查询允许装载的多值链接总数
     */
    record ProjectionQuery(
            String entityCode,
            List<LinkField> projectedFields,
            PredicateType predicateType,
            LinkField predicateField,
            List<String> predicateValues,
            DataScopePlan dataScopePlan,
            long pageNum,
            long pageSize,
            int maxMultiValues) {

        /**
         * 初始化投影查询，保存构造参数供后续方法使用。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param projectedFields {@code projected}字段，保存在对象中供后续校验、查询或展示
         * @param predicateType 判断条件类型标识，决定后续投影查询采用的处理分支
         * @param predicateField 判断条件字段，保存在对象中供后续校验、查询或展示
         * @param predicateValues 判断条件值集合，保存在对象中供后续校验、查询或展示
         * @param dataScopePlan 数据作用域方案，保存在对象中供后续校验、查询或展示
         * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
         * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
         * @param maxMultiValues 最大多实例值集合，保存在对象中供后续校验、查询或展示
         */
        public ProjectionQuery {
            projectedFields = projectedFields == null
                    ? List.of() : List.copyOf(projectedFields);
            predicateValues = predicateValues == null
                    ? List.of() : List.copyOf(predicateValues);
        }
    }

    /**
     * 一条最小记录投影。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param linkValues 链接值集合，保存在对象中供后续校验、查询或展示
     */
    record ProjectionRow(
            String recordId,
            Map<String, Object> linkValues) {

        /**
         * 初始化投影行，保存构造参数供后续方法使用。
         *
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         * @param linkValues 链接值集合，保存在对象中供后续校验、查询或展示
         */
        public ProjectionRow {
            linkValues = linkValues == null
                    ? Map.of() : Map.copyOf(linkValues);
        }
    }

    /**
     * 分页投影结果。
     *
     * @param rows 行，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    record ProjectionPage(
            List<ProjectionRow> rows,
            long total,
            long pageNum,
            long pageSize) {

        /**
         * 初始化投影分页，保存构造参数供后续方法使用。
         *
         * @param rows 行，保存在对象中供后续校验、查询或展示
         * @param total 总数，保存在对象中供后续校验、查询或展示
         * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
         * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
         */
        public ProjectionPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}
