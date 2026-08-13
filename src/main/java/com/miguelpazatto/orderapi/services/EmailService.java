package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void enviarEmailAtualizacaoPagamento(UUID orderId, PaymentStatus status, String emailDestino) {

        log.info("📧 Montando o e-mail para enviar ao cliente: {}", emailDestino);

        SimpleMailMessage mensagemCliente = new SimpleMailMessage();
        mensagemCliente.setFrom("nao-responda@orderapi.com.br");
        mensagemCliente.setTo(emailDestino);

        if (status == PaymentStatus.APPROVED) {
            mensagemCliente.setSubject("Pagamento Aprovado! Pedido #" + orderId);
            mensagemCliente.setText("Olá!\n\nSeu pagamento foi aprovado com sucesso. Seu pedido já está sendo separado em nosso centro de distribuição.\n\nObrigado por comprar conosco!");
            javaMailSender.send(mensagemCliente);

            log.info("🛒 Notificando o setor de logística/dono sobre a nova venda...");
            SimpleMailMessage mensagemDono = new SimpleMailMessage();
            mensagemDono.setFrom("sistema@sualoja.com.br");
            mensagemDono.setTo("admin@sualoja.com.br");

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
    }

}
