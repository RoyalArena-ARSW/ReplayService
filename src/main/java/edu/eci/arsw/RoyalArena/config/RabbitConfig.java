package edu.eci.arsw.RoyalArena.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumidor de los paquetes de replay. Declara SU cola y la bindea al exchange
 * compartido. Game Engine no sabe que este servicio existe: solo publica al
 * exchange con la routing key 'replay.recorded', y quien quiera esos eventos se
 * suscribe. Profile escucha 'match.finished' en el mismo exchange; este servicio
 * escucha 'replay.recorded'. Dos consumidores independientes, un solo productor.
 */
@Configuration
public class RabbitConfig {

    @Value("${royalarena.events.exchange}")
    private String exchangeName;

    @Value("${royalarena.events.queue.replay-recorded}")
    private String queueName;

    @Value("${royalarena.events.routing-key.replay-recorded}")
    private String routingKey;

    @Bean
    public TopicExchange matchesExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue replayQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", queueName + ".dlq")
                .build();
    }

    @Bean
    public Queue replayDlq() {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    @Bean
    public Binding replayBinding(Queue replayQueue, TopicExchange matchesExchange) {
        return BindingBuilder.bind(replayQueue).to(matchesExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}