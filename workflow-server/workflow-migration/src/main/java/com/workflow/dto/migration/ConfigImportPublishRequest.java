package com.workflow.dto.migration;

import lombok.Data;

import java.util.List;

@Data
public class ConfigImportPublishRequest {

    private List<String> itemIds;
}
