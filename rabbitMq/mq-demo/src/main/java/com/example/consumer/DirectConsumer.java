package com.example.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * DirectExchange对应的consumer类
 * 1. work-queue: consumer、consumer1监听同一个队列，queue队列里的消息会均分下发到对应的listener中
 * 2. publish/subscribe(广播):
 * 2.1 exchange为DirectExchange，consumer2 、consumer 监听不同的队列，但是通过同一个routeKey绑定同一个exchange
 * 2.2 exchange为FanoutExchange，消息给exchange对应的所有queue
 * 3.
 */
@Component
public class DirectConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectConsumer.class);

    @RabbitListener(containerFactory = "myFactory", bindings = {@QueueBinding(value = @Queue(value = "direct-queue-test"), exchange = @Exchange(value = "direct-exchange-test"), key = "route-key")})
    public void consumer(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        LOGGER.info(message.getMessageProperties().getHeaders().toString());
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        LOGGER.info("consumer reciver msg: {}", msg);
        int i = 1 / 0;
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(containerFactory = "myFactory", bindings = {@QueueBinding(value = @Queue(value = "direct-queue-test"), exchange = @Exchange(value = "direct-exchange-test"), key = "route-key")})
    public void consumer1(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        LOGGER.info(message.getMessageProperties().getHeaders().toString());
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        LOGGER.info("consumer1 reciver msg: {}", msg);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(containerFactory = "myFactory", bindings = {@QueueBinding(value = @Queue(value = "direct-queue-test-1"), exchange = @Exchange(value = "direct-exchange-test"), key = "route-key")})
    public void consumer2(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        LOGGER.info(message.getMessageProperties().getHeaders().toString());
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        LOGGER.info("consumer2 reciver msg: {}", msg);
        channel.basicAck(deliveryTag, false);
    }
}
