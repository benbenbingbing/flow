package com.workflow.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
interface HostAddressResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
