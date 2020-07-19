# rabbitmq
#### 1. 消息丢失：

开启mq持久化，broker端confirm机制，也有可能出现消息丢失，此时采用持久化补偿机制，即消息投递之前，先进要发送的消息持久化到redis/db，

##### 1.1 broker端丢失

* 事务模式：效率较低，不常用

* confirm模式：生产者开启confirm模式，即`ConnectionFactory.setPublisherConfirms(true)`

  因为confirm模式是异步确认的，即需要给对应的消息一个唯一id，在confirm回调函数中根据消息id拿到对应的发布结果，如果失败，可以重试（可能出现消息重复投递）

##### 1.2 MQ端丢失

解决办法即设置持久化；存在问题：还没来得及持久化消息，mq宕机了

* Queue持久化

  声明持久化

* Message持久化

  即发送消息时，设置持久化，`deliveryMode = 2`

  1. 如果使用`rabbitTemplate`发送消息，设置方式如下

     ```java
      MessageProperties messageProperties = new MessageProperties();
      messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      Message message = new Message(msg.getBytes(), messageProperties);
      rabbitTemplate.convertAndSend("route-key", message);
     ```

  2. 使用`channel`发送消息

     

##### 1.3 Consumer端丢失

Acknowledge模式

关闭MQ的自动ack，设置`RabbitListenerContainerFactory`具体实现类可简单使用`org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory` `factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)`

手动代码中进行ack或者nack

`channel.basicAck(deliveryTag, false)`

#### 2. 消息重复：

消费端根据消息做唯一性去重

#### 3. 消息顺序传递





