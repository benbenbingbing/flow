package com.workflow.embed.management.crypto;

import java.util.List;

/** 外部 Subject 摘要端口；原始 Subject 绝不能越过该边界进入持久化。 */
public interface EmbedSubjectDigester {

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理当前时定位或关联目标
     * @param providerId 提供者ID，后续用于处理当前时定位或关联目标
     * @param subjectNamespace 主体命名空间，供本方法处理当前时使用
     * @param externalSubject 外部主体，供本方法处理当前时使用
     * @return 处理后的当前结果，供调用方继续处理
     */
    Digest current(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject);

    /**
     * 整理{@code accepted}数据，供调用方遍历或继续处理。
     *
     * @param applicationId 应用ID，后续用于处理{@code accepted}时定位或关联目标
     * @param providerId 提供者ID，后续用于处理{@code accepted}时定位或关联目标
     * @param subjectNamespace 主体命名空间，供本方法处理{@code accepted}时使用
     * @param externalSubject 外部主体，供本方法处理{@code accepted}时使用
     * @return 摘要集合，供调用方遍历或展示
     */
    List<Digest> accepted(
            String applicationId,
            String providerId,
            String subjectNamespace,
            String externalSubject);

    /**
     * 封装摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param value 待处理摘要的原始输入，结果供调用方继续使用
     * @param keyVersion 键版本，保存在对象中供后续校验、查询或展示
     */
    record Digest(String value, String keyVersion) {
    }
}
