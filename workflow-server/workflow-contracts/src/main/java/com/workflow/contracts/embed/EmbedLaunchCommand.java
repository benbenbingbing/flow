package com.workflow.contracts.embed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable business input for issuing an Embed launch.
 */
public record EmbedLaunchCommand(
        String viewKey,
        String parentOrigin,
        String channelId,
        EmbedLaunchSubject subject,
        EmbedLaunchEntry entry,
        Map<String, Object> context,
        EmbedLaunchUi ui) {

    public EmbedLaunchCommand {
        Objects.requireNonNull(viewKey, "viewKey");
        Objects.requireNonNull(parentOrigin, "parentOrigin");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(entry, "entry");
        context = context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }
}
