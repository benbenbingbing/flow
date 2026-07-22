package com.workflow.dto.permission;

import com.workflow.dto.EntityDataDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EntityListScopeSimulationDTO {
    private String entityCode;
    private String listKey;
    private String userId;
    private String dataScopeMode;
    private Integer releaseVersion;
    private PermissionPreviewDTO preview;
    private long visibleCount;
    private List<EntityDataDTO> samples = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
