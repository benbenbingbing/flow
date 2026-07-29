package com.workflow.openapi.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class IpNetwork {

    private final byte[] networkAddress;
    private final int prefixLength;

    private IpNetwork(byte[] networkAddress, int prefixLength) {
        this.networkAddress = networkAddress.clone();
        this.prefixLength = prefixLength;
    }

    public static IpNetwork parse(String value) {
        if (value == null || !value.equals(value.trim())) {
            throw new IllegalArgumentException("CIDR 格式不正确");
        }
        String[] parts = value.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR 格式不正确");
        }
        InetAddress address = parseAddress(parts[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "CIDR 前缀格式不正确",
                    exception);
        }
        int maximum = address.getAddress().length * Byte.SIZE;
        if (prefix < 0 || prefix > maximum) {
            throw new IllegalArgumentException("CIDR 前缀超出地址范围");
        }
        return new IpNetwork(address.getAddress(), prefix);
    }

    public static InetAddress parseAddress(String value) {
        if (value == null
                || value.isBlank()
                || !value.equals(value.trim())) {
            throw new IllegalArgumentException("IP 地址格式不正确");
        }
        if (value.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(value);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException(
                        "IPv6 地址格式不正确",
                        exception);
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("IPv4 地址格式不正确");
        }
        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            if (!octets[index].matches("0|[1-9][0-9]{0,2}")) {
                throw new IllegalArgumentException("IPv4 地址格式不正确");
            }
            int octet = Integer.parseInt(octets[index]);
            if (octet > 255) {
                throw new IllegalArgumentException("IPv4 地址格式不正确");
            }
            address[index] = (byte) octet;
        }
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(
                    "IPv4 地址格式不正确",
                    exception);
        }
    }

    public boolean contains(String address) {
        byte[] candidate = parseAddress(address).getAddress();
        if (candidate.length != networkAddress.length) {
            return false;
        }
        int completeBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int index = 0; index < completeBytes; index++) {
            if (candidate[index] != networkAddress[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (Byte.SIZE - remainingBits);
        return (candidate[completeBytes] & mask)
                == (networkAddress[completeBytes] & mask);
    }
}
