package com.workflow.http;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

public record ApprovedEndpoint(
        URI uri,
        String host,
        List<InetAddress> addresses) {
}
