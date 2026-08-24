package com.workflow.entity.permission.api.request;

import com.workflow.entity.permission.api.response.EntityListScopeBindingDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖某个列表的数据范围绑定。
 */
@Data
public class EntityListScopeListBindingsRequest {

    /** 绑定列表，空表示运行时采用 unboundPolicy，而不是隐式放行。 */
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();

    /**
     * 未绑定 ALLOW 规则时的策略；为空表示本次只更新绑定，不改变现有安全默认值。
     */
    private String unboundPolicy;

    /** 切换到 EXPLICIT_ALL 时必须显式传 true。 */
    private Boolean confirmExplicitAll;

    /** 切换到 EXPLICIT_ALL 时必填的业务原因。 */
    private String confirmationNote;
}
