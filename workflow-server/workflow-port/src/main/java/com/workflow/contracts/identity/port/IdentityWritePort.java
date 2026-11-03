package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.*;
import java.util.List;

/** biz 负责取数和调用时机，框架仅在调用期间校验、保存并返回结果。外层事务如存在须使用 READ_COMMITTED。 */
public interface IdentityWritePort {
    /** 保存用户基础快照及携带的扩展，映射、审计与资料一起提交；失败整次回滚。 */
    IdentitySaveResult saveUser(UserSaveModel model);
    /** 保存组织基础快照与扩展；父组织必须存在，派生路径由框架维护。 */
    IdentitySaveResult saveOrganization(OrganizationSaveModel model);
    /** 同来源最多 100 个用户，整批原子提交；已有同库事务须为 READ_COMMITTED，由外层决定最终提交。 */
    List<IdentitySaveResult> saveUsers(List<UserSaveModel> models);
    /** 同来源最多 100 个组织，按父子依赖处理并按输入顺序返回，错误整批回滚。 */
    List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models);
}
