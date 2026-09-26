package com.workflow.embed.api.response;

import java.util.List;

/** RECORD_CREATE 的最小稳定响应；不包含字段、组件或记录值投影。 */
public final class EmbedRecordCreateViews {

    /**
     * 初始化嵌入式记录创建视图，保存构造参数供后续方法使用。
     */
    private EmbedRecordCreateViews() {
    }

    /**
     * 封装创建的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param receiptId 回执ID，后续用于处理创建结果时定位或关联目标
     * @param record 记录，保存在对象中供后续校验、查询或展示
     * @param effects {@code effects}，保存在对象中供后续校验、查询或展示
     * @param clientMutationId 客户端变更ID，后续用于处理创建结果时定位或关联目标
     */
    public record CreateResult(
            String receiptId,
            CreatedRecord record,
            List<Object> effects,
            String clientMutationId) {
    }

    /**
     * 封装已创建记录的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param recordVersion 记录版本，保存在对象中供后续校验、查询或展示
     */
    public record CreatedRecord(
            String id,
            Long recordVersion) {
    }
}
