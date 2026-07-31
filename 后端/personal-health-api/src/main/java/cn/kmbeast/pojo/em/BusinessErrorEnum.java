package cn.kmbeast.pojo.em;

/**
 * 业务异常枚举类
 * 定义系统中所有业务异常类型和错误码
 */
public enum BusinessErrorEnum {

    // 用户相关错误 (1000-1999)
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ACCOUNT_EXISTS(1002, "账号已存在"),
    USER_NAME_EXISTS(1003, "用户名已被使用"),
    USER_PASSWORD_ERROR(1004, "密码错误"),
    USER_LOGIN_EXPIRED(1005, "登录状态异常"),
    USER_ACCESS_DENIED(1006, "无访问权限"),

    // 药品相关错误 (2000-2999)
    DRUG_NOT_FOUND(2001, "药品不存在"),
    DRUG_ALREADY_SUBSCRIBED(2002, "您已订阅该药品"),
    DRUG_SUBSCRIBE_FAILED(2003, "药品订阅失败"),
    DRUG_UNSUBSCRIBE_FAILED(2004, "取消订阅失败"),

    // 健康记录相关错误 (3000-3999)
    HEALTH_RECORD_NOT_FOUND(3001, "健康记录不存在"),
    HEALTH_RECORD_SAVE_FAILED(3002, "健康记录保存失败"),
    HEALTH_RECORD_DELETE_FAILED(3003, "健康记录删除失败"),
    HEALTH_RECORD_UPDATE_FAILED(3004, "健康记录更新失败"),
    HEALTH_MODEL_NOT_FOUND(3005, "健康模型不存在"),
    HEALTH_DATA_IMPORT_FAILED(3006, "健康数据导入失败"),

    // AI相关错误 (4000-4999)
    AI_SERVICE_UNAVAILABLE(4001, "AI服务不可用"),
    AI_REQUEST_FAILED(4002, "AI请求失败"),
    AI_RESPONSE_EMPTY(4003, "AI响应为空"),

    // 新闻相关错误 (5000-5999)
    NEWS_NOT_FOUND(5001, "资讯不存在"),
    NEWS_SAVE_FAILED(5002, "资讯保存失败"),

    // 系统错误 (9000-9999)
    SYSTEM_ERROR(9001, "系统异常，请稍后重试"),
    DATABASE_ERROR(9002, "数据库操作失败"),
    PARAMETER_ERROR(9003, "参数错误"),
    FILE_OPERATION_ERROR(9004, "文件操作失败");

    private final int code;
    private final String message;

    BusinessErrorEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 根据错误码获取枚举
     * @param code 错误码
     * @return 对应的枚举
     */
    public static BusinessErrorEnum getByCode(int code) {
        for (BusinessErrorEnum error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        return SYSTEM_ERROR;
    }
}
