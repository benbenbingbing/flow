package com.workflow.port.system;

import com.workflow.port.system.identity.model.IdentityGroup;
import java.util.Optional;

/** 供平台内部解析系统用户组定义。 */
public interface SysGroupPort {
    /**
     * 查询分组；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组时定位或关联目标
     * @return 匹配的分组；未找到时为空
     */
    Optional<IdentityGroup> findGroup(String idOrCode);
}
