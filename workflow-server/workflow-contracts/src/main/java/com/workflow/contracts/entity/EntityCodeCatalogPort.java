package com.workflow.contracts.entity;

import java.util.Set;

public interface EntityCodeCatalogPort {

    Set<String> findAllEntityCodes();

    String findEntityCodeByProcessDefinitionId(String processDefinitionId);
}
