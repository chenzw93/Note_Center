package com.example;

import com.example.configuration.MQConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MQConfiguration.class)
public class SpringbootMqProducorApplication {
    private static final Logger LOGGER = LogManager.getLogger(SpringbootMqProducorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SpringbootMqProducorApplication.class, args);
    }

//    @Bean
//    @ConditionalOnClass
//    public CachingConnectionFactory connectionFactory() {
//        CachingConnectionFactory factory = new CachingConnectionFactory();
//        factory.setHost("127.0.0.1");
//        factory.setUsername("guest");
//        factory.setPassword("guest");
//        factory.setVirtualHost("/");
//        factory.setPublisherConfirms(true);
//        factory.setPublisherReturns(true);
//        return factory;
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public Channel channel(CachingConnectionFactory factory) {
//        Channel channel = null;
//        try {
//            Connection connection = factory.createConnection();
//            channel = connection.createChannel(false);
//            channel.basicQos(1);
//        } catch (Exception e) {
//            LOGGER.error("create channel fail, e: {}", e);
//        }
//        return channel;
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public FanoutExchange fanoutExchange(MQConfiguration mqConfiguration) {
//        return new FanoutExchange(mqConfiguration.getExchangeName(), false, false);
//    }

//    @Bean
//    @ConditionalOnClass
//    public Queue yearQueue(MQConfiguration mqConfiguration) {
//        return new Queue(mqConfiguration.getYearQueue(), false);
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public Queue monthQueue(MQConfiguration mqConfiguration) {
//        return new Queue(mqConfiguration.getMonthQueue(), false);
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public FanoutExchange exchange(MQConfiguration mqConfiguration) {
//        return new FanoutExchange(mqConfiguration.getExchangeName(), false, false);
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public Binding monthBinding(FanoutExchange exchange, Queue monthQueue) {
//        return BindingBuilder.bind(monthQueue).to(exchange);
//    }
//
//    @Bean
//    @ConditionalOnClass
//    public Binding yearBinding(FanoutExchange exchange, Queue yearQueue) {
//        return BindingBuilder.bind(yearQueue).to(exchange);
//    }


//    @Bean
//    @ConditionalOnClass
//    public RabbitTemplate template(CachingConnectionFactory connectionFactory, MQConfiguration mqConfiguration) {
//        RabbitTemplate template = new RabbitTemplate(connectionFactory);
//        template.setExchange(mqConfiguration.getExchangeName());
//        // 找到对应的exchange
//        template.setConfirmCallback((correlationData, ack, cause) -> {
//            LOGGER.info("confirm callback publish --> status of publihs message: {}", ack);
//            LOGGER.info("confirm callback publish --> correlationData: {}", correlationData);
//            LOGGER.info("confirm callback publish --> cause: {}", cause);
//        });
//        template.setMandatory(true);
//        template.setReturnCallback((message, replyCode, replyText,
//                                    exchange, routingKey) -> {
//            LOGGER.info("return callbak exchange: {}, routingKey: {}, message:{}", exchange, routingKey, new String(message.getBody(), StandardCharsets.UTF_8));
//        });
//        return template;
//    }

//    @Bean
//    @ConditionalOnClass
//    public RabbitTemplate template(CachingConnectionFactory connectionFactory) {
//        RabbitTemplate template = new RabbitTemplate(connectionFactory);
//        template.setConfirmCallback((correlationData, ack, cause) -> {
//            LOGGER.info("confirm callback publish --> status of publihs message: {}", ack);
//            LOGGER.info("confirm callback publish --> correlationData: {}", correlationData);
//            LOGGER.info("confirm callback publish --> cause: {}", cause);
//        });
//        // 如果设置ReturnCallback，必须将Mandatory设置为true
//        template.setMandatory(true);
//        template.setReturnCallback((message, replyCode, replyText,
//                                    exchange, routingKey) -> {
//            LOGGER.info("return callbak exchange: {}, routingKey: {}, message:{}", exchange, routingKey, new String(message.getBody(), StandardCharsets.UTF_8));
//        });
//        return template;
//    }
}
