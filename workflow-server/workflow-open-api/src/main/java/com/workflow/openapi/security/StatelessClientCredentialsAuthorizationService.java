package com.workflow.openapi.security;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

public class StatelessClientCredentialsAuthorizationService
        implements OAuth2AuthorizationService {

    @Override
    public void save(OAuth2Authorization authorization) {
        // Client Credentials access tokens are self-contained and have no refresh token.
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        // No server-side authorization state is retained.
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return null;
    }

    @Override
    public OAuth2Authorization findByToken(
            String token,
            OAuth2TokenType tokenType) {
        return null;
    }
}
