package com.yikang.mq;

import com.alibaba.fastjson.JSON;
import com.yikang.error.BusinessException;
import com.yikang.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProducer {
    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @PostConstruct
    public void init() throws MQClientException {
        //做mq producer的初始化
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();
        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        //当createOrder方法执行成功后，consumer端才会执行
        //即createOrder失败回滚时，consumer端也一起回滚
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                //创建订单
                Integer userId = (Integer) ((Map) o).get("userId");
                Integer promoId = (Integer) ((Map) o).get("promoId");
                Integer itemId = (Integer) ((Map) o).get("itemId");
                Integer amount = (Integer) ((Map) o).get("amount");
                try {
                    orderService.createOrder(userId, itemId, amount, promoId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //注意这里没有抛出异常，因此需要在上层调用getLocalTransactionState方法来了解异常情况
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            //当executeLocalTransaction一直没有发送明确的消息（可能断链，也可能延时）
            //那么会调用checkLocalTransaction方法
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功，来判断要返回COMMIT、ROLLBACK还是继续UNKNOWN
                String jsonString = new String(msg.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                return null;
            }
        });
    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer promoId, Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);

        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = null;
        try {
            sendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        //让上层了解错误信息
        if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            return false;
        } else if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
            return true;
        } else {
            return false;
        }
    }

    //同步库存扣减消息
    public boolean asyncReduceStock(Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
