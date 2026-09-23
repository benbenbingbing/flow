package com.workflow.entity.data.application;

import com.workflow.contracts.entity.list.model.DataScopePlan;

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

    /**
     * 当前授权绑定的登录用户。
     *
     * @return 处理后的主体用户ID文本，供调用方比较或展示
     */
    String subjectUserId();

    /**
     * 显式声明的平台内部使用目的，禁止把空 listKey 当隐式默认。
     *
     * @return 处理后的用途结果，供调用方继续处理
     */
    InternalPurpose purpose();

    /**
     * 来源实体授权。
     *
     * @return 处理后的来源结果，供调用方继续处理
     */
    Grant source();

    /**
     * 与发布路径 hop 严格一一对应的目标实体授权。
     *
     * @return 授权集合，供调用方遍历或展示
     */
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

    /**
     * 初始化已签发实体关系图授权方案，保存构造参数供后续方法使用。
     *
     * @param subjectUserId 主体用户ID依赖，保存到当前对象供后续业务方法调用
     * @param purpose 用途依赖，保存到当前对象供后续业务方法调用
     * @param source 来源依赖，保存到当前对象供后续业务方法调用
     * @param hops {@code hops}依赖，保存到当前对象供后续业务方法调用
     */
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

    /**
     * 生成主体用户ID文本，供后续匹配或展示。
     *
     * @return 处理后的主体用户ID文本，供调用方比较或展示
     */
    @Override
    public String subjectUserId() {
        return subjectUserId;
    }

    /**
     * 处理用途，并将结果传给后续步骤。
     *
     * @return 处理后的用途结果，供调用方继续处理
     */
    @Override
    public InternalPurpose purpose() {
        return purpose;
    }

    /**
     * 处理来源，并将结果传给后续步骤。
     *
     * @return 处理后的来源结果，供调用方继续处理
     */
    @Override
    public Grant source() {
        return source;
    }

    /**
     * 整理{@code hops}数据，供调用方遍历或继续处理。
     *
     * @return 授权集合，供调用方遍历或展示
     */
    @Override
    public List<Grant> hops() {
        return hops;
    }
}
