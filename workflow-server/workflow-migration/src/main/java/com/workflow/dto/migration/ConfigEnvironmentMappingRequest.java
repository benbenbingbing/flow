package com.workflow.dto.migration;

import lombok.Data;

import java.util.List;

@Data
public class ConfigEnvironmentMappingRequest {

    private List<MappingItem> mappings;

    @Data
    public static class MappingItem {
        private String sourceType;
        private String sourceKey;
        private String targetKey;
        private String description;
        private Boolean enabled = Boolean.TRUE;
    }
}
