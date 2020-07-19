package com.example.configuration;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MQCommonConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MQConfiguration.class);

    /**
     * connection 连接池工程，初始化连接信息,用户名，密码等等
     *
     * @return org.springframework.amqp.rabbit.connection.ConnectionFactory
     */
    @Bean
    @ConditionalOnClass
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");
        // 如果是broker端并且需要有确认操作，必须设置publisherConfirms为true
        factory.setPublisherConfirms(true);
        // 回调方法
        factory.setPublisherReturns(true);
        return factory;
    }

    /**
     * rabbitmq 通信途径
     *
     * @param connectionFactory rabbitmq的连接池工厂
     * @return channel 根据连接池工厂创建对应的channel
     * @throws IOException connection无法正常创建channel
     */
    @Bean
    @ConditionalOnClass
    public Channel channel(ConnectionFactory connectionFactory) throws IOException {
        // 是否开启事务channel
        Channel channel = connectionFactory.createConnection().createChannel(false);
        // 给consumer来用，即consumerack之前，给consumer分发消息的数量；
        // These settings impose limits on the amount of data the server will deliver to consumers before
        // requiring acknowledgements.Thus they provide a means of consumer-initiated flow control.
        // https://www.rabbitmq.com/tutorials/tutorial-two-java.html #Fair dispatch
        channel.basicQos(1, true);
        return channel;
    }

    /**
     * 声明exchange，一般分为：
     * directExchange: 默认的exchange模式
     * fanoutExchange: 广播模式
     * topicExchange: 话题模式，通配符匹配
     * headersExchange
     *
     * @return
     */
    @Bean
    @ConditionalOnClass
    public Exchange exchange() {
        return ExchangeBuilder.directExchange("direct-exchange-test").build();
    }

    /**
     * Queue
     *
     *
     * @return
     */
    public Queue queue() {
        return new Queue("name", true, true, true);
    }

    /**
     * broker、consumer连接rabbitmq的注意途径
     *
     * @param factory  连接池工厂
     * @param exchange exchange
     * @return 可用的rabbitTemplate
     */
    @Bean
    @ConditionalOnClass
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory factory, Exchange exchange) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(factory);
        // broker端confirm调用函数
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // 已经正确发送到mq
                LOGGER.info("message id: {}", correlationData.getId());
            } else {
                LOGGER.error("message id : {}, cause: {}", correlationData.getId(), cause);
            }
        });

        //是否有回调，回调具体作用
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnCallback((message, replyCode, replyText,
                                          exchang, routingKey) -> {
            LOGGER.info("message : {}, replycode: {}, replyText: {}, exchange : {}, routingkey: {}", new String(message.getBody()), replyCode, replyText,
                    exchang, routingKey);
        });
        rabbitTemplate.setExchange(exchange.getName());
        return rabbitTemplate;
    }

    /**
     * consumer端的RabbitListenerContainerFactory，监听队列的ListenerContainerFactory
     *
     * @param configurer        config
     * @param connectionFactory connection连接池工厂
     * @return RabbitListenerContainerFactory给RabbitListener使用
     */
    @Bean
    @ConditionalOnClass
    public SimpleRabbitListenerContainerFactory myFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, CachingConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // 设置ack模式，不设置默认为AUTO
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
