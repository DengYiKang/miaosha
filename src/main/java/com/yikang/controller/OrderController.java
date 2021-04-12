package com.yikang.controller;

import com.yikang.error.BusinessException;
import com.yikang.error.EmBusinessError;
import com.yikang.mq.MqProducer;
import com.yikang.response.CommonReturnType;
import com.yikang.service.ItemService;
import com.yikang.service.OrderService;
import com.yikang.service.model.OrderModel;
import com.yikang.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.BatchUpdateException;

@Controller("order")
@RequestMapping("/order")
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {
        //Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户尚未登录");
        }
        //获取用户登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户尚未登录");
        }
        //UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId, amount, promoId);

        //加入库存流水init状态
        String stockLogId = itemService.initStockLog(itemId, amount);

        //再去完成对应的下单事务型消息
        if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), promoId, itemId, amount, stockLogId)) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
        }
        return CommonReturnType.create(null);
    }
}
