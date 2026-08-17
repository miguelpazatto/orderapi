package com.miguelpazatto.orderapi.core.config;

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

    // ===============================================================
    // --- MÓDULO WEBHOOK STRIPE (Ouvindo o provedor de pagamento) ---
    // ===============================================================

    public static final String FILA_WEBHOOK_SUCESSO = "webhook.pagamento.sucesso.queue";
    public static final String FILA_WEBHOOK_FALHA = "webhook.pagamento.falha.queue";
    public static final String EXCHANGE_WEBHOOK = "webhook.stripe.exchange";
    public static final String ROTA_WEBHOOK_SUCESSO = "stripe.sucesso.routing.key";
    public static final String ROTA_WEBHOOK_FALHA = "stripe.falha.routing.key";

    @Bean
    public Queue webhookSucessoQueue() {
        return new Queue(FILA_WEBHOOK_SUCESSO, true);
    }

    @Bean
    public Queue webhookFalhaQueue() {
        return new Queue(FILA_WEBHOOK_FALHA, true);
    }

    @Bean
    public DirectExchange webhookExchange() {
        return new DirectExchange(EXCHANGE_WEBHOOK);
    }

    @Bean
    public Binding bindingWebhookSucesso(Queue webhookSucessoQueue, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookSucessoQueue).to(webhookExchange).with(ROTA_WEBHOOK_SUCESSO);
    }

    @Bean
    public Binding bindingWebhookFalha(Queue webhookFalhaQueue, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookFalhaQueue).to(webhookExchange).with(ROTA_WEBHOOK_FALHA);
    }

    // ===============================================================
    // --- MÓDULO EXPIRAÇÃO DO PEDIDO ---
    // ===============================================================

    public static final String EXCHANGE_DLX = "pedido.dlx.exchange";
    public static final String FILA_ESPERA_TTL = "pedido.espera.ttl.queue";
    public static final String FILA_CANCELAMENTO_DLQ = "pedido.cancelamento.dlq.queue";
    public static final String ROTA_ESPERA = "pedido.espera.routing.key";
    public static final String ROTA_CANCELAMENTO = "pedido.cancelamento.routing.key";

    @Bean
    public Queue filaEspera() {
        return QueueBuilder.durable(FILA_ESPERA_TTL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROTA_CANCELAMENTO)
                .withArgument("x-message-ttl", 1800000)
                .build();
    }

    @Bean
    public Queue filaCancelamento() {
        return new Queue(FILA_CANCELAMENTO_DLQ, true);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    @Bean
    public Binding bindingFilaEspera(Queue filaEspera, DirectExchange dlxExchange) {
        return BindingBuilder.bind(filaEspera).to(dlxExchange).with(ROTA_ESPERA);
    }

    @Bean
    public Binding bindingFilaCancelamento(Queue filaCancelamento, DirectExchange dlxExchange) {
        return BindingBuilder.bind(filaCancelamento).to(dlxExchange).with(ROTA_CANCELAMENTO);
    }

    // ===============================================================
    // --- MÓDULO AVISO PRÉVIO DE EXPIRAÇÃO (Trilha Paralela)       ---
    // ===============================================================

    public static final String FILA_ESPERA_AVISO_TTL = "pedido.espera.aviso.ttl.queue";
    public static final String FILA_AVISO_DLQ = "pedido.aviso.dlq.queue";

    public static final String ROTA_ESPERA_AVISO = "pedido.espera.aviso.routing.key";
    public static final String ROTA_AVISO = "pedido.aviso.routing.key";

    @Bean
    public Queue filaEsperaAviso() {
        return QueueBuilder.durable(FILA_ESPERA_AVISO_TTL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROTA_AVISO)
                .withArgument("x-message-ttl", 1200000)
                .build();
    }

    @Bean
    public Queue filaAvisoDlq() {
        return new Queue(FILA_AVISO_DLQ, true);
    }

    @Bean
    public Binding bindingFilaEsperaAviso(Queue filaEsperaAviso, DirectExchange dlxExchange) {
        return BindingBuilder.bind(filaEsperaAviso).to(dlxExchange).with(ROTA_ESPERA_AVISO);
    }

    @Bean
    public Binding bindingFilaAvisoDlq(Queue filaAvisoDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(filaAvisoDlq).to(dlxExchange).with(ROTA_AVISO);
    }


}