package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.Map;

/**
 * 新增或更新“关联内容”的请求。
 */
@Data
public class UiViewCompositionSaveRequest {

    /** 兼容正文式资源路由；路径式路由会忽略并以路径为准。 */
    private String ownerType;
    /** 兼容正文式资源路由；路径式路由会忽略并以路径为准。 */
    private String ownerId;
    /** 更新时必填：客户端读取到的关联内容修订号。 */
    private Integer expectedRevision;
    /**
     * 更新时必填：客户端读取关联内容时对应的宿主草稿修订号。
     *
     * <p>撤销或回滚会重建关联内容行；同时校验宿主修订号可阻止旧页面利用
     * 重建后的相同子项 revision 覆盖刚恢复的草稿。</p>
     */
    private Integer expectedOwnerRevision;
    /** 宿主范围内稳定且唯一的业务编码。 */
    private String compositionKey;
    /** OWNER/FORM_NODE/PAGE_SECTION/ROW_EXPAND/TOOLBAR_ACTION/ROW_ACTION。 */
    private String anchorType;
    /** 非 OWNER 挂载点对应的节点、分区或操作编码。 */
    private String anchorKey;
    /** 四步引导生成的结构化关联内容配置。 */
    private Map<String, Object> config;
    /** 同一挂载点内的排序键；不传时由服务端追加到末尾。 */
    private Long orderKey;
}
