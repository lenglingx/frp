package com.imddy.frp.agent.config;

import com.imddy.frp.common.protocol.ProxyProtocol;
import lombok.Data;

@Data
public class AgentConfig {
    private String type;
    private String tunnelName = "default";
    private FrpTunnel frpTunnel;
    private Proxy proxy;

    @Data
    public static class FrpTunnel {
        private String host;
        private int port;
    }

    @Data
    public static class Proxy {
        private String host;
        private int port;
    }

    public ProxyProtocol getProtocolType() {
        return ProxyProtocol.fromString(type);
    }
}
