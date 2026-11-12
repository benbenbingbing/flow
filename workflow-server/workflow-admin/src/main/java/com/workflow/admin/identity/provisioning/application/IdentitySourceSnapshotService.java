package com.workflow.admin.identity.provisioning.application;

import com.fasterxml.jackson.core.*;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentitySourceSnapshotMapper;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.record.IdentitySourceSnapshot;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.*;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 来源原文只保留一份；接受写入时与基础资料共用目录锁、事务和必需审计。 */
@Service
@RequiredArgsConstructor
public class IdentitySourceSnapshotService {
    private final IdentitySourceSnapshotMapper snapshots;
    private final IdentityWriteProperties properties;
    private final IdentityAccessService access;
    private final SystemAuditPort audit;
    // 独立严格解析器，不继承业务 ObjectMapper 的宽松 JSON 配置，也不把原文转换成可能丢精度的对象。
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    /** 入口资源限制也适用于旧版本；批次先整体检查，避免在部分写入后才发现累计超限。 */
    public void checkSizes(List<String> rawValues, boolean batch) {
        if (properties.getRawDataMaxBytes() <= 0 || properties.getRawDataMaxBatchBytes() <= 0) {
            throw new IllegalStateException("来源原文字节上限必须为正数");
        }
        long total = 0;
        for (int i = 0; i < rawValues.size(); i++) {
            String raw = rawValues.get(i);
            int bytes = raw == null ? 0 : raw.getBytes(StandardCharsets.UTF_8).length;
            total += bytes;
            if (bytes > properties.getRawDataMaxBytes() || batch && total > properties.getRawDataMaxBatchBytes()) {
                throw new IdentityWriteException("RAW_DATA_TOO_LARGE", batch ? "models[" + i + "].rawDataJson" : "rawDataJson",
                        "来源原文超过 UTF-8 字节上限");
            }
        }
    }

    /** 已通过版本判定的请求才解析原文，返回 null 表示调用方省略；错误消息不携带敏感原文。 */
    public String validate(String raw, IdentityAccessService.Writer writer) {
        if (raw == null) {
            if (writer.source().isRawDataRequired()) {
                throw new IdentityWriteException("RAW_DATA_REQUIRED", "rawDataJson", "该来源必须提供原始 JSON 对象");
            }
            return null;
        }
        try (var parser = JSON.createParser(raw)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException();
            parser.skipChildren();
            if (parser.nextToken() != null) throw new IllegalArgumentException();
        } catch (Exception e) {
            // 解析异常可能包含来源内容；只返回固定业务错误，不保留原始异常为 cause。
            throw new IdentityWriteException("INVALID_RAW_DATA", "rawDataJson", "来源原文必须是无重复字段的单个合法 JSON 对象");
        }
        return raw;
    }

    /**
     * 保存已接受的原文，包括业务 UNCHANGED 时的首次补录及原文独立变化。
     * @param businessChanged 无原文的业务更新须将旧原文标为过期；业务相同则保持原状态
     * @throws RuntimeException 数据库或必需审计失败时向外传播，由来源写入事务整体回滚
     */
    public void save(IdentityObjectType type, ExternalIdentityKey key, String id, Long version, String payloadHash,
                     String raw, IdentityAccessService.Writer writer, boolean businessChanged) {
        String sourceKey = IdentitySourceStore.hash(type, key);
        if (raw == null) {
            if (businessChanged) snapshots.markStale(sourceKey);
            return;
        }
        String hash = IdentitySourceStore.digest(raw);
        var previous = snapshots.metadata(sourceKey);
        if (previous != null && Objects.equals(previous.getRawHash(), hash)
                && Objects.equals(previous.getPayloadHash(), payloadHash)
                && Objects.equals(previous.getSourceVersion(), version) && Objects.equals(previous.getIsCurrent(), 1)) return;
        var snapshot = new IdentitySourceSnapshot();
        snapshot.setSourceKey(sourceKey); snapshot.setSourceVersion(version); snapshot.setPayloadHash(payloadHash);
        snapshot.setRawJson(raw); snapshot.setRawHash(hash);
        snapshot.setRawBytes(raw.getBytes(StandardCharsets.UTF_8).length); snapshot.setRecordedBy(writer.actorId());
        if (previous == null) snapshots.insert(snapshot); else snapshots.update(snapshot);
        audit.record(SystemAuditEvent.builder().operatorId(writer.actorId()).module(AuditModule.SYSTEM).action(AuditAction.UPSERT)
                .operationName("来源原文留存").riskLevel(AuditRiskLevel.HIGH).result(AuditResult.SUCCESS).required(true)
                .targetType(type == IdentityObjectType.USER ? "SYS_USER" : "SYS_ORGANIZATION").targetId(id)
                .summary("sourceKey=" + sourceKey + ", rawHash=" + hash + ", rawBytes=" + snapshot.getRawBytes()).build());
    }

    /**
     * 管理员按需查看原文：必须有登录上下文、对象查看及管理权限，并通过现有组织数据范围检查。
     * 来源免写权限配置和服务账号均不能绕过这里的读取授权。
     */
    public View read(IdentityObjectType target, String localId) {
        String actor = UserContext.getUserId();
        if (actor == null) throw new IdentityWriteException("FORBIDDEN", "actor", "查看来源原文需要登录");
        String permission = target == IdentityObjectType.USER ? "system:user:" : "system:organization:";
        access.requirePermission(actor, permission + "view");
        access.requirePermission(actor, permission + "manage");
        access.requireObject(target, localId);
        var row = snapshots.read(target, localId);
        if (row == null) return new View("UNAVAILABLE", null, null, null, null, null, null, null, null, null);
        String state = row.getRawJson() == null ? "UNAVAILABLE" : Objects.equals(row.getIsCurrent(), 1) ? "CURRENT" : "STALE";
        return new View(state, row.getSourceCode(), row.getExternalId(), version(row.getCurrentSourceVersion()),
                version(row.getSourceVersion()), row.getRecordedAt() == null ? null : row.getRecordedAt().toLocalDateTime().toString(),
                row.getRecordedBy(), row.getRawHash(), row.getRawBytes(), row.getRawJson());
    }
    private String version(Long value) { return value == null ? null : value.toString(); }

    /** 版本与原文均用字符串传输，浏览器无需解析 JSON 数字即可完整展示外部大整数。 */
    public record View(String state, String sourceCode, String externalId, String currentSourceVersion,
                       String recordedSourceVersion, String recordedAt, String recordedBy, String rawHash,
                       Integer rawBytes, String rawDataJson) { }
}
