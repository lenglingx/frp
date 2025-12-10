package com.imddy.frp.server.config;

import com.imddy.frp.common.protocol.ProxyProtocol;
import lombok.Data;
import java.util.List;

@Data
public class ServerConfig {
    private Management management;
    private List<TunnelConfig> tunnels;

    @Data
    public static class Management {
        private int port = 8089;
    }

    @Data
    public static class TunnelConfig {
        private String name;
        private String type;
        private int openPort;    // 对外开放的端口
        private int agentPort;   // Agent连接的端口

        public ProxyProtocol getProtocolType() {
            return ProxyProtocol.fromString(type);
        }
    }
}
