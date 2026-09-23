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

    /**
     * 生成签发来源行文本，供后续匹配或展示。
     *
     * @param ownerType 归属方类型标识，决定后续签发来源行采用的处理分支
     * @param ownerId 归属方ID，后续用于处理签发来源行时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理签发来源行时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code issue} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理签发来源行时定位或关联目标
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的签发来源行文本，供调用方比较或展示
     */
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

    /**
     * 生成签发目标列表文本，供后续匹配或展示。
     *
     * @param ownerType 归属方类型标识，决定后续签发目标列表采用的处理分支
     * @param ownerId 归属方ID，后续用于处理签发目标列表时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理签发目标列表时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code issueListContext} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理签发目标列表时定位或关联目标
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param targetEntityCode 目标实体编码，后续用于处理签发目标列表时定位或关联目标
     * @param targetContentId 目标内容ID，后续用于处理签发目标列表时定位或关联目标
     * @param targetReleaseId 目标发布版本ID，后续用于处理签发目标列表时定位或关联目标
     * @param targetReleaseVersion 目标发布版本，供本方法处理签发目标列表时使用
     * @param fixedFilters 固定过滤条件，供本方法处理签发目标列表时使用
     * @param matchNone 匹配{@code none}，供本方法处理签发目标列表时使用
     * @return 处理后的签发目标列表文本，供调用方比较或展示
     */
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
     *
     * @param ownerType 归属方类型标识，决定后续签发候选人列表采用的处理分支
     * @param ownerId 归属方ID，后续用于处理签发候选人列表时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理签发候选人列表时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code issueListContext} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理签发候选人列表时定位或关联目标
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param targetEntityCode 目标实体编码，后续用于处理签发候选人列表时定位或关联目标
     * @param targetContentId 目标内容ID，后续用于处理签发候选人列表时定位或关联目标
     * @param targetReleaseId 目标发布版本ID，后续用于处理签发候选人列表时定位或关联目标
     * @param targetReleaseVersion 目标发布版本，供本方法处理签发候选人列表时使用
     * @param fixedFilters 固定过滤条件，供本方法处理签发候选人列表时使用
     * @param matchNone 匹配{@code none}，供本方法处理签发候选人列表时使用
     * @return 处理后的签发候选人列表文本，供调用方比较或展示
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

    /**
     * 验证来源行；不满足约束时阻止后续处理。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的来源行结果，供调用方继续处理
     */
    public Claims verifySourceRow(String token) {
        Claims claims = verify(token);
        if (!SOURCE_ROW.equals(claims.purpose())) {
            throw forbidden("关联内容令牌用途不正确");
        }
        return claims;
    }

    /**
     * 验证目标列表；不满足约束时阻止后续处理。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的目标列表结果，供调用方继续处理
     */
    public Claims verifyTargetList(String token) {
        Claims claims = verify(token);
        if (!TARGET_LIST.equals(claims.purpose())) {
            throw forbidden("关联内容令牌用途不正确");
        }
        return claims;
    }

    /**
     * 校验专用候选列表令牌，普通已关联列表令牌不能通过。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的候选人列表结果，供调用方继续处理
     */
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
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的列表上下文结果，供调用方继续处理
     */
    public Claims verifyListContext(String token) {
        Claims claims = verify(token);
        if (!Set.of(TARGET_LIST, CANDIDATE_LIST)
                .contains(claims.purpose())) {
            throw forbidden("关联内容列表令牌用途不正确");
        }
        return claims;
    }

    /**
     * 生成签发列表上下文文本，供后续匹配或展示。
     *
     * @param purpose 用途，作为 {@code issue} 的输入影响后续处理
     * @param ownerType 归属方类型标识，决定后续签发列表上下文采用的处理分支
     * @param ownerId 归属方ID，后续用于处理签发列表上下文时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理签发列表上下文时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code issue} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理签发列表上下文时定位或关联目标
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param targetEntityCode 目标实体编码，后续用于处理签发列表上下文时定位或关联目标
     * @param targetContentId 目标内容ID，后续用于处理签发列表上下文时定位或关联目标
     * @param targetReleaseId 目标发布版本ID，后续用于处理签发列表上下文时定位或关联目标
     * @param targetReleaseVersion 目标发布版本，供本方法处理签发列表上下文时使用
     * @param fixedFilters 固定过滤条件，供本方法处理签发列表上下文时使用
     * @param matchNone 匹配{@code none}，供本方法处理签发列表上下文时使用
     * @return 处理后的签发列表上下文文本，供调用方比较或展示
     */
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
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param ownerType 归属方类型标识，决定后续遍历入口采用的处理分支
     * @param ownerId 归属方ID，后续用于验证遍历入口时定位或关联目标
     * @param releaseId 发布版本ID，后续用于验证遍历入口时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code requirePositiveVersion} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
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
     *
     * @param previousToken 上一项令牌，后续用于授权校验、关联或幂等去重
     * @param ownerType 归属方类型标识，决定后续{@code advance}遍历采用的处理分支
     * @param ownerId 归属方ID，后续用于处理{@code advance}遍历时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理{@code advance}遍历时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code requirePositiveVersion} 的输入影响后续处理
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param nextOwnerType 下一步归属方类型标识，决定后续{@code advance}遍历采用的处理分支
     * @param nextOwnerId 下一步归属方ID，后续用于处理{@code advance}遍历时定位或关联目标
     * @param nextReleaseId 下一步发布版本ID，后续用于处理{@code advance}遍历时定位或关联目标
     * @param nextReleaseVersion 下一步发布版本，作为 {@code requirePositiveVersion} 的输入影响后续处理
     * @param nextRecordId 下一步记录ID，后续用于处理{@code advance}遍历时定位或关联目标
     * @return 处理后的{@code advance}遍历文本，供调用方比较或展示
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

    /**
     * 验证遍历；不满足约束时阻止后续处理。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的遍历结果，供调用方继续处理
     */
    private Claims verifyTraversal(String token) {
        Claims claims = verify(token);
        if (!TRAVERSAL.equals(claims.purpose())) {
            throw forbidden("关联内容遍历令牌用途不正确");
        }
        return claims;
    }

    /**
     * 为服务端运行时适配器解析已验证的遍历声明。
     *
     * <p>调用方只能使用返回值收窄到令牌固定的下一跳；不得把声明中的坐标
     * 当作设计态查询条件。签名、有效期和当前用户在返回前已经统一校验。</p>
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的遍历上下文结果，供调用方继续处理
     */
    public Claims verifyTraversalContext(String token) {
        return verifyTraversal(token);
    }

    /**
     * 构造{@code reentry}{@code blocked}异常，供调用方区分失败原因。
     *
     * @return 处理后的{@code reentry}{@code blocked}结果，供调用方继续处理
     */
    private BusinessConflictException reentryBlocked() {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RUNTIME_REENTRY_BLOCKED",
                "关联内容导航再次进入同一内容和记录，已停止加载以避免循环。"
                        + "请返回上一层，或改用其他记录。");
    }

    /**
     * 构造深度{@code exceeded}异常，供调用方区分失败原因。
     *
     * @return 处理后的深度{@code exceeded}结果，供调用方继续处理
     */
    private BusinessConflictException depthExceeded() {
        return new BusinessConflictException(
                "VIEW_COMPOSITION_RUNTIME_DEPTH_EXCEEDED",
                "关联内容导航已达到 " + MAX_TRAVERSAL_DEPTH
                        + " 层，已停止继续加载。请返回上一层后再打开其他内容。");
    }

    /**
     * 生成签发文本，供后续匹配或展示。
     *
     * @param claims 声明集合，供本方法处理签发时使用
     * @return 处理后的签发文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 验证界面视图组合令牌；不满足约束时阻止后续处理。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 验证后的界面视图组合令牌结果，供调用方继续处理
     */
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

    /**
     * 校验并获取密钥；不满足约束时阻止后续处理。
     *
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void requireSecret() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("关联内容令牌密钥未配置");
        }
    }

    /**
     * 生成签名文本，供后续匹配或展示。
     *
     * @param payload 载荷，后续用于处理签名并传递处理结果
     * @return 处理后的签名文本，供调用方比较或展示
     * @throws Exception 下游操作失败时向调用方传递
     */
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

    /**
     * 处理当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间结果，供调用方继续处理
     */
    private long now() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 构造权限不足异常，供调用方停止当前操作。
     *
     * @param message 消息，作为 {@code BusinessForbiddenException} 的输入影响后续处理
     * @return 处理后的禁止结果，供调用方继续处理
     */
    private BusinessForbiddenException forbidden(String message) {
        return new BusinessForbiddenException(
                "INVALID_VIEW_COMPOSITION_TOKEN",
                message);
    }

    /**
     * 校验并获取文本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取文本的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验并获取文本时匹配或展示
     * @return 校验并获取后的文本文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    /**
     * 校验并获取正数版本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取正数版本的原始输入，结果供调用方继续使用
     * @return 校验并获取后的正数版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Integer requirePositiveVersion(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("宿主发布版本号必须为正整数");
        }
        return value;
    }

    /**
     * 封装声明集合的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param purpose 用途，保存在对象中供后续校验、查询或展示
     * @param ownerType 归属方类型标识，决定后续声明集合采用的处理分支
     * @param ownerId 归属方ID，后续用于处理声明集合时定位或关联目标
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理声明集合时定位或关联目标
     * @param sourceRecordId 来源记录ID，后续用于处理声明集合时定位或关联目标
     * @param targetEntityCode 目标实体编码，后续用于处理声明集合时定位或关联目标
     * @param targetContentId 目标内容ID，后续用于处理声明集合时定位或关联目标
     * @param targetReleaseId 目标发布版本ID，后续用于处理声明集合时定位或关联目标
     * @param targetReleaseVersion 目标发布版本，保存在对象中供后续校验、查询或展示
     * @param fixedFilters 固定过滤条件，保存在对象中供后续校验、查询或展示
     * @param matchNone 匹配{@code none}，保存在对象中供后续校验、查询或展示
     * @param nextOwnerType 下一步归属方类型标识，决定后续声明集合采用的处理分支
     * @param nextOwnerId 下一步归属方ID，后续用于处理声明集合时定位或关联目标
     * @param nextReleaseId 下一步发布版本ID，后续用于处理声明集合时定位或关联目标
     * @param nextReleaseVersion 下一步发布版本，保存在对象中供后续校验、查询或展示
     * @param nextRecordId 下一步记录ID，后续用于处理声明集合时定位或关联目标
     * @param traversal 遍历，保存在对象中供后续校验、查询或展示
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     */
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

    /**
     * 单个已解析运行时节点，字段组合用于精确判定重入。
     *
     * @param ownerType 归属方类型标识，决定后续遍历跳采用的处理分支
     * @param ownerId 归属方ID，后续用于处理遍历跳时定位或关联目标
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param compositionKey 组合键，后续用于授权校验、关联或幂等去重
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public record TraversalHop(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String recordId) {
    }
}
