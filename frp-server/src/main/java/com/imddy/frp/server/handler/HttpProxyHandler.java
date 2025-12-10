package com.imddy.frp.server.handler;

import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
public class HttpProxyHandler extends SimpleChannelInboundHandler<Object> {
    private final TunnelManager tunnelManager;
    private final String tunnelName;
    private final String channelId;
    private boolean requestSent = false;

    public HttpProxyHandler(TunnelManager tunnelManager, String tunnelName) {
        this.tunnelManager = tunnelManager;
        this.tunnelName = tunnelName;
        this.channelId = UUID.randomUUID().toString();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        tunnelManager.addClientChannel(tunnelName, channelId, ctx.channel());

        TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
        if (tunnel != null && tunnel.getAgentChannel() != null) {
            Message connectMsg = Message.connect(tunnelName, channelId);
            tunnel.getAgentChannel().writeAndFlush(connectMsg);
            log.info("New HTTP client connected: channelId={}, tunnel={}", channelId, tunnelName);
        } else {
            log.error("Agent not connected for tunnel: {}", tunnelName);
            ctx.close();
        }

        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();

        try {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                requestSent = true;

                // 编码HTTP请求行
                String requestLine = request.method().name() + " " + request.uri() + " " + request.protocolVersion() + "\r\n";
                buf.writeBytes(requestLine.getBytes(StandardCharsets.UTF_8));

                // 写入headers
                for (String name : request.headers().names()) {
                    for (String value : request.headers().getAll(name)) {
                        String header = name + ": " + value + "\r\n";
                        buf.writeBytes(header.getBytes(StandardCharsets.UTF_8));
                    }
                }
                buf.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));

                log.info("HTTP Request: {} {} from channelId={}", request.method(), request.uri(), channelId);
            }

            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                ByteBuf contentBuf = content.content();
                if (contentBuf.readableBytes() > 0) {
                    buf.writeBytes(contentBuf);
                }

                // 检查是否是最后一个chunk
                if (msg instanceof LastHttpContent) {
                    log.debug("HTTP request complete for channelId={}", channelId);
                }
            }

            if (buf.readableBytes() > 0) {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
                if (tunnel != null && tunnel.getAgentChannel() != null) {
                    Message dataMsg = Message.data(channelId, data);
                    dataMsg.setTunnelName(tunnelName);
                    tunnel.getAgentChannel().writeAndFlush(dataMsg).addListener(future -> {
                        if (future.isSuccess()) {
                            log.debug("HTTP data forwarded to agent: channelId={}, size={}", channelId, data.length);
                        } else {
                            log.error("Failed to forward HTTP data: channelId={}", channelId, future.cause());
                            ctx.close();
                        }
                    });
                } else {
                    log.error("Agent channel not available");
                    ctx.close();
                }
            }
        } finally {
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 延迟移除，确保响应数据完全发送
        ctx.executor().schedule(() -> {
            tunnelManager.removeClientChannel(tunnelName, channelId);

            TunnelManager.TunnelInfo tunnel = tunnelManager.getTunnel(tunnelName);
            if (tunnel != null && tunnel.getAgentChannel() != null) {
                Message disconnectMsg = Message.disconnect(channelId);
                disconnectMsg.setTunnelName(tunnelName);
                tunnel.getAgentChannel().writeAndFlush(disconnectMsg);
            }

            log.info("HTTP client disconnected: channelId={}, tunnel={}", channelId, tunnelName);
        }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in HTTP proxy handler: channelId={}", channelId, cause);
        ctx.close();
    }
}
