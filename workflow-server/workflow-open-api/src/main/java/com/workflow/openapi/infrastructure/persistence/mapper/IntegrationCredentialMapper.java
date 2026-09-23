package com.workflow.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationCredentialMapper
        extends BaseMapper<IntegrationApplicationCredentialRecord> {

    /** 查询应用首条活动凭据，由分页插件保留单行数量限制。 */
    default IntegrationApplicationCredentialRecord findActive(String applicationId) {
        return selectPage(new Page<IntegrationApplicationCredentialRecord>(1, 1, false),
                Wrappers.<IntegrationApplicationCredentialRecord>lambdaQuery()
                        .eq(IntegrationApplicationCredentialRecord::getApplicationId, applicationId)
                        .eq(IntegrationApplicationCredentialRecord::getStatus, "ACTIVE"))
                .getRecords().stream().findFirst().orElse(null);
    }

    /** 批量读取指定应用的活动凭据；空集合必须返回空结果，不能退化为所有应用。 */
    default List<IntegrationApplicationCredentialRecord> findActiveByApplicationIds(List<String> applicationIds) {
        return applicationIds == null || applicationIds.isEmpty() ? List.of()
                : selectList(Wrappers.<IntegrationApplicationCredentialRecord>lambdaQuery()
                        .eq(IntegrationApplicationCredentialRecord::getStatus, "ACTIVE")
                        .in(IntegrationApplicationCredentialRecord::getApplicationId, applicationIds)
                        .orderByAsc(IntegrationApplicationCredentialRecord::getApplicationId));
    }

    /** 历史和已撤销凭据仍参与最大版本计算，保证重新签发不会复用旧版本；无记录返回 0。 */
    default long findLatestVersion(String applicationId) {
        List<Object> values = selectObjs(Wrappers.<IntegrationApplicationCredentialRecord>query()
                .select("MAX(credential_version)").eq("application_id", applicationId));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).longValue();
    }

    /** 单条 UPDATE 撤销应用仍处于活动状态的凭据，保留原子状态筛选和 null 赋值语义。 */
    default int revokeActive(String applicationId, String operatorId, LocalDateTime now) {
        return update(null, Wrappers.<IntegrationApplicationCredentialRecord>lambdaUpdate()
                .set(IntegrationApplicationCredentialRecord::getStatus, "REVOKED")
                .set(IntegrationApplicationCredentialRecord::getRevokedBy, operatorId)
                .set(IntegrationApplicationCredentialRecord::getRevokedAt, now)
                .eq(IntegrationApplicationCredentialRecord::getApplicationId, applicationId)
                .eq(IntegrationApplicationCredentialRecord::getStatus, "ACTIVE"));
    }

    /**
     * 同一条语句更新指定启用应用的全部活动凭据，避免先查询再更新产生状态竞争。
     * 子查询只限定应用范围；保留 null clientId 不匹配、null now 清空使用时间的原有语义。
     */
    @Update("""
            UPDATE integration_application_credential
               SET last_used_at = #{now,jdbcType=TIMESTAMP}
             WHERE status = 'ACTIVE'
               AND application_id IN (
                   SELECT id FROM integration_application
                    WHERE client_id = #{clientId,jdbcType=VARCHAR}
                      AND status = 'ACTIVE'
               )
            """)
    int markActiveUsedByClientId(
            @Param("clientId") String clientId,
            @Param("now") LocalDateTime now);
}
