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

    /** 绑定列表，空表示该列表不绑规则（运行时可见全部） */
    private List<EntityListScopeBindingDTO> bindings = new ArrayList<>();
}
