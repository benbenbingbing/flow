package com.workflow.contracts.process.port;

/** 流程表单只读入口的记录访问契约，不授予实体编辑或任务办理权限。 */
public interface ProcessRecordReadAccessPort {

    /**
     * 按当前认证用户的流程参与/知会关系校验记录，并核对实例实际绑定的业务记录和发布版本。
     *
     * @param entityCode 服务端表单所属实体编码
     * @param recordId 服务端读取的业务记录 ID
     * @param processInstanceId 业务记录上的流程实例 ID
     * @param processVersionHistoryId 已验签表单上下文中的不可变流程发布历史 ID
     * @throws RuntimeException 用户无权读取流程或任一记录/版本坐标不匹配
     */
    void requireReadAccess(String entityCode, String recordId,
                           String processInstanceId, String processVersionHistoryId);
}
