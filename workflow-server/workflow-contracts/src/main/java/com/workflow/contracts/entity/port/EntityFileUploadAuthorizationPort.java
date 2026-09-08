package com.workflow.contracts.entity.port;

/**
 * 实体字段文件上传的对象级授权端口。
 *
 * <p>存储模块只负责文件落盘与归属登记；实体模块通过该端口校验当前用户的
 * 实体操作权限，以及上传目标确实是对应实体的文件字段。</p>
 */
public interface EntityFileUploadAuthorizationPort {

    /**
     * 校验当前用户是否可以在指定实体操作中向目标字段上传文件。
     *
     * @param entityCode 实体编码
     * @param action     实体操作权限码后缀，如 create、update、approve
     * @param fieldCode  文件字段编码
     * @throws RuntimeException 上下文无效、字段不匹配或当前用户无权限时抛出
     */
    void requireUpload(
            String entityCode,
            String action,
            String fieldCode);
}
