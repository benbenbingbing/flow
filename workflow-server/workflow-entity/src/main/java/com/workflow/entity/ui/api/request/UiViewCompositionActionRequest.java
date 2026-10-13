package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 已发布“关联内容”的权威动作请求。
 *
 * <p>客户端只提交业务定位上下文、业务动作和记录 ID。宿主、发布版本、
 * 实体、关系字段及映射均从已发布配置恢复，禁止由浏览器
 * 临时指定。</p>
 */
@Data
public class UiViewCompositionActionRequest {
    /** 服务端每次重新解析的业务入口及关联路径。 */
    private UiViewCompositionResolveRequest compositionContext;

    /** SELECT、LINK 或 UNLINK；其余动作在安全提交桥开放前会明确拒绝。 */
    private String action;
    /** 目标记录 ID；服务端会逐条重读并重新应用数据权限。 */
    private List<String> targetRecordIds;
    /** 预留的受控动作输入；当前权威动作不接受客户端字段补丁。 */
    private Map<String, Object> input;
    /** 客户端生成的单次业务操作 ID，写动作据此生成持久化幂等键。 */
    private String operationId;
}
