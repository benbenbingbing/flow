package com.workflow.service.cc;

import java.util.List;
import java.util.Map;

/**
 * 知会人员解析器接口。
 *
 * <p>实现该接口可注册自定义的知会人员解析逻辑，由知会配置中的
 * RESOLVER 类型规则调用，按 code 匹配对应的解析器。</p>
 */
public interface CcRecipientResolver {

    /**
     * 获取解析器唯一标识，用于在知会配置中通过 resolverCode 引用。
     *
     * @return 解析器编码
     */
    String code();

    /**
     * 根据运行时上下文与参数解析出知会人员标识列表（用户ID或用户名）。
     *
     * @param context   知会运行时上下文
     * @param parameters 解析器参数
     * @return 知会人员标识列表
     */
    List<String> resolve(CcRuntimeContext context, Map<String, Object> parameters);
}
