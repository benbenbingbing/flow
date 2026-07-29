package com.workflow.openapi.connector.secret;

import com.workflow.contracts.integration.IntegrationSecretResolver;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIntegrationSecretResolver
        implements IntegrationSecretResolver {

    private static final Pattern ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");

    private final IntegrationSecretMapper mapper;
    private final IntegrationSecretCipher cipher;

    DatabaseIntegrationSecretResolver(
            IntegrationSecretMapper mapper,
            IntegrationSecretCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    @Override
    public String resolve(String secretAlias) {
        SecretReference reference = parse(secretAlias);
        IntegrationSecretRecord secret = mapper.findResolvable(
                reference.applicationId(),
                reference.secretName());
        if (secret == null) {
            throw new IllegalArgumentException(
                    "集成 Secret 引用不存在或不可用");
        }
        return cipher.decrypt(
                secret.getApplicationId(),
                secret.getSecretName(),
                secret.getSecretVersion(),
                secret.envelope());
    }

    static SecretReference parse(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            String[] segments = path == null
                    ? new String[0]
                    : path.split("/", -1);
            if (!"secret".equals(uri.getScheme())
                    || !"integration".equals(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || segments.length != 3
                    || !segments[0].isEmpty()
                    || !ID.matcher(segments[1]).matches()
                    || !NAME.matcher(segments[2]).matches()) {
                throw invalidReference();
            }
            return new SecretReference(segments[1], segments[2]);
        } catch (URISyntaxException | NullPointerException exception) {
            throw invalidReference();
        }
    }

    private static IllegalArgumentException invalidReference() {
        return new IllegalArgumentException(
                "Secret 引用必须符合 secret://integration/{applicationId}/{secretName}");
    }

    record SecretReference(String applicationId, String secretName) {
    }
}
