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

    /** 查询单条未删除记录；记录不存在时返回空 Map。 */
    Map<String, Object> findRecord(
            String entityCode,
            String recordId);

    /**
     * 查询可能与当前规范化值冲突的未删除候选记录。
     *
     * <p>基础设施应尽量先按唯一字段和值缩小结果集；条件表达式仍由应用层统一
     * 求值，避免把版本化表单规则拼接成动态 SQL。</p>
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
     */
    List<Map<String, Object>> findCandidatesForAuthoritativeCheck(
            String entityCode,
            String fieldCode,
            String normalizedValue,
            String excludeRecordId);
}
