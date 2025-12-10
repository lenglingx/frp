package com.imddy.frp.server.tunnel;

import com.imddy.frp.server.config.ServerConfig;
import com.imddy.frp.server.handler.ClientProxyHandler;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TunnelManager {
    private final Map<String, TunnelInfo> tunnels = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Channel>> clientChannels = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ClientProxyHandler>> clientHandlers = new ConcurrentHashMap<>();

    public void registerTunnel(ServerConfig.TunnelConfig config) {
        TunnelInfo info = new TunnelInfo();
        info.setConfig(config);
        info.setStatus(TunnelStatus.WAITING);
        tunnels.put(config.getName(), info);
        log.info("Registered tunnel: {}, openPort: {}, agentPort: {}",
                config.getName(), config.getOpenPort(), config.getAgentPort());
    }

    public void agentConnected(String tunnelName, Channel agentChannel) {
        TunnelInfo info = tunnels.get(tunnelName);
        if (info != null) {
            info.setAgentChannel(agentChannel);
            info.setStatus(TunnelStatus.CONNECTED);
            clientChannels.putIfAbsent(tunnelName, new ConcurrentHashMap<>());
            clientHandlers.putIfAbsent(tunnelName, new ConcurrentHashMap<>());
            log.info("Agent connected to tunnel: {}", tunnelName);
        }
    }

    public void agentDisconnected(String tunnelName) {
        TunnelInfo info = tunnels.get(tunnelName);
        if (info != null) {
            info.setAgentChannel(null);
            info.setStatus(TunnelStatus.DISCONNECTED);
            // 清理所有客户端连接
            Map<String, Channel> channels = clientChannels.remove(tunnelName);
            if (channels != null) {
                channels.values().forEach(Channel::close);
            }
            clientHandlers.remove(tunnelName);
            log.info("Agent disconnected from tunnel: {}", tunnelName);
        }
    }

    public void addClientChannel(String tunnelName, String channelId, Channel channel) {
        clientChannels.computeIfAbsent(tunnelName, k -> new ConcurrentHashMap<>())
                .put(channelId, channel);
    }

    public void addClientHandler(String tunnelName, String channelId, ClientProxyHandler handler) {
        clientHandlers.computeIfAbsent(tunnelName, k -> new ConcurrentHashMap<>())
                .put(channelId, handler);
    }

    public Channel getClientChannel(String tunnelName, String channelId) {
        Map<String, Channel> channels = clientChannels.get(tunnelName);
        return channels != null ? channels.get(channelId) : null;
    }

    public ClientProxyHandler getClientHandler(String tunnelName, String channelId) {
        Map<String, ClientProxyHandler> handlers = clientHandlers.get(tunnelName);
        return handlers != null ? handlers.get(channelId) : null;
    }

    public void removeClientChannel(String tunnelName, String channelId) {
        Map<String, Channel> channels = clientChannels.get(tunnelName);
        if (channels != null) {
            channels.remove(channelId);
        }
        Map<String, ClientProxyHandler> handlers = clientHandlers.get(tunnelName);
        if (handlers != null) {
            handlers.remove(channelId);
        }
    }

    public TunnelInfo getTunnel(String tunnelName) {
        return tunnels.get(tunnelName);
    }

    public Map<String, TunnelInfo> getAllTunnels() {
        return new ConcurrentHashMap<>(tunnels);
    }

    @Data
    public static class TunnelInfo {
        private ServerConfig.TunnelConfig config;
        private Channel agentChannel;
        private TunnelStatus status;
        private long connectedTime;
        private long totalConnections;
    }

    public enum TunnelStatus {
        WAITING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }
}
