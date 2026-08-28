package com.workflow.embed.application.port;

import com.workflow.embed.domain.ProtectedContext;
import java.util.Map;

/** Encrypts trusted launch/session context and computes its versioned equality-safe digest. */
public interface EmbedContextProtectionPort {

    ProtectedContext protectLaunch(
            String applicationId,
            String launchId,
            Map<String, Object> context);

    Map<String, Object> unprotectLaunch(
            String applicationId,
            String launchId,
            ProtectedContext context);

    ProtectedContext protectSession(
            String applicationId,
            String sessionId,
            Map<String, Object> context);

    Map<String, Object> unprotectSession(
            String applicationId,
            String sessionId,
            String ciphertext,
            String keyVersion);
}
