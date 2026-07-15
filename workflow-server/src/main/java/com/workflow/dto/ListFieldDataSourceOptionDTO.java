package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListFieldDataSourceOptionDTO {

    private String value;

    private String label;

    private String description;

    private boolean supportsVirtualField;

    private boolean supportsQuery;

    @Builder.Default
    private List<Map<String, Object>> configSchema = new ArrayList<>();
}
