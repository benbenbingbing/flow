package com.workflow.openapi.connector.secret;

record IntegrationSecretEnvelope(
        String keyVersion,
        String encryptedDataKey,
        String dataKeyNonce,
        String secretCiphertext,
        String secretNonce) {
}
