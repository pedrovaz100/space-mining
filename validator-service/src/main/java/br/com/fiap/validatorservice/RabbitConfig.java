package br.com.fiap.validatorservice;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Exchange: recebe e encaminha as mensagens de mineração
    public static final String EXCHANGE_NAME = "mining-exchange";
    // Queue: armazena os comandos até o mining-service processar
    public static final String QUEUE_NAME = "mining-queue";
    // Routing Key: como a mensagem vai da exchange para a fila
    public static final String ROUTING_KEY = "mining-key";

    @Bean
    public Queue miningQueue() {
        return new Queue(QUEUE_NAME, true); // durável
    }

    @Bean
    public TopicExchange miningExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding miningBinding() {
        return BindingBuilder
                .bind(miningQueue())
                .to(miningExchange())
                .with(ROUTING_KEY);
    }

}
