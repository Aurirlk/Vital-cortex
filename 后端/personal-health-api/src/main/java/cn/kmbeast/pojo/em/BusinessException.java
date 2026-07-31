package cn.kmbeast.pojo.em;

import lombok.Getter;

/**
 * 自定义业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;

    public BusinessException(BusinessErrorEnum errorEnum) {
        super(errorEnum.getMessage());
        this.code = errorEnum.getCode();
        this.message = errorEnum.getMessage();
    }

    public BusinessException(BusinessErrorEnum errorEnum, String detail) {
        super(errorEnum.getMessage() + ": " + detail);
        this.code = errorEnum.getCode();
        this.message = errorEnum.getMessage() + ": " + detail;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
