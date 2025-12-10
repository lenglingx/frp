package com.imddy.frp.common.protocol;

public enum MessageType {
    // 控制消息
    REGISTER(1),           // Agent注册
    REGISTER_SUCCESS(2),   // 注册成功
    REGISTER_FAIL(3),      // 注册失败
    HEARTBEAT(4),          // 心跳
    HEARTBEAT_ACK(5),      // 心跳响应

    // 数据传输
    CONNECT(10),           // 新连接通知
    CONNECT_ACK(11),       // 连接确认（新增）
    DISCONNECT(12),        // 断开连接
    DATA(13),              // 数据传输

    // 管理消息
    QUERY_STATUS(20),      // 查询状态
    STATUS_RESPONSE(21);   // 状态响应

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}
