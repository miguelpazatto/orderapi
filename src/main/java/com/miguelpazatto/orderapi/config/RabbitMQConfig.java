package com.miguelpazatto.orderapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ORDER AS PRODUCTOR CONFIG

    public static final String FILA_PAGAMENTO = "pagamento.queue";
    public static final String EXCHANGE_PEDIDOS = "pedidos.exchange";
    public static final String ROTA_PEDIDO_CRIADO = "pedido.criado.routing.key";

    @Bean
    public Queue pagamentoQueue() {
        return new Queue(FILA_PAGAMENTO, true);
    }

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE_PEDIDOS);
    }

    @Bean
    public Binding pagamentoBinding(Queue pagamentoQueue, DirectExchange pedidosExchange) {
        return BindingBuilder.bind(pagamentoQueue).to(pedidosExchange).with(ROTA_PEDIDO_CRIADO);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // PAYMENT AS PRODUCTOR CONFIG

    private static final String FILA_STATUS_PAGAMENTO = "status.pagemento.queue";
    private static final
}
