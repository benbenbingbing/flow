package com.workflow.entity.definition.api.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量解析实体选择项。
 */
@Data
public class EntityDefinitionOptionResolveRequest {

    private List<String> ids = new ArrayList<>();

    private List<String> entityCodes = new ArrayList<>();
}
