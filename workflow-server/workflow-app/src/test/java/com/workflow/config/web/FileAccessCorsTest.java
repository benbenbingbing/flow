package com.workflow.config.web;

import com.workflow.admin.auth.infrastructure.web.AuthInterceptor;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.embed.infrastructure.web.EmbedDelegatedRuntimeAuthorizationInterceptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FileAccessCorsTest {

    @Test
    void crossOriginFileResponseExposesOriginalFilenameAndDisposition() throws Exception {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://flow-ui.example"));
        ObjectProvider<EmbedDelegatedRuntimeAuthorizationInterceptor> delegated = mock(ObjectProvider.class);
        WebAccessConfiguration configuration = new WebAccessConfiguration(
                mock(AuthInterceptor.class), mock(EndpointAuthorizationInterceptor.class), delegated, properties);
        TestCorsRegistry registry = new TestCorsRegistry();
        configuration.webConfigurer().addCorsMappings(registry);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.setCorsConfigurations(registry.configurations());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/file/preview");
        request.addHeader(HttpHeaders.ORIGIN, "https://flow-ui.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, (req, res) -> {});

        assertEquals("https://flow-ui.example", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("Content-Disposition", response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
    }

    private static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
