package com.workflow.embed.application.validation;

import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** Normalizes an HTTPS origin before exact grant comparison. */
public final class EmbedOriginNormalizer {

    private static final String HTTPS_PREFIX = "https://";

    private EmbedOriginNormalizer() {
    }

    /**
     * Produces {@code https://host[:non-default-port]} and rejects every URL component that is
     * not part of an Origin. Unicode DNS names are converted with IDNA STD3 rules.
     */
    public static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 2048) {
            throw invalidOrigin();
        }
        String value = candidate.trim();
        if (!value.regionMatches(true, 0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length())) {
            throw invalidOrigin();
        }
        String remainder = value.substring(HTTPS_PREFIX.length());
        int componentIndex = firstComponentIndex(remainder);
        String authority = componentIndex < 0 ? remainder : remainder.substring(0, componentIndex);
        String suffix = componentIndex < 0 ? "" : remainder.substring(componentIndex);
        // A root slash is harmless and canonicalized away; query and fragment are never Origins.
        if (!(suffix.isEmpty() || "/".equals(suffix)) || authority.contains("@")) {
            throw invalidOrigin();
        }

        HostPort hostPort = splitHostPort(authority);
        String host = normalizeHost(hostPort.host());
        int port = hostPort.port();
        String normalized = HTTPS_PREFIX + host + (port == -1 || port == 443 ? "" : ":" + port);
        if (normalized.length() > 255) {
            throw invalidOrigin();
        }
        return normalized;
    }

    private static int firstComponentIndex(String value) {
        int result = -1;
        for (char marker : new char[]{'/', '?', '#'}) {
            int index = value.indexOf(marker);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private static HostPort splitHostPort(String authority) {
        if (authority == null || authority.isBlank()) {
            throw invalidOrigin();
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close <= 1) {
                throw invalidOrigin();
            }
            String host = authority.substring(0, close + 1).toLowerCase(Locale.ROOT);
            String portPart = authority.substring(close + 1);
            return new HostPort(host, parsePortSuffix(portPart));
        }
        int firstColon = authority.indexOf(':');
        int lastColon = authority.lastIndexOf(':');
        if (firstColon != lastColon) {
            // IPv6 literals must use brackets in an Origin.
            throw invalidOrigin();
        }
        if (lastColon < 0) {
            return new HostPort(authority, -1);
        }
        return new HostPort(
                authority.substring(0, lastColon),
                parsePort(authority.substring(lastColon + 1)));
    }

    private static int parsePortSuffix(String suffix) {
        if (suffix.isEmpty()) {
            return -1;
        }
        if (!suffix.startsWith(":")) {
            throw invalidOrigin();
        }
        return parsePort(suffix.substring(1));
    }

    private static int parsePort(String value) {
        if (value == null || !value.matches("[0-9]{1,5}")) {
            throw invalidOrigin();
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw invalidOrigin();
            }
            return port;
        } catch (NumberFormatException error) {
            throw invalidOrigin();
        }
    }

    private static String normalizeHost(String candidate) {
        if (candidate == null || candidate.isBlank()
                || candidate.indexOf('*') >= 0 || candidate.indexOf('%') >= 0) {
            throw invalidOrigin();
        }
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            String literal = candidate.substring(1, candidate.length() - 1);
            if (!literal.contains(":") || !literal.matches("[0-9A-Fa-f:.]+")) {
                throw invalidOrigin();
            }
            try {
                // 输入已经被限制为数字字面量；InetAddress 在此只做 IPv6 语法校验，不会发起 DNS。
                InetAddress parsed = InetAddress.getByName(literal);
                if (!(parsed instanceof Inet6Address)) {
                    throw invalidOrigin();
                }
                return "[" + literal.toLowerCase(Locale.ROOT) + "]";
            } catch (UnknownHostException error) {
                throw invalidOrigin();
            }
        }
        String noTrailingDot = candidate.endsWith(".")
                ? candidate.substring(0, candidate.length() - 1)
                : candidate;
        try {
            String ascii = IDN.toASCII(noTrailingDot, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            if (ascii.isBlank() || ascii.length() > 253 || ascii.contains("..")) {
                throw invalidOrigin();
            }
            return ascii;
        } catch (IllegalArgumentException error) {
            throw invalidOrigin();
        }
    }

    private static EmbedException invalidOrigin() {
        return new EmbedException(
                400,
                EmbedErrorCode.INVALID_REQUEST,
                "parentOrigin must be an exact HTTPS origin");
    }

    private record HostPort(String host, int port) {
    }
}
