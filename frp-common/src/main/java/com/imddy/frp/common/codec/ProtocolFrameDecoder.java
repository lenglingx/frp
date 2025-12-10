package com.imddy.frp.common.codec;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 基于长度的帧解码器，解决粘包拆包问题
 */
public class ProtocolFrameDecoder extends LengthFieldBasedFrameDecoder {
    private static final int MAX_FRAME_LENGTH = 1024 * 1024 * 10; // 10MB
    private static final int LENGTH_FIELD_OFFSET = 2 + 1 + 2; // magic(2) + type(1) + tunnelNameLen(2)
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = -9; // 调整偏移
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public ProtocolFrameDecoder() {
        super(MAX_FRAME_LENGTH, 0, 4, 0, 0);
    }
}
