package com.workflow.entity.mutationpolicy.application.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutation policy document.
 *
 * <p>The legacy scenario/step DTO shape is deliberately retained as an
 * anti-corruption contract while persistence and publication are separated
 * from data-version policy. A scenario in this document is a mutation rule,
 * not a version capture trigger.</p>
 */
@JsonIgnoreProperties({
        "triggers",
        "snapshotScope",
        "diffPolicy",
        "relationOptions",
        "fieldOptions"
})
@Getter
@Setter
public class EntityMutationPolicyDocument
        extends EntityVersionConfiguration {

    /** 独立变更策略仍保留自身的草稿/发布生命周期。 */
    private String status;
    private String migrationState = "NATIVE";
    private String activeReleaseId;
    private Integer activeReleaseVersion;

    public EntityMutationPolicyDocument() {
        setSchemaVersion(1);
    }
}
