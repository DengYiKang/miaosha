package com.yikang.service.impl;

import com.yikang.dao.OrderDOMapper;
import com.yikang.dao.SequenceDOMapper;
import com.yikang.dao.StockLogDOMapper;
import com.yikang.dataobject.OrderDO;
import com.yikang.dataobject.SequenceDO;
import com.yikang.dataobject.StockLogDO;
import com.yikang.error.BusinessException;
import com.yikang.error.EmBusinessError;
import com.yikang.service.ItemService;
import com.yikang.service.OrderService;
import com.yikang.service.UserService;
import com.yikang.service.model.ItemModel;
import com.yikang.service.model.OrderModel;
import com.yikang.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer amount, Integer promoId, String stockLogId) throws BusinessException {
        //1. 检验下单状态，下单商品是否存在，用户是否合法，购买数量是否正确
        //ItemModel itemModel = itemService.getItemById(itemId);
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }
        //UserModel userModel = userService.getUserById(userId);
        UserModel userModel = userService.getUserByIdInCache(userId);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户信息不存在");
        }
        if (amount <= 0 || amount > 99) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }

        //校验活动信息
        if (promoId != null) {
            //1. 检验对应活动是否存在这个适用商品
            if (promoId.intValue() != itemModel.getPromoModel().getId()) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀活动信息不正确");
            } else if (itemModel.getPromoModel().getStatus() != 2) {
                //校验活动是否在进行中
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀活动尚未开始");
            }
        }

        //2. 落单减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if (!result) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        //3. 订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if (promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //生成交易流水号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = this.convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //加上商品的销量
        itemService.increaseSales(itemId, amount);


        //设置库存流水状态成功
        //注意这没有行锁竞争，所以引入一个StockLog的操作对性能的影响很小
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "库存流水不存在");
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//            @Override
//            public void afterCommit() {
//                //异步更新库存
//                boolean mqresult = itemService.asyncDecreaseStock(itemId, amount);
//                //如果将异步发送消息的方法放在外面，即先异步发送消息，然后再commit，但是因为网络的原因等等也可能commit失败，这种情况下这个异步更新就不能回滚了
//                //但是如果指定异步更新在commit成功后进行，也存在一个问题，就是如果异步更新失败，throw出的runtimeException将不会回滚事务，因为事务已经commit了
////                if (!mqresult) {
////                    itemService.increaseStock(itemId, amount);
////                    throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
////                }
//            }
//        });


        //4. 返回前端
        return orderModel;
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel) {
        if (orderModel == null) {
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        return orderDO;
    }

    //这里需要开启新的事务
    //generateOrderNo方法被createOder方法调用，而createOrder方法也是事务的
    //那么意味着，如果在生成订单号之后createOrder某些方法失败了，那么订单号也会回滚
    //但是订单号本身不允许回滚，只要生成了就不能回滚
    //因此需要为这个方法开启新的事务，与createOrder的事务相隔离
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNo() {
        StringBuilder stringBuilder = new StringBuilder();
        //订单号有16位 前8位为时间信息，年月日，中间6位为自增序列，最后2位为分库分表位暂时写死
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);
        //获取当前sequence
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKey(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for (int i = 0; i < 6 - sequenceStr.length(); i++) {
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);
        stringBuilder.append("00");
        return stringBuilder.toString();
    }
}
