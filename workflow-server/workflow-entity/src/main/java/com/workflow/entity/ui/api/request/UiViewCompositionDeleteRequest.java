package com.workflow.entity.ui.api.request;

import lombok.Data;

/**
 * 删除“关联内容”的乐观锁请求。
 */
@Data
public class UiViewCompositionDeleteRequest {

    /** 兼容正文式资源路由使用的 FORM/LIST 宿主类型。 */
    private String ownerType;
    /** 兼容正文式资源路由使用的宿主 ID。 */
    private String ownerId;
    /** 客户端读取到的关联内容修订号。 */
    private Integer expectedRevision;
    /** 客户端读取关联内容时对应的宿主草稿修订号。 */
    private Integer expectedOwnerRevision;
}
