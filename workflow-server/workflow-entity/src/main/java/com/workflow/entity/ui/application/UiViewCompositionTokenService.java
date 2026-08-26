package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关联内容来源行与列表固定条件的短期签名令牌。
 *
 * <p>令牌绑定当前用户、宿主发布版本和关联内容编码。浏览器即使修改 recordId、
 * 目标发布或筛选条件也无法生成有效签名，服务端仍会在每次解析时重读来源记录。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionTokenService {

    private static final long TOKEN_TTL_SECONDS = 300L;
    public static final int MAX_TRAVERSAL_DEPTH = 8;
    private static final String SOURCE_ROW = "SOURCE_ROW";
    private static final String TARGET_LIST = "TARGET_LIST";
    private static final String CANDIDATE_LIST = "CANDIDATE_LIST";
    private static final String TRAVERSAL = "TRAVERSAL";

    private final ObjectMapper objectMapper;

    @Value("${ui.view-composition.secret:${ui.release-resolution.secret:${jwt.secret}}}")
    private String secret;

    public String issueSourceRow(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String recordId) {
        return issue(new Claims(
                SOURCE_ROW,
                ownerType,
                ownerId,
                releaseId,
                releaseVersion,
                compositionKey,
                sourceEntityCode,
                recordId,
                null,
                null,
                null,
                null,
                Map.of(),
                false,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                UserContext.getUserId(),
                now(),
                now() + TOKEN_TTL_SECONDS));
    }

    public String issueTargetList(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String recordId,
            String targetEntityCode,
            String targetContentId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            Map<String, Object> fixedFilters,
            boolean matchNone) {
        return issueListContext(
                TARGET_LIST,
                ownerType,
                ownerId,
                releaseId,
                releaseVersion,
                compositionKey,
                sourceEntityCode,
                recordId,
                targetEntityCode,
                targetContentId,
                targetReleaseId,
                targetReleaseVersion,
                fixedFilters,
                matchNone);
    }

    /**
     * 签发“选择候选记录建立关联”的专用列表令牌。
     *
     * <p>候选令牌与普通已关联列表令牌用途隔离。前者只能由动作服务根据已发布
     * 关系生成，并在 LINK 执行时再次校验；浏览器不能把普通列表上下文冒充为
     * 候选范围。</p>
     */
    public String issueCandidateList(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String recordId,
            String targetEntityCode,
            String targetContentId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            Map<String, Object> fixedFilters,
            boolean matchNone) {
        return issueListContext(
                CANDIDATE_LIST,
                ownerType,
                ownerId,
                releaseId,
                releaseVersion,
                compositionKey,
                sourceEntityCode,
                recordId,
                targetEntityCode,
                targetContentId,
                targetReleaseId,
                targetReleaseVersion,
                fixedFilters,
                matchNone);
    }

    public Claims verifySourceRow(String token) {
        Claims claims = verify(token);
        if (!SOURCE_ROW.equals(claims.purpose())) {
            throw forbidden("关联内容令牌用途不正确");
        }
        return claims;
    }

    public Claims verifyTargetList(String token) {
        Claims claims = verify(token);
        if (!TARGET_LIST.equals(claims.purpose())) {
            throw forbidden("关联内容令牌用途不正确");
        }
        return claims;
    }

    /** 校验专用候选列表令牌，普通已关联列表令牌不能通过。 */
    public Claims verifyCandidateList(String token) {
        Claims claims = verify(token);
        if (!CANDIDATE_LIST.equals(claims.purpose())) {
            throw forbidden("关联内容候选令牌用途不正确");
        }
        return claims;
    }

    /**
     * 列表 schema/query 读取入口可接受普通已关联列表或候选列表令牌；动作执行
     * 仍必须分别调用严格用途校验方法，避免两种授权范围互换。
     */
    public Claims verifyListContext(String token) {
        Claims claims = verify(token);
        if (!Set.of(TARGET_LIST, CANDIDATE_LIST)
                .contains(claims.purpose())) {
            throw forbidden("关联内容列表令牌用途不正确");
        }
        return claims;
    }

    private String issueListContext(
            String purpose,
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String recordId,
            String targetEntityCode,
            String targetContentId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            Map<String, Object> fixedFilters,
            boolean matchNone) {
        return issue(new Claims(
                purpose,
                ownerType,
                ownerId,
                releaseId,
                releaseVersion,
                compositionKey,
                sourceEntityCode,
                recordId,
                targetEntityCode,
                targetContentId,
                targetReleaseId,
                targetReleaseVersion,
                fixedFilters == null
                        ? Map.of()
                        : Map.copyOf(new LinkedHashMap<>(fixedFilters)),
                matchNone,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                UserContext.getUserId(),
                now(),
                now() + TOKEN_TTL_SECONDS));
    }

    /**
     * 校验上一层遍历令牌确实允许进入当前钉定资产和记录。
     *
     * <p>该授权仅允许解析父层已经固定的目标发布；宿主、实体和记录权限仍由
     * 运行时服务重新校验。LIST 目标不会预先绑定某一行，FORM 目标则会绑定
     * 解析出的唯一目标记录。</p>
     */
    public void verifyTraversalEntry(
            String token,
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String recordId) {
        Claims claims = verifyTraversal(token);
        TraversalHop current = new TraversalHop(
                requireText(ownerType, "宿主类型"),
                requireText(ownerId, "宿主 ID"),
                requireText(releaseId, "宿主发布版本"),
                requirePositiveVersion(releaseVersion),
                requireText(compositionKey, "关联内容编码"),
                requireText(recordId, "来源记录 ID"));
        boolean recordMatches = !StringUtils.hasText(claims.nextRecordId())
                || claims.nextRecordId().equals(recordId);
        if (!current.ownerType().equals(claims.nextOwnerType())
                || !current.ownerId().equals(claims.nextOwnerId())
                || !current.releaseId().equals(claims.nextReleaseId())
                || !current.releaseVersion().equals(
                claims.nextReleaseVersion())
                || !recordMatches) {
            throw forbidden("关联内容遍历令牌与当前目标版本或记录不一致");
        }
        List<TraversalHop> hops = claims.traversal() == null
                ? List.of() : claims.traversal();
        if (hops.contains(current)) {
            throw reentryBlocked();
        }
        if (hops.size() >= MAX_TRAVERSAL_DEPTH) {
            throw depthExceeded();
        }
    }

    /**
     * 将当前解析追加到短期签名遍历链，并固定允许进入的下一层目标。
     *
     * <p>遍历身份同时包含资产、精确发布版本、关联内容编码和来源记录。
     * 同一业务页面可以打开同一资产的不同记录或不同关联内容，但导航不能
     * 再次进入完全相同的运行时节点；链路总深度最多八层。令牌只承担递归
     * 保护和精确下一跳传递，不替代实体或记录权限校验。</p>
     */
    public String advanceTraversal(
            String previousToken,
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String recordId,
            String nextOwnerType,
            String nextOwnerId,
            String nextReleaseId,
            Integer nextReleaseVersion,
            String nextRecordId) {
        TraversalHop next = new TraversalHop(
                requireText(ownerType, "宿主类型"),
                requireText(ownerId, "宿主 ID"),
                requireText(releaseId, "宿主发布版本"),
                requirePositiveVersion(releaseVersion),
                requireText(compositionKey, "关联内容编码"),
                requireText(recordId, "来源记录 ID"));
        List<TraversalHop> hops = new ArrayList<>();
        if (StringUtils.hasText(previousToken)) {
            verifyTraversalEntry(
                    previousToken,
                    ownerType,
                    ownerId,
                    releaseId,
                    releaseVersion,
                    compositionKey,
                    recordId);
            Claims previous = verifyTraversal(previousToken);
            if (previous.traversal() != null) {
                hops.addAll(previous.traversal());
            }
        }
        if (hops.contains(next)) {
            throw reentryBlocked();
        }
        if (hops.size() >= MAX_TRAVERSAL_DEPTH) {
            throw depthExceeded();
        }
        hops.add(next);
        return issue(new Claims(
                TRAVERSAL,
                next.ownerType(),
                next.ownerId(),
                next.releaseId(),
                next.releaseVersion(),
                next.compositionKey(),
                null,
                next.recordId(),
                null,
                null,
                null,
                null,
                Map.of(),
                false,
                requireText(nextOwnerType, "下一层目标类型"),
                requireText(nextOwnerId, "下一层目标 ID"),
                requireText(nextReleaseId, "下一层目标发布版本"),
                requirePositiveVersion(nextReleaseVersion),
                StringUtils.hasText(nextRecordId)
                        ? nextRecordId.trim() : null,
                List.copyOf(hops),
                UserContext.getUserId(),
                now(),
                now() + TOKEN_TTL_SECONDS));
    }

    private Claims verifyTraversal(String token) {
        Claims claims = verify(token);
        if (!TRAVERSAL.equals(claims.purpose())) {
            throw forbidden("关联内容遍历令牌用途不正确");
        }
        return claims;
    }

    private BusinessConflictException reentryBlocked() {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RUNTIME_REENTRY_BLOCKED",
                "关联内容导航再次进入同一内容和记录，已停止加载以避免循环。"
                        + "请返回上一层，或改用其他记录。");
    }

    private BusinessConflictException depthExceeded() {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RUNTIME_DEPTH_EXCEEDED",
                "关联内容导航已达到 " + MAX_TRAVERSAL_DEPTH
                        + " 层，已停止继续加载。请返回上一层后再打开其他内容。");
    }

    private String issue(Claims claims) {
        requireSecret();
        if (!StringUtils.hasText(claims.userId())) {
            throw forbidden("关联内容令牌缺少当前用户");
        }
        try {
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            return payload + "." + sign(payload);
        } catch (BusinessForbiddenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("关联内容令牌签发失败", exception);
        }
    }

    private Claims verify(String token) {
        requireSecret();
        if (!StringUtils.hasText(token)) {
            throw forbidden("关联内容令牌不能为空");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw forbidden("关联内容令牌格式不正确");
        }
        try {
            if (!MessageDigest.isEqual(
                    sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw forbidden("关联内容令牌签名无效");
            }
            Claims claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    Claims.class);
            if (claims.expiresAt() < now()) {
                throw forbidden("关联内容令牌已过期");
            }
            if (!StringUtils.hasText(claims.userId())
                    || !claims.userId().equals(UserContext.getUserId())) {
                throw forbidden("关联内容令牌不属于当前用户");
            }
            return claims;
        } catch (BusinessForbiddenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw forbidden("关联内容令牌无法解析");
        }
    }

    private void requireSecret() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("关联内容令牌密钥未配置");
        }
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(
                        payload.getBytes(StandardCharsets.UTF_8)));
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private BusinessForbiddenException forbidden(String message) {
        return new BusinessForbiddenException(
                "INVALID_VIEW_COMPOSITION_TOKEN",
                message);
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private Integer requirePositiveVersion(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("宿主发布版本号必须为正整数");
        }
        return value;
    }

    public record Claims(
            String purpose,
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String sourceRecordId,
            String targetEntityCode,
            String targetContentId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            Map<String, Object> fixedFilters,
            boolean matchNone,
            String nextOwnerType,
            String nextOwnerId,
            String nextReleaseId,
            Integer nextReleaseVersion,
            String nextRecordId,
            List<TraversalHop> traversal,
            String userId,
            long issuedAt,
            long expiresAt) {
    }

    /** 单个已解析运行时节点，字段组合用于精确判定重入。 */
    public record TraversalHop(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String recordId) {
    }
}
