package com.workflow.admin.identity.provisioning.application;

import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import lombok.RequiredArgsConstructor;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 来源定位与数据库目录锁；所有写入路径共享锁，防止并发建树和首次创建产生交叉状态。 */
@Component
@RequiredArgsConstructor
public class IdentitySourceStore {
    private final IdentityPersistenceMapper persistence;

    /** 锁必须属于外层业务事务，不能以独立连接或方法返回时释放。 */
    public void lockDirectory() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("身份写入必须位于数据库事务中");
        }
        persistence.lockDirectory();
    }
    public Map<String, Object> find(IdentityObjectType type, ExternalIdentityKey key) {
        return persistence.findBinding(hash(type, key));
    }
    public Map<String, Object> findLocal(IdentityObjectType type, String localId) {
        return persistence.findLocal(type, localId);
    }
    public String resolveOrganization(String source, String externalId) {
        if (externalId == null) return null;
        var row = find(IdentityObjectType.ORGANIZATION, new ExternalIdentityKey(source, externalId));
        if (row == null || !"ACTIVE".equals(row.get("binding_status"))) {
            throw new IdentityWriteException("REFERENCE_NOT_FOUND", "organization", "引用的来源组织不存在或已退休");
        }
        return (String) row.get("local_id");
    }
    public IdentitySourceInfo info(IdentityObjectType type, String localId) {
        var row = findLocal(type, localId);
        return row == null ? null : new IdentitySourceInfo((String) row.get("source_code"),
                (String) row.get("external_account"), flag(row.get("source_enabled")), flag(row.get("local_allowed")));
    }
    /** 管理员停用是独立闸门，来源再次启用时不得覆盖。调用前需持有目录事务锁。 */
    public String localStatus(IdentityObjectType type, String localId, String requested) {
        var row = findLocal(type, localId);
        if (row == null) return requested;
        if (!Set.of("0", "1").contains(requested)) throw new IdentityWriteException("INVALID_MODEL", "status", "状态非法");
        boolean allowed = "0".equals(requested);
        persistence.localStatus((String) row.get("source_key"), allowed);
        return allowed && flag(row.get("source_enabled")) ? "0" : "1";
    }
    public void retire(IdentityObjectType type, String localId) {
        persistence.retire(type, localId);
    }
    public static boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }
    public static String hash(IdentityObjectType type, ExternalIdentityKey key) {
        return digest(type + "\0" + key.sourceCode() + "\0" + key.externalId());
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
