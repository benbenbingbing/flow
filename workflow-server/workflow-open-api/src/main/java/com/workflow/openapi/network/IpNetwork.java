package com.workflow.openapi.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 封装{@code ip}{@code network}相关能力和状态；供同一业务流程的后续处理使用。
 */
public final class IpNetwork {

    private final byte[] networkAddress;
    private final int prefixLength;

    /**
     * 初始化{@code ip}{@code network}，保存构造参数供后续方法使用。
     *
     * @param networkAddress {@code network}地址依赖，保存到当前对象供后续业务方法调用
     * @param prefixLength 前缀长度依赖，保存到当前对象供后续业务方法调用
     */
    private IpNetwork(byte[] networkAddress, int prefixLength) {
        this.networkAddress = networkAddress.clone();
        this.prefixLength = prefixLength;
    }

    /**
     * 解析{@code ip}{@code network}；输出作为后续校验或处理的输入。
     *
     * @param value 待解析{@code ip}{@code network}的原始输入，结果供调用方继续使用
     * @return 解析后的{@code ip}{@code network}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 解析地址；输出作为后续校验或处理的输入。
     *
     * @param value 待解析地址的原始输入，结果供调用方继续使用
     * @return 解析后的地址结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 判断是否包含{@code ip}{@code network}；判断结果决定调用方的后续分支。
     *
     * @param address 地址，作为 {@code parseAddress} 的输入影响后续处理
     * @return {@code ip}{@code network}条件成立时为 true，否则为 false
     */
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
