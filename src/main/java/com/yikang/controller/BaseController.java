package com.yikang.controller;


import com.yikang.error.BusinessException;
import com.yikang.error.EmBusinessError;
import com.yikang.response.CommonReturnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class BaseController {

    public final static String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

//    final Logger logger = LoggerFactory.getLogger(BaseController.class);
//
//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    public Object handlerException(HttpServletRequest request, Exception e) {
//        Map<String, Object> responseData = new HashMap<>();
//        CommonReturnType commonReturnType = new CommonReturnType();
//        if (e instanceof BusinessException) {
//            BusinessException businessException = (BusinessException) e;
//            responseData.put("errCode", businessException.getErrorCode());
//            responseData.put("errMsg", businessException.getErrorMsg());
//        } else {
//            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrorCode());
//            responseData.put("errMsg", EmBusinessError.UNKNOWN_ERROR.getErrorMsg());
//            logger.error(e.getMessage());
//        }
//        commonReturnType.setStatus("fail");
//        commonReturnType.setData(responseData);
//        return commonReturnType;
//    }
}
