package com.example.producer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * @author Administrator
 */
@Controller
@RequestMapping("/v1")
public class ProducerController {
    private static final Logger LOGGER = LogManager.getLogger(ProducerController.class);
    @Resource
    private RabbitTemplate template;

    @PostMapping(value = "/consumer")
    @ResponseBody
    public void consumer(@RequestBody String body) {
        LOGGER.info("ask message: {}", body);
        JSONObject jsonParam = JSON.parseObject(body);
        String msg = jsonParam.getString("message");
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(msg.getBytes(), messageProperties);
        template.convertAndSend("route-key", msg, (message1) -> message1, new CorrelationData(UUID.randomUUID().toString()));
        LOGGER.info("send message success");
    }

}
