package com.campus.reservation.entity;

/** 通用结果实体：仅封装操作状态、中文消息和数据，不处理业务或文件读写。 */
public class Result<T> {
    private boolean success;
    private String msg;
    private T data;

    /** 创建空结果，供调用方通过 setter 填充。 */
    public Result() {
    }

    /** 供工厂方法构造一致的结果，失败结果不携带数据。 */
    private Result(boolean success, String msg, T data) {
        this.success = success;
        this.msg = msg;
        this.data = success ? data : null;
    }

    /** 成功并携带数据；字符串作为数据时应使用消息加数据的重载。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(true, "操作成功。", data);
    }

    /** 成功且仅携带中文提示，数据为空。 */
    public static <T> Result<T> success(String msg) {
        return new Result<>(true, msg, null);
    }

    /** 成功并使用指定消息和返回数据。 */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(true, msg, data);
    }

    /** 失败结果只携带消息，禁止残留成功数据。 */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(false, msg, null);
    }

    // 访问方法只维护结果结构；设置失败状态时同时清空数据。
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
        if (!success) {
            this.data = null;
        }
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = success ? data : null;
    }

    /** 展示结果的三个字段，便于核对泛型封装。 */
    @Override
    public String toString() {
        return "Result{success=" + success + ", msg='" + msg + "', data=" + data + "}";
    }
}
