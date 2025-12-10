package com.imddy.frp.server.handler;

import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UdpProxyHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final TunnelManager tunnelManager;
    private final String tunnelName;
    private final Map<InetSocketAddress, String> addressToChannelId = new ConcurrentHashMap<>();

    public UdpProxyHandler(TunnelManager tunnelManager, String tunnelName) {
        this.tunnelManager = tunnelManager;
        this.tunnelName = tunnelName;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress sender = packet.sender();
        String channelId = addressToChannelId.computeIfAbsent(sender,
                addr -> addr.getAddress().getHostAddress() + ":" + addr.getPort());

        ByteBuf content = packet.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);

        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
        if (tunnel != null && tunnel.getAgentChannel() != null) {
            Message dataMsg = Message.data(channelId, data);
            dataMsg.setTunnelName(tunnelName);
            dataMsg.setMetadata("{\"sender\":\"" + sender.toString() + "\"}");
            tunnel.getAgentChannel().writeAndFlush(dataMsg);
            log.debug("UDP packet from {}, size: {}", sender, data.length);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in UDP proxy handler", cause);
    }
}
