package com.geoviz.terrain.validation;

/**
 * 参数非法时抛出的异常，由全局异常处理器转成带原因的 400 响应。
 */
public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
