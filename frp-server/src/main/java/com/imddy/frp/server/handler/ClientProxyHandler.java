package com.imddy.frp.server.handler;

import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

@Slf4j
public class ClientProxyHandler extends ChannelInboundHandlerAdapter {
    private final TunnelManager tunnelManager;
    private final String tunnelName;
    private final String channelId;
    private volatile boolean agentConnected = false;
    private final Queue<ByteBuf> pendingData = new LinkedList<>();
    private ChannelHandlerContext ctx;

    public ClientProxyHandler(TunnelManager tunnelManager, String tunnelName) {
        this.tunnelManager = tunnelManager;
        this.tunnelName = tunnelName;
        this.channelId = UUID.randomUUID().toString();
    }

    // 添加getter方法
    public String getChannelId() {
        return channelId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;

        // 注册到TunnelManager
        tunnelManager.addClientChannel(tunnelName, channelId, ctx.channel());
        tunnelManager.addClientHandler(tunnelName, channelId, this);

        // 暂停读取，等待Agent连接确认
        ctx.channel().config().setAutoRead(false);

        // 通知Agent有新连接
        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
        if (tunnel != null && tunnel.getAgentChannel() != null) {
            Message connectMsg = Message.connect(tunnelName, channelId);
            tunnel.getAgentChannel().writeAndFlush(connectMsg);
            log.info("New client connected, waiting for agent: channelId={}, tunnel={}", channelId, tunnelName);
        } else {
            log.error("Agent not connected for tunnel: {}", tunnelName);
            ctx.close();
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;

            if (!agentConnected) {
                // Agent还未确认连接，缓存数据
                pendingData.offer(buf.retain());
                log.debug("Buffering data, agent not ready: channelId={}, size={}", channelId, buf.readableBytes());
                buf.release();
                return;
            }

            try {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                // 转发数据到Agent
                TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
                if (tunnel != null && tunnel.getAgentChannel() != null) {
                    Message dataMsg = Message.data(channelId, data);
                    dataMsg.setTunnelName(tunnelName);
                    tunnel.getAgentChannel().writeAndFlush(dataMsg);
                    log.debug("Forward data to agent: channelId={}, size={}", channelId, data.length);
                } else {
                    log.error("Agent channel not available for tunnel: {}", tunnelName);
                    ctx.close();
                }
            } finally {
                buf.release();
            }
        }
    }

    // Agent连接确认回调
    public void onAgentConnected(boolean success) {
        if (!success) {
            log.error("Agent failed to connect to target: channelId={}", channelId);
            releasePendingData();
            if (ctx != null) {
                ctx.close();
            }
            return;
        }

        agentConnected = true;
        log.info("Agent connected to target, start forwarding: channelId={}", channelId);

        // 发送缓存的数据
        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
        if (tunnel != null && tunnel.getAgentChannel() != null) {
            while (!pendingData.isEmpty()) {
                ByteBuf buf = pendingData.poll();
                try {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);

                    Message dataMsg = Message.data(channelId, data);
                    dataMsg.setTunnelName(tunnelName);
                    tunnel.getAgentChannel().writeAndFlush(dataMsg);
                    log.debug("Forward buffered data to agent: channelId={}, size={}", channelId, data.length);
                } finally {
                    buf.release();
                }
            }
        }

        // 恢复读取
        if (ctx != null && ctx.channel().isActive()) {
            ctx.channel().config().setAutoRead(true);
            ctx.read();
        }
    }

    private void releasePendingData() {
        while (!pendingData.isEmpty()) {
            ByteBuf buf = pendingData.poll();
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        releasePendingData();
        tunnelManager.removeClientChannel(tunnelName, channelId);

        // 通知Agent连接断开
        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
        if (tunnel != null && tunnel.getAgentChannel() != null) {
            Message disconnectMsg = Message.disconnect(channelId);
            disconnectMsg.setTunnelName(tunnelName);
            tunnel.getAgentChannel().writeAndFlush(disconnectMsg);
        }

        log.info("Client disconnected: channelId={}, tunnel={}", channelId, tunnelName);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in client proxy handler: channelId={}", channelId, cause);
        releasePendingData();
        ctx.close();
    }
}
