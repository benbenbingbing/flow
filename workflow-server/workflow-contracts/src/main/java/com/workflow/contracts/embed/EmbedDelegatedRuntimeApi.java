package com.workflow.contracts.embed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Flow 原生端点需要额外的固定发布坐标校验。
 *
 * <p>已验证的 opaque Session 作为 mapped user 原生登录态，未声明端点
 * 仍可以进入普通 Flow 权限链。该注解只叠加 target/release binding，不替代
 * {@code AuthenticatedApi}/{@code RequiresPermission}、对象权限或 DataScope。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EmbedDelegatedRuntimeApi {

    Scope value();

    /**
     * 历史声明字段，仅保留二进制兼容与控制面审计。
     *
     * <p>原生数据面不消费该值；View/Grant capability 只限制入口与 Bridge，
     * 页面内按钮与事件按 mapped user 的普通 Flow 权限求值。</p>
     */
    @Deprecated(forRemoval = false)
    Capability requiredCapability() default Capability.NONE;

    /**
     * 请求如何携带服务端固定 target 坐标。
     *
     * <p>该声明让中央策略按稳定的坐标形状校验，而不是维护 URL path 或组件清单。
     * 只有需要读取浏览器携带的历史发布坐标时才应声明；新增普通
     * 原生组件端点无需为 Embed 单独注解。</p>
     */
    TargetBinding targetBinding() default TargetBinding.SCOPE_DEFAULT;

    enum Capability {
        NONE,
        LIST_QUERY,
        SELECTION_RETURN,
        RECORD_VIEW,
        RECORD_CREATE,
        ACTION_EXECUTE
    }

    enum TargetBinding {
        /** 使用 Scope 的标准 binding；主要用于兼容已存在的声明。 */
        SCOPE_DEFAULT,
        /** 不从请求读取 target；下游普通权限仍必须执行。 */
        NONE,
        /** {@code entityCode} URI template variable，可选签名遍历上下文。 */
        ROOT_ENTITY_PATH,
        /** {@code formId} URI variable 与精确 release query。 */
        FORM_RELEASE_PATH_QUERY,
        /** 标准 FormAction body 中的 form/release/entity/mode/record 坐标。 */
        FORM_ACTION_BODY,
        /** 标准 UiEvent body 与安全的 {@code eventCode} URI variable。 */
        FORM_EVENT_BODY,
        /** 唯一性预检的 {@code formId} URI variable、release body 与 record。 */
        FORM_UNIQUE_PRECHECK,
        /** 详情加载的 entity/record URI variables 与固定 release query。 */
        RECORD_DETAIL_PATH_QUERY,
        /** ownerType/ownerId/release/record body 坐标。 */
        FORM_OWNER_BODY,
        /** 由 Flow 服务端签发且由业务服务再次验证的运行时令牌。 */
        SIGNED_RUNTIME_CONTEXT,
        /** 原生文件读取；文件归属继续由 FileAccessService 验证。 */
        FILE_READ,
        /**
         * 原生 multipart 文件写入；要求幂等键，并继续执行通用存储权限或
         * 与固定根实体一致的字段级上传授权。
         */
        FILE_WRITE,
        /** {@code processInstanceId} URI variable，并反查固定实体记录。 */
        PROCESS_INSTANCE_PATH
    }

    enum Scope {
        /** 固定根实体的只读元数据。 */
        ROOT_ENTITY_METADATA,
        /** 固定 Published Form 及其签名子表单发布快照。 */
        FORM_RELEASE,
        /** 含 form/entity/release/mode/record 坐标的表单运行请求。 */
        FORM_CONTEXT,
        /** 固定实体与固定记录的详情加载。 */
        RECORD_DETAIL,
        /** 映射用户在普通 Flow 页面也可使用的无副作用运行时引用读取。 */
        REFERENCE_READ,
        /** 固定表单 owner 下由服务端发布绑定解析的运行请求。 */
        FORM_OWNER_RUNTIME,
        /** 上一步原生运行时签发的用户/发布/记录绑定令牌驱动的请求。 */
        SIGNED_RUNTIME_CONTEXT,
        /** 原生附件组件的上传/预览，同时受 Flow 存储权限约束。 */
        FILE_RUNTIME,
        /** 必须属于当前固定实体记录的流程只读运行时。 */
        PROCESS_RECORD_RUNTIME
    }
}
