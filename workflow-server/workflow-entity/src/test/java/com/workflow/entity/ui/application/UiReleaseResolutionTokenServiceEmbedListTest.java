package com.workflow.entity.ui.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UiReleaseResolutionTokenServiceEmbedListTest {

    @Test
    void elr1LengthDoesNotGrowWithDependencyClosureNodeCount()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        UiReleaseResolutionTokenService service =
                new UiReleaseResolutionTokenService(objectMapper);
        ReflectionTestUtils.setField(
                service,
                "secret",
                "embed-list-resolution-test-secret-32-bytes");
        EmbedNativeListDependencyClosure oneNode = closure(1);
        EmbedNativeListDependencyClosure sixtyFourNodes = closure(64);
        Instant expiresAt = Instant.now().plusSeconds(1200);

        String shortClosureToken = service.issueEmbedList(
                "entity_0", "list-0", "release-0", 1,
                "session-1", "view-1", "view-release-1", 1,
                hash(objectMapper, oneNode), expiresAt);
        String largeClosureToken = service.issueEmbedList(
                "entity_0", "list-0", "release-0", 1,
                "session-1", "view-1", "view-release-1", 1,
                hash(objectMapper, sixtyFourNodes), expiresAt);

        // elr1 只保存固定长度 SHA-256，不会随 closure 节点数增长并挤爆 URL 请求行。
        assertEquals(shortClosureToken.length(), largeClosureToken.length());
        assertTrue(largeClosureToken.length() < 1024);
        assertEquals(
                1,
                service.verifyEmbedList(largeClosureToken)
                        .dependencyClosureVersion());
    }

    private static EmbedNativeListDependencyClosure closure(int size) {
        List<ListNode> nodes = IntStream.range(0, size)
                .mapToObj(index -> new ListNode(
                        new ListCoordinate(
                                "entity_" + index,
                                "list_" + index,
                                "list-" + index,
                                "release-" + index,
                                index + 1),
                        true,
                        null,
                        index + 1 < size
                                ? List.of(new ListCoordinate(
                                "entity_" + (index + 1),
                                "list_" + (index + 1),
                                "list-" + (index + 1),
                                "release-" + (index + 1),
                                index + 2))
                                : List.of()))
                .toList();
        return new EmbedNativeListDependencyClosure(1, nodes);
    }

    private static String hash(
            ObjectMapper objectMapper,
            EmbedNativeListDependencyClosure closure) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        objectMapper.writeValueAsString(closure)
                                .getBytes(StandardCharsets.UTF_8)));
    }
}
