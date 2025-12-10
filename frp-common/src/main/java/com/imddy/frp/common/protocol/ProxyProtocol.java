package com.imddy.frp.common.protocol;

public enum ProxyProtocol {
    TCP("tcp"),
    UDP("udp"),
    HTTP("http"),
    HTTPS("https");

    private final String name;

    ProxyProtocol(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static ProxyProtocol fromString(String name) {
        for (ProxyProtocol protocol : values()) {
            if (protocol.name.equalsIgnoreCase(name)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Unknown protocol: " + name);
    }
}
