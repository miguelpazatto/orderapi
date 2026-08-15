package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.remetente}")
    private String remetente;

    @Value("${app.mail.admin}")
    private String emailAdmin;

    public void enviarEmailAtualizacaoPagamento(UUID orderId, PaymentStatus status, String emailDestino) {
        try {
            log.info("Montando o e-mail para enviar ao cliente: {}", emailDestino);

            SimpleMailMessage mensagemCliente = new SimpleMailMessage();
            mensagemCliente.setFrom(remetente);
            mensagemCliente.setTo(emailDestino);

            if (status == PaymentStatus.APPROVED) {
                mensagemCliente.setSubject("Pagamento Aprovado! Pedido #" + orderId);
                mensagemCliente.setText("Olá!\n\nSeu pagamento foi aprovado com sucesso. Seu pedido já está sendo separado em nosso centro de distribuição.\n\nObrigado por comprar conosco!");
                javaMailSender.send(mensagemCliente);

                log.info("Notificando o setor de logística/dono sobre a nova venda...");
                SimpleMailMessage mensagemDono = new SimpleMailMessage();
                mensagemDono.setFrom(remetente);
                mensagemDono.setTo(emailAdmin);

                mensagemDono.setSubject("NOVA VENDA APROVADA! Separar Pedido #" + orderId);
                mensagemDono.setText("Oba! O cliente efetuou o pagamento.\n\n" +
                        "Pedido: #" + orderId + "\n" +
                        "Acesse o painel para separar os itens e marcar como ENVIADO.");
                javaMailSender.send(mensagemDono);

            } else if (status == PaymentStatus.REJECTED) {
                mensagemCliente.setSubject("Problema no Pagamento! Pedido #" + orderId);
                mensagemCliente.setText("Ops!\n\nTivemos um problema para processar o seu pagamento. Por favor, acesse o sistema e tente com outro cartão.");
                javaMailSender.send(mensagemCliente);
            }

            log.info("Disparos de e-mail concluídos com sucesso!");
        } catch (Exception e) {
            log.error("ERRO AO ENVIAR E-MAIL DE PAGAMENTO para o pedido {}: {}", orderId, e.getMessage());
        }
    }

    public void enviarEmailPedidoEnviado(UUID orderId, String emailDestino) {
        try {
            log.info("Disparando e-mail de pedido enviado para: {}", emailDestino);

            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(emailDestino);

            mensagem.setSubject("Seu pedido #" + orderId + " foi enviado!");
            mensagem.setText("Acabamos de separar seu pedido e enviá-lo! Fique atento à entrega");
            javaMailSender.send(mensagem);
        } catch (Exception e) {
            log.error("ERRO AO ENVIAR E-MAIL DE PEDIDO ENVIADO para o pedido {}: {}", orderId, e.getMessage());
        }
    }

    public void enviarEmailConfirmacaoEntrega(UUID orderId, String emailCliente) {

        try {
            log.info("Notificando entrega confirmada do Pedido: {}", orderId);

            SimpleMailMessage mensagemDono = new SimpleMailMessage();
            mensagemDono.setFrom(remetente);
            mensagemDono.setTo(emailAdmin);
            mensagemDono.setSubject("Pedido #" + orderId + " FINALIZADO!");
            mensagemDono.setText("O cliente confirmou o recebimento do pedido através do painel.\n" +
                    "O ciclo desta venda foi concluído com sucesso!");
            javaMailSender.send(mensagemDono);

            SimpleMailMessage mensagemCliente = new SimpleMailMessage();
            mensagemCliente.setFrom(remetente);
            mensagemCliente.setTo(emailCliente);
            mensagemCliente.setSubject("Entrega Confirmada - Pedido #" + orderId);
            mensagemCliente.setText("Olá!\n\nRegistramos a sua confirmação de recebimento do pedido.\n\n" +
                    "Muito obrigado por comprar conosco! Esperamos ver você em breve.");
            javaMailSender.send(mensagemCliente);
        } catch (MailException e) {
            log.error("ERRO AO ENVIAR E-MAIL DE CONFIRMAÇÃO DE ENTREGA para o pedido {}: {}", orderId, e.getMessage());
        }
    }

    public void enviarEmailCancelamentoPorInatividade(UUID orderId, String emailDestino) {
        try {
            log.info("Disparando e-mail de cancelamento por inatividade para: {}", emailDestino);

            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(emailDestino);

            mensagem.setSubject("Aviso: Seu pedido #" + orderId + " foi cancelado");
            mensagem.setText("Olá!\n\n" +
                    "Notamos que o pagamento do seu pedido #" + orderId + " não foi identificado dentro do tempo limite.\n" +
                    "Por esse motivo, seu pedido foi cancelado automaticamente e os itens retornaram ao nosso estoque.\n\n" +
                    "Se você ainda tem interesse nos produtos, fique à vontade para realizar uma nova compra em nosso site!\n\n" +
                    "Atenciosamente,\n" +
                    "Equipe de Vendas");

            javaMailSender.send(mensagem);
        } catch (Exception e) {
            log.error("ERRO AO ENVIAR E-MAIL DE CANCELAMENTO para o pedido {}: {}", orderId, e.getMessage());
        }
    }
}
