package com.workflow.entity.data.application;

import com.workflow.contracts.entity.list.DataScopePlan;

import java.util.List;

/**
 * 关系图读取的服务端授权凭证。
 *
 * <p>该接口是 sealed 的，唯一实现没有 public 构造器，只能由
 * {@link EntityRelationGraphAuthorizationService} 在已认证请求中签发。它不是
 * API 请求模型，控制器不得从客户端 JSON 反序列化或自行拼装。</p>
 */
public sealed interface EntityRelationGraphAuthorizationPlan
        permits IssuedEntityRelationGraphAuthorizationPlan {

    /** 当前授权绑定的登录用户。 */
    String subjectUserId();

    /** 显式声明的平台内部使用目的，禁止把空 listKey 当隐式默认。 */
    InternalPurpose purpose();

    /** 来源实体授权。 */
    Grant source();

    /** 与发布路径 hop 严格一一对应的目标实体授权。 */
    List<Grant> hops();

    /**
     * 当前允许的平台内部安全调用场景。
     *
     * <p>关联内容等 UI 运行时不在此列；UI 必须由宿主的精确表单/列表发布
     * 身份另行签发逐跳授权，不能复用内部入口。</p>
     */
    enum InternalPurpose {
        PROCESS_COORDINATION,
        VERSION_GRAPH,
        AUDIT_PROJECTION
    }

    /** 逐跳授权来源；当前底座只签发 INTERNAL_SAFE。 */
    enum AccessMode {
        PUBLISHED_LIST,
        INTERNAL_SAFE
    }

    /**
     * 某个精确实体发布版本的数据范围授权。
     *
     * @param hopIndex       来源为 0，目标 hop 从 1 开始
     * @param entityCode     实体编码
     * @param entityHistoryId 精确发布历史 ID
     * @param entitySchemaHash 精确发布指纹
     * @param accessMode      发布列表或显式内部安全模式
     * @param listKey        发布列表键或固定平台内部入口键
     * @param publishedIdentity 发布列表精确发布身份；内部模式为空
     * @param dataScopePlan  当前用户由平台权限引擎计算的范围计划
     */
    record Grant(
            int hopIndex,
            String entityCode,
            String entityHistoryId,
            String entitySchemaHash,
            AccessMode accessMode,
            String listKey,
            String publishedIdentity,
            DataScopePlan dataScopePlan) {
    }
}

/** 包级私有实现阻止其他模块伪造“服务端已授权”对象。 */
final class IssuedEntityRelationGraphAuthorizationPlan
        implements EntityRelationGraphAuthorizationPlan {

    private final String subjectUserId;
    private final InternalPurpose purpose;
    private final Grant source;
    private final List<Grant> hops;

    IssuedEntityRelationGraphAuthorizationPlan(
            String subjectUserId,
            InternalPurpose purpose,
            Grant source,
            List<Grant> hops) {
        this.subjectUserId = subjectUserId;
        this.purpose = purpose;
        this.source = source;
        this.hops = hops == null ? List.of() : List.copyOf(hops);
    }

    @Override
    public String subjectUserId() {
        return subjectUserId;
    }

    @Override
    public InternalPurpose purpose() {
        return purpose;
    }

    @Override
    public Grant source() {
        return source;
    }

    @Override
    public List<Grant> hops() {
        return hops;
    }
}
