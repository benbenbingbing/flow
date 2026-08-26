package com.workflow.entity.data.application;

import com.workflow.contracts.entity.list.DataScopePlan;
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

    /** 执行一页最小投影查询。 */
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

        public ProjectionQuery {
            projectedFields = projectedFields == null
                    ? List.of() : List.copyOf(projectedFields);
            predicateValues = predicateValues == null
                    ? List.of() : List.copyOf(predicateValues);
        }
    }

    /** 一条最小记录投影。 */
    record ProjectionRow(
            String recordId,
            Map<String, Object> linkValues) {

        public ProjectionRow {
            linkValues = linkValues == null
                    ? Map.of() : Map.copyOf(linkValues);
        }
    }

    /** 分页投影结果。 */
    record ProjectionPage(
            List<ProjectionRow> rows,
            long total,
            long pageNum,
            long pageSize) {

        public ProjectionPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}
