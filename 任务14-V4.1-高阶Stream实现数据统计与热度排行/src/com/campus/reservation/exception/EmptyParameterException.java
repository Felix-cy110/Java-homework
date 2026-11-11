package com.campus.reservation.exception;

import com.campus.reservation.common.BusinessException;


/** 参数为空的业务异常，供账号和密码校验共用。 */
public class EmptyParameterException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 根据固定字段名称生成中文提示，不包含用户输入。 */
    public EmptyParameterException(String fieldName) {
        super(fieldName + "不能为空。");
    }
}
