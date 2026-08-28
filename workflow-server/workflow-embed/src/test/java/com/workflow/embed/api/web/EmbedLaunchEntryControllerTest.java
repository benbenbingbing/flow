package com.workflow.embed.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.launch.EmbedLaunchEntryService;
import com.workflow.embed.config.EmbedProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EmbedLaunchEntryControllerTest {

    @Test
    void emitsExactCspAndOnlyNonSecretBase64Metadata() throws Exception {
        EmbedLaunchEntryService service = mock(EmbedLaunchEntryService.class);
        when(service.resolve("lch_0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.of(new EmbedLaunchEntryService.EntryMetadata(
                        "lch_0123456789abcdef0123456789abcdef",
                        "https://portal.partner.example",
                        "channel-12345678",
                        "flow-embed/1")));
        EmbedProperties properties = new EmbedProperties();
        properties.setEntryAssetPath("/embed-assets/embed-main.js");
        properties.setEntryStylePath("/embed-assets/embed-main.css");
        var controller = new EmbedLaunchEntryController(
                service, properties, new ObjectMapper());

        var response = controller.entry(
                "lch_0123456789abcdef0123456789abcdef");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst("Content-Security-Policy")
                .endsWith("frame-ancestors https://portal.partner.example"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        String html = response.getBody();
        assertFalse(html.contains("launchCode"));
        assertFalse(html.contains("accessToken"));
        assertTrue(html.contains("src=\"/embed-assets/embed-main.js\""));
        assertTrue(html.contains("href=\"/embed-assets/embed-main.css\""));

        var matcher = Pattern.compile(
                "name=\"flow-embed-entry\" content=\"([A-Za-z0-9_-]+)\"")
                .matcher(html);
        assertTrue(matcher.find());
        String json = new String(
                Base64.getUrlDecoder().decode(matcher.group(1)),
                StandardCharsets.UTF_8);
        assertTrue(json.contains("https://portal.partner.example"));
        assertFalse(json.contains("user"));
    }

    @Test
    void invalidLaunchCannotBeFramed() {
        EmbedLaunchEntryService service = mock(EmbedLaunchEntryService.class);
        when(service.resolve("missing")).thenReturn(Optional.empty());
        var controller = new EmbedLaunchEntryController(
                service, new EmbedProperties(), new ObjectMapper());

        var response = controller.entry("missing");

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst("Content-Security-Policy")
                .endsWith("frame-ancestors 'none'"));
    }
}
