package com.workflow.entity.form.application.port;

import java.util.List;
import java.util.Map;

/**
 * 表单唯一规则读取实体记录的应用端口。
 *
 * <p>端口只返回未删除记录，并将动态表列还原为字段编码；是否满足条件以及值如何
 * 规范化均由领域规则服务判断，避免预检与最终校验产生语义漂移。</p>
 */
public interface FormUniqueConflictQuery {

    /**
     * 查询单条未删除记录；记录不存在时返回空 Map。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 记录键值结果，供调用方继续处理
     */
    Map<String, Object> findRecord(
            String entityCode,
            String recordId);

    /**
     * 查询可能与当前规范化值冲突的未删除候选记录。
     *
     * <p>基础设施应尽量先按唯一字段和值缩小结果集；条件表达式仍由应用层统一
     * 求值，避免把版本化表单规则拼接成动态 SQL。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于查询候选集合时定位或关联目标
     * @param normalizedValue 规范化值，供本方法查询候选集合时使用
     * @param excludeRecordId 排除记录ID，后续用于查询候选集合时定位或关联目标
     * @return 表单唯一冲突查询集合，供调用方遍历或展示
     */
    List<Map<String, Object>> findCandidates(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId);

    /**
     * 为事务内权威写前终检查询可能冲突的未删除候选记录。
     *
     * <p>调用方已经按 entity/field/value 获取稳定 gate，基础设施必须执行
     * MySQL current/locking read，不能复用普通一致性读，否则外层事务为
     * REPEATABLE READ 时可能看不到刚在 gate 前提交的并发记录。调用方必须
     * 在任何业务行锁/写入前完成本查询，防止业务表锁序反转。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于查询候选集合{@code authoritative}检查时定位或关联目标
     * @param normalizedValue 规范化值，供本方法查询候选集合{@code authoritative}检查时使用
     * @param excludeRecordId 排除记录ID，后续用于查询候选集合{@code authoritative}检查时定位或关联目标
     * @return 表单唯一冲突查询集合，供调用方遍历或展示
     */
    List<Map<String, Object>> findCandidatesForAuthoritativeCheck(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId);
}
