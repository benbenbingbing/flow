package com.workflow.entity.form.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 表单接口响应：保留现有字段结构，将审计时间输出为明确的 UTC 时刻。
 *
 * <p>表单创建和更新时间由 LocalDateTime.now() 写入，数据库列不保存时区。
 * API 必须按写入所用的服务端时区解释，避免 UTC 容器中的时间被浏览器当成本地时间。
 * 转换仅作用于 HTTP 响应，实体持久化和发布快照继续使用原有时间格式。
 * 同一数据库的服务端时区应与写入时保持一致，不能通过更换 JVM 时区修正历史值。</p>
 *
 * @param form 表单，保存在对象中供后续校验、查询或展示
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EntityFormResponse(
        @JsonUnwrapped
        @JsonIgnoreProperties({"createTime", "updateTime"})
        EntityForm form,
        Instant createTime,
        Instant updateTime) {

    /**
     * 将表单的服务端本地时间转换成 UTC 时刻；不存在的表单仍返回 null。
     *
     * @param form 表单，作为 {@code EntityFormResponse} 的输入影响后续处理
     * @return 处理后的起始结果，供调用方继续处理
     */
    public static EntityFormResponse from(EntityForm form) {
        if (form == null) {
            return null;
        }
        ZoneId serverZone = ZoneId.systemDefault();
        return new EntityFormResponse(
                form,
                asInstant(form.getCreateTime(), serverZone),
                asInstant(form.getUpdateTime(), serverZone));
    }

    /**
     * 转换为绝对时间；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为绝对时间的原始输入，结果供调用方继续使用
     * @param zone {@code zone}，作为 {@code value.atZone} 的输入影响后续处理
     * @return 转换为后的绝对时间结果，供调用方继续处理
     */
    private static Instant asInstant(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant();
    }
}
