package com.imddy.frp.server.handler;

import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.common.protocol.MessageType;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentServerHandler extends SimpleChannelInboundHandler<Message> {
    private final TunnelManager tunnelManager;
    private String tunnelName;

    public AgentServerHandler(TunnelManager tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        switch (msg.getType()) {
            case REGISTER:
                handleRegister(ctx, msg);
                break;
            case HEARTBEAT:
                handleHeartbeat(ctx);
                break;
            case CONNECT_ACK:  // 新增
                handleConnectAck(msg);
                break;
            case DATA:
                handleData(msg);
                break;
            case DISCONNECT:
                handleDisconnect(msg);
                break;
            default:
                log.warn("Unknown message type: {}", msg.getType());
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, Message msg) {
        tunnelName = msg.getTunnelName();
        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);

        if (tunnel == null) {
            log.error("Tunnel not found: {}", tunnelName);
            Message response = new Message(MessageType.REGISTER_FAIL);
            response.setMetadata("Tunnel not found");
            ctx.writeAndFlush(response);
            ctx.close();
            return;
        }

        tunnelManager.agentConnected(tunnelName, ctx.channel());

        Message response = new Message(MessageType.REGISTER_SUCCESS);
        response.setTunnelName(tunnelName);
        ctx.writeAndFlush(response);

        log.info("Agent registered successfully: {}", tunnelName);
    }

    private void handleHeartbeat(ChannelHandlerContext ctx) {
        Message response = new Message(MessageType.HEARTBEAT_ACK);
        ctx.writeAndFlush(response);
    }

    private void handleConnectAck(Message msg) {
        String channelId = msg.getChannelId();
        boolean success = "success".equals(msg.getMetadata());

        ClientProxyHandler handler = tunnelManager.getClientHandler(tunnelName, channelId);
        if (handler != null) {
            handler.onAgentConnected(success);
            log.info("Agent connection ack received: channelId={}, success={}", channelId, success);
        } else {
            log.warn("Handler not found for channelId: {}", channelId);
        }
    }

    private void handleData(Message msg) {
        String channelId = msg.getChannelId();
        Channel clientChannel = tunnelManager.getClientChannel(tunnelName, channelId);

        if (clientChannel != null && clientChannel.isActive()) {
            // 写入数据并等待完成
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData())).addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("Forward data to client: channelId={}, size={}", channelId, msg.getData().length);
                } else {
                    log.error("Failed to forward data to client: channelId={}", channelId, future.cause());
                    clientChannel.close();
                    tunnelManager.removeClientChannel(tunnelName, channelId);
                }
            });
        } else {
            log.warn("Client channel not found or inactive: {}", channelId);
        }
    }

    private void handleDisconnect(Message msg) {
        String channelId = msg.getChannelId();
        Channel clientChannel = tunnelManager.getClientChannel(tunnelName, channelId);

        if (clientChannel != null) {
            // 延迟关闭，确保所有数据都发送完成
            clientChannel.eventLoop().schedule(() -> {
                clientChannel.close();
                tunnelManager.removeClientChannel(tunnelName, channelId);
                log.info("Client channel closed: {}", channelId);
            }, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (tunnelName != null) {
            tunnelManager.agentDisconnected(tunnelName);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in agent handler", cause);
        ctx.close();
    }
}
