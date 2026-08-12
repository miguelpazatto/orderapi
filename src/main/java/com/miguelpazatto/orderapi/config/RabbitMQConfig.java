package com.miguelpazatto.orderapi.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ===============================================================
    // --- MÓDULO DE PEDIDOS (Dispara quando o pedido nasce)       ---
    // ===============================================================

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


    // ===============================================================
    // --- MÓDULO DE PAGAMENTOS (Dispara quando finaliza análise)  ---
    // ===============================================================

    public static final String FILA_STATUS_PAGAMENTO = "status.pagamento.queue";
    public static final String FILA_NOTIFICACAO = "notificacao.queue";
    public static final String EXCHANGE_PAGAMENTO_CONCLUIDO = "pagamento.concluido.exchange";

    @Bean
    public Queue pagamentoStatusQueue() {
        return new Queue(FILA_STATUS_PAGAMENTO, true);
    }

    @Bean
    public Queue notificacaoQueue() {
        return new Queue(FILA_NOTIFICACAO, true);
    }

    @Bean
    public FanoutExchange pagamentoConcluidoExchange() {
        return new FanoutExchange(EXCHANGE_PAGAMENTO_CONCLUIDO);
    }

    @Bean
    public Binding bindingStatusPagamento(Queue pagamentoStatusQueue, FanoutExchange pagamentoConcluidoExchange) {
        return BindingBuilder.bind(pagamentoStatusQueue).to(pagamentoConcluidoExchange);
    }

    @Bean
    public Binding bindingNotificacao(Queue notificacaoQueue, FanoutExchange pagamentoConcluidoExchange) {
        return BindingBuilder.bind(notificacaoQueue).to(pagamentoConcluidoExchange);
    }
}