package com.workflow.entity.mutationpolicy.application.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;

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
public class EntityMutationPolicyDocument
        extends EntityVersionConfiguration {

    public EntityMutationPolicyDocument() {
        setSchemaVersion(1);
    }
}
