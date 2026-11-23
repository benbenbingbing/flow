package com.workflow.port.entity.model;

import java.util.List;

/** 运行时读取的发布快照标识、可读字段和关系，以及当前业务状态。 */
public record EntityPublishedMetadata(String entityCode, String name, String publicationId, Integer version,
        String processDefinitionId, List<Field> fields, List<Relation> relations, List<Status> statuses) {
    public EntityPublishedMetadata {
        fields = List.copyOf(fields);
        relations = List.copyOf(relations);
        statuses = List.copyOf(statuses);
    }

    public record Field(String code, String name, String type, boolean required, String dictionaryCode,
                        String optionInterfaceId, String referencedEntityCode) { }
    public record Relation(String code, String name, String targetEntityCode, String type) { }
    public record Status(String code, String name, String category, String color) { }
}
