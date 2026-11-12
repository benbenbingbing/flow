package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.*;
import java.util.List;

/**
 * biz 负责取数和调用时机，框架仅在调用期间校验、保存并返回结果。外层事务如存在须使用 READ_COMMITTED。
 * rawDataJson 是转换前的单条来源 JSON 对象，按来源身份覆盖保存；原文单独变化时仍返回 UNCHANGED。
 * 原文与业务资料共用事务，受 UTF-8 单条/批次限额和来源 raw-data-required 配置约束。
 */
public interface IdentityWritePort {
    /** 保存用户基础快照及携带的扩展，映射、审计与资料一起提交；失败整次回滚。 */
    IdentitySaveResult saveUser(UserSaveModel model);
    /** 保存组织基础快照与扩展；同来源父级允许后到，父链未齐时隐藏于组织树，补齐后自动维护路径及下级。 */
    IdentitySaveResult saveOrganization(OrganizationSaveModel model);
    /** 同来源最多 100 个用户，整批原子提交；已有同库事务须为 READ_COMMITTED，由外层决定最终提交。 */
    List<IdentitySaveResult> saveUsers(List<UserSaveModel> models);
    /** 同来源最多 100 个组织，允许父级不在本批或尚未到达；按输入顺序返回，非法父链及其他错误整批回滚。 */
    List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models);
}
