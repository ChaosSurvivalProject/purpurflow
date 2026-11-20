package com.purpurflow.common.protocol;

/** 帧解析/编码异常。触发时调用方应计入安全计数并丢弃该帧，绝不抛出到主线程。 */
public class FrameException extends RuntimeException {
    public FrameException(String message) {
        super(message);
    }

    public FrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
