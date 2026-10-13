package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Excel 文件请求的短期签名上下文；签名不替代实时权限与发布摘要校验。 */
@Service
public class ExcelActionTokenService {
    private final ObjectMapper mapper;
    private final byte[] secret;

    public ExcelActionTokenService(ObjectMapper mapper,
            @Value("${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}") String secret) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 固定 15 分钟有效期，不信任浏览器传入的用户身份和操作集合。 */
    public String issue(Claims claims) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(claims));
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        } catch (Exception ex) { throw new IllegalStateException("Excel执行上下文签发失败", ex); }
    }

    /** 验证签名、当前用户和操作，任何不完整或过期令牌均拒绝。 */
    public Claims verify(String token, String operation) {
        try {
            if (token == null || token.length() > 1_000_000) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            Claims value = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Claims.class);
            if (value.version() != 1 || value.expiresAt() <= Instant.now().getEpochSecond()
                    || UserContext.getUserId() == null || !UserContext.getUserId().equals(value.userId())
                    || !validClaims(value) || !value.allowedOperations().contains(operation))
                throw new IllegalArgumentException();
            return value;
        } catch (Exception ex) { throw new ForbiddenException("Excel执行上下文无效、已过期或操作不匹配，请重新点击按钮"); }
    }

    /** 签名只证明来源可信；仍拒绝缺失坐标或操作集合自相矛盾的上下文，避免调用方后续空指针或权限回退。 */
    private boolean validClaims(Claims claims) {
        UiEventExecuteRequest origin = claims.origin();
        if (origin == null || !Set.of("LIST", "FORM").contains(text(origin.getConfigType()))
                || !"BUTTON".equals(origin.getTargetType()) || text(origin.getConfigId()).isBlank()
                || text(origin.getTargetKey()).isBlank() || text(origin.getEntityCode()).isBlank()
                || !Set.of("BUILT_IN", "CUSTOM").contains(text(claims.entryType()))
                || !Set.of("DEFAULT", "EXPLICIT").contains(text(claims.referenceMode()))
                || text(claims.schemeCode()).isBlank() || text(claims.sourceHash()).isBlank()
                || text(claims.schemeHash()).isBlank() || text(claims.entityHash()).isBlank()) return false;
        Set<String> expected = switch (text(claims.operation())) {
            case "IMPORT" -> Set.of("IMPORT", "CHECK", "TEMPLATE");
            case "EXPORT" -> Set.of("EXPORT");
            case "TEMPLATE" -> Set.of("TEMPLATE");
            default -> Set.of();
        };
        if (expected.isEmpty() || claims.allowedOperations() == null || claims.allowedOperations().isEmpty()
                || !expected.containsAll(claims.allowedOperations())
                || !claims.allowedOperations().contains(claims.operation())) return false;
        String event = text(origin.getEventCode());
        if ("FORM".equals(origin.getConfigType()) ? !"FORM_BUTTON_CLICK".equals(event)
                : !Set.of("TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK").contains(event)) return false;
        if ("BUILT_IN".equals(claims.entryType()) && (!"LIST".equals(origin.getConfigType())
                || !"TOOLBAR_BUTTON_CLICK".equals(event) || !"DEFAULT".equals(claims.referenceMode()))) return false;
        return !"EXPORT".equals(claims.operation())
                || Set.of("CURRENT_RECORD", "SELECTED", "FILTERED").contains(text(claims.dataScope()));
    }

    private static String text(String value) { return value == null ? "" : value; }

    /** 对配置做稳定摘要，Map 排序消除数据库 JSON 键顺序差异。 */
    public String hash(Object value) {
        try { return hashBytes(mapper.writeValueAsBytes(mapper.convertValue(value, Object.class))); }
        catch (Exception ex) { throw new IllegalStateException("Excel配置摘要失败", ex); }
    }

    public static String hashBytes(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private byte[] sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        // 领域前缀隔离同一部署密钥下的其他令牌协议。
        return mac.doFinal(("entity-excel-action:v1:" + payload).getBytes(StandardCharsets.UTF_8));
    }

    /** origin 只包含服务端核验过的来源坐标；不得在日志中输出令牌或完整来源输入。 */
    public record Claims(int version, String userId, long expiresAt, String entryType,
            UiEventExecuteRequest origin, String sourceHash, String operation, String dataScope,
            String referenceMode, String schemeCode, String schemeHash, String defaultHash,
            String entityHash, String writeFormHash, List<String> allowedOperations,
            Map<String, Object> selectionSource) {
        /** 保留无列表选择弹窗的构造形式，已有默认按钮和测试不需要提供空来源。 */
        public Claims(int version, String userId, long expiresAt, String entryType, UiEventExecuteRequest origin,
                String sourceHash, String operation, String dataScope, String referenceMode, String schemeCode,
                String schemeHash, String defaultHash, String entityHash, String writeFormHash, List<String> allowedOperations) {
            this(version, userId, expiresAt, entryType, origin, sourceHash, operation, dataScope, referenceMode,
                    schemeCode, schemeHash, defaultHash, entityHash, writeFormHash, allowedOperations, Map.of());
        }
    }
}
