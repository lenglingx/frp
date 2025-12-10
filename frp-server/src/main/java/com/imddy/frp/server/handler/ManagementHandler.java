package com.imddy.frp.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ManagementHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final TunnelManager tunnelManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManagementHandler(TunnelManager tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        if ("/api/tunnels".equals(uri)) {
            handleGetTunnels(ctx);
        } else if ("/api/status".equals(uri)) {
            handleGetStatus(ctx);
        } else {
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
        }
    }

    private void handleGetTunnels(ChannelHandlerContext ctx) throws Exception {
        Map<String, TunnelManager.TunnelInfo> tunnels = tunnelManager.getAllTunnels();
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, TunnelManager.TunnelInfo> entry : tunnels.entrySet()) {
            Map<String, Object> info = new HashMap<>();
            TunnelManager.TunnelInfo tunnel = entry.getValue();
            info.put("name", tunnel.getConfig().getName());
            info.put("type", tunnel.getConfig().getType());
            info.put("openPort", tunnel.getConfig().getOpenPort());
            info.put("agentPort", tunnel.getConfig().getAgentPort());
            info.put("status", tunnel.getStatus().name());
            info.put("connected", tunnel.getAgentChannel() != null && tunnel.getAgentChannel().isActive());
            result.put(entry.getKey(), info);
        }

        String json = objectMapper.writeValueAsString(result);
        sendJsonResponse(ctx, json);
    }

    private void handleGetStatus(ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> status = new HashMap<>();
        status.put("running", true);
        status.put("tunnelCount", tunnelManager.getAllTunnels().size());

        String json = objectMapper.writeValueAsString(status);
        sendJsonResponse(ctx, json);
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, String json) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(json, StandardCharsets.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(content, StandardCharsets.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in management handler", cause);
        ctx.close();
    }
}
