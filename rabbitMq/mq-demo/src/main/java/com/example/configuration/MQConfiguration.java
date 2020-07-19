package com.example.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mq.config")
public class MQConfiguration {
    private String exchangeName;
    private String yearQueue;
    private String monthQueue;

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getYearQueue() {
        return yearQueue;
    }

    public void setYearQueue(String yearQueue) {
        this.yearQueue = yearQueue;
    }

    public String getMonthQueue() {
        return monthQueue;
    }

    public void setMonthQueue(String monthQueue) {
        this.monthQueue = monthQueue;
    }
}
