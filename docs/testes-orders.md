# Guia dos testes de pedidos

Este documento explica as alterações e ensina a executar e ampliar os testes. Os dados de teste são fictícios. Não é necessário ler ou modificar `.env`, nem iniciar a aplicação no perfil de desenvolvimento.

## Estrutura criada

```text
src/test/java/com/miguelpazatto/orderapi/
├── unit/
│   ├── orders/
│   │   ├── entities/OrderTest.java
│   │   └── services/OrderServiceTest.java
│   └── core/services/EmailServiceTest.java
└── integration/orders/
    ├── OrderIntegrationTest.java
    └── OrderMessagingIntegrationTest.java
```

A estrutura de entidade, serviço e integração segue a suíte de produtos. Os nomes `should...` descrevem comportamentos; os testes parametrizados repetem a mesma regra para várias entradas. As verificações se concentram no resultado e nos efeitos relevantes, sem comparar todos os campos de todos os objetos.

## O que mudou na aplicação e por quê

| Arquivo | Alteração | Problema que resolve |
|---|---|---|
| `OrderController` | `@Valid` no corpo do cadastro | Ativa as restrições já presentes nos DTOs e rejeita entradas inválidas com `400`. |
| `OrderRequestDTO` | Validação dos elementos e rejeição de item nulo | Impede listas como `[null]`; mantém validação de produto e quantidade de cada item. |
| `OrderItem` | Quantidade de 1 a 999 e preço positivo, ambos não nulos | Preserva as regras também na construção direta do domínio, fora do HTTP. |
| `OrderService` | Usa `UserRole.ADMIN`, em vez de comparar enums com strings | Corrige listagem administrativa e a verificação de propriedade. |
| `OrderService` | Valida o proprietário na criação, consulta e cancelamento | Cliente não pode operar pedidos de outro cliente. Administrador pode operar para qualquer cliente. |
| `CustomerService` | `findEntityById` | Permite verificar o estado ativo do cliente na criação do pedido. |
| `OrderService` | Bloqueia compra para cliente/produto inativo | Evita novas compras de cadastros indisponíveis. |
| `OrderService` | Valida a quantidade depois de agrupar produtos repetidos | Duas linhas de 600 unidades não contornam o limite de 999 unidades por produto. |
| `OrderService` | Agrupamento com `LinkedHashMap` | Preserva a ordem de processamento dos itens; o cenário de rollback consegue demonstrar falha depois de uma baixa anterior. |
| `OrderService` | Extrai `cancelAndRestoreStock` | Cancelamento HTTP exige propriedade; expiração interna reutiliza a devolução sem depender de login. A transação continua nos métodos públicos. |
| `OrderService` | Usa `ROTA_PEDIDO_DESPACHADO` ao publicar despacho | O binding RabbitMQ usa a chave de roteamento, que é diferente do nome da fila. |
| `Order.markAsPaid` | Aprovação repetida preserva `PAID`, `SHIPPED` e `DELIVERED` | Evento atrasado não regride um pedido enviado/entregue. Cancelado continua rejeitando aprovação. |

Na infraestrutura de testes, `EmailService` passou a ser `protected` para permitir `verify` nas subclasses. `@DirtiesContext(AFTER_CLASS)` fecha o contexto Spring após cada classe de integração: evita reutilizar conexões de um PostgreSQL cujo container já foi encerrado. Isso aumenta um pouco o tempo de inicialização, mas mantém o isolamento entre configurações com mocks e com RabbitMQ real.

As seguintes decisões de negócio foram adotadas explicitamente:

- Cliente cria pedidos apenas para seu próprio perfil; administrador pode criar para outro cliente.
- Cliente e produto inativos não recebem novas compras.
- O limite de 999 vale para a quantidade total do mesmo produto no pedido.
- Mantida a possibilidade existente de cancelar pedido pago. Esse cancelamento devolve estoque, mas **não implementa reembolso**.
- Não foram adicionados endpoints HTTP para aprovar pagamentos ou expirar pedidos; são operações internas.

## Qual é a diferença entre os níveis de teste?

| Classe | Componentes reais | Componentes substituídos |
|---|---|---|
| `OrderTest` | Entidade e itens | Nenhum servidor ou dependência externa |
| `OrderServiceTest` | Serviço, entidades e contexto de autenticação em memória | Repositório, serviços de clientes/produtos, RabbitTemplate e e-mail |
| `EmailServiceTest` | Composição das mensagens pelo EmailService | `JavaMailSender`, sem conexão SMTP |
| `OrderIntegrationTest` | HTTP, segurança/JWT, serviços, JPA, migrations e PostgreSQL | RabbitTemplate e EmailService |
| `OrderMessagingIntegrationTest` | HTTP, serviços, PostgreSQL, RabbitMQ, exchanges, bindings e conversor JSON | EmailService; consumidores automáticos ficam desabilitados |

Um mock de RabbitTemplate comprova que a aplicação solicitou uma publicação com determinados argumentos. Não comprova conexão, roteamento ou entrega. Por isso há uma classe adicional usando um broker real.

## Testes unitários da entidade

`OrderTest` cobre construção, vínculo bidirecional dos itens, instante da compra, total decimal exato, ausência de itens, quantidade/preço inválidos, limite de quantidade e propriedade do pedido.

As mudanças de estado são parametrizadas com `@EnumSource`: cancelamento permitido e proibido, envio somente após pagamento, entrega somente após envio, falha de pagamento em pedidos pendentes e aprovação repetida sem regressão de estado. Os estados são preparados por métodos do domínio, sem setters artificiais.

## Testes unitários do serviço

`@ExtendWith(MockitoExtension.class)` inicia os mocks. `@InjectMocks` constrói OrderService com essas dependências.

```java
when(orderRepository.findById(id)).thenReturn(Optional.of(order));
```

Isso fornece uma resposta controlada para a consulta. Após executar o serviço:

```java
verify(emailService).enviarEmailPedidoEnviado(id, email);
```

Essa verificação significa que a solicitação de e-mail foi feita uma vez. Em um envio inválido, `verifyNoInteractions(emailService, rabbitTemplate)` comprova que os efeitos de sucesso não foram solicitados.

`ArgumentCaptor<OrderCreatedEventDTO>` captura a mensagem criada para verificar ID, cliente e valor total. Não basta verificar apenas que algum objeto foi enviado.

Os testes também cobrem listagens por papel, perfil ausente, propriedade, criação/agrupamento, referências inexistentes, inatividade, quantidades inválidas, cancelamento/devolução, envio, entrega, pagamento, avisos e expiração.

Os IDs nos testes unitários são atribuídos com `ReflectionTestUtils`: não há JPA para gerá-los. Nos testes de integração, o banco gera os IDs normalmente.

A autenticação unitária usa `UsernamePasswordAuthenticationToken` com um `User` real. `@AfterEach` limpa `SecurityContextHolder`, para um usuário não vazar para outro teste.

## Autorização HTTP, como na suíte de produtos

A fábrica cria um usuário persistido com papel CUSTOMER ou ADMIN. O helper herdado `authenticatedSpec(user)` gera um JWT real e adiciona o cabeçalho Bearer.

Os cenários verificam:

- Administrador lista todos os pedidos; cliente lista somente os seus.
- Proprietário e administrador consultam um pedido.
- Outro cliente não consulta nem cancela o pedido (`403`).
- Cliente não cria pedido em nome de outro (`403`).
- Apenas administrador envia e confirma entrega (`403` para cliente).
- Requisição sem token é bloqueada em todos os endpoints de pedidos (`403`, conforme a configuração atual).
- UUID inválido recebe `400`; pedido inexistente recebe `404`.

Os testes de permissão usam dados válidos e estados que permitiriam a operação. Assim, um `403` não fica mascarado por erro de validação ou por estado inválido.

## Persistência e rollback

`OrderIntegrationTest` não usa uma transação envolvendo todo o método de teste. A requisição HTTP abre a transação do serviço; a consulta posterior ao repositório verifica o resultado já confirmado no banco.

Há dois cenários importantes:

1. Primeiro produto tem estoque e é atualizado; o segundo não tem estoque suficiente. Um spy de ProductService verifica que a primeira gravação realmente foi chamada. Depois do `400`, o banco deve manter ambos os estoques originais e nenhum pedido.
2. A publicação de criação lança `AmqpException` depois das alterações no banco. A requisição retorna `500`; pedido e estoque são desfeitos pela transação.

`@SpyBean` conserva o ProductService real e apenas registra as chamadas, ao contrário de um mock completo.

A falha do broker é simulada com:

```java
doThrow(new AmqpException("Broker indisponível"))
    .when(rabbitTemplate)
    .convertAndSend(eq(EXCHANGE_PEDIDOS), eq(ROTA_PEDIDO_CRIADO), any(OrderCreatedEventDTO.class));
```

Limite: rollback do PostgreSQL não desfaz mensagens já publicadas. O teste simula falha na primeira publicação; ele não demonstra atomicidade entre banco e broker. Uma publicação posterior pode falhar quando a anterior já saiu. Uma evolução para essa garantia seria uma outbox transacional, com testes específicos.

## RabbitMQ real: o que foi implementado

`OrderMessagingIntegrationTest` inicia `rabbitmq:4-alpine` em um container temporário. As credenciais são fictícias e definidas somente nesse container. `@DynamicPropertySource` informa ao Spring o host e a porta aleatória, sem `.env`.

O teste usa o `RabbitMQConfig` e o conversor JSON da aplicação. Antes dos casos, inicializa a topologia e limpa as filas utilizadas. Os listeners permanecem desligados no perfil `test`, para que pagamento/logística não consumam as mensagens antes da verificação.

Ao cadastrar via HTTP, verifica:

- Evento `OrderCreatedEventDTO` recebido em `pagamento.queue`, com ID, cliente e total corretos.
- UUID recebido na fila de espera de aviso.
- UUID recebido na fila de espera de cancelamento.

Ao despachar via HTTP, verifica `OrderDispatchedEventDTO` na fila de logística. Esse caso detectaria a antiga publicação usando nome da fila como routing key.

```java
Object payload = rabbitTemplate.receiveAndConvert(FILA_PAGAMENTO, 5000);
```

O timeout limita a espera e evita `Thread.sleep` fixo. O payload passa pelo broker e pela serialização/deserialização reais.

Esses testes consomem as mensagens das filas de espera diretamente: **não esperam os TTLs de 20 e 30 minutos e não testam o dead-lettering**. A expiração de negócio é testada chamando o serviço sem autenticação e conferindo estado/estoque no PostgreSQL. Uma segunda chamada deve ser inofensiva.

### Como ampliar para TTL, DLQ e listeners

Para testar dead-lettering sem esperar minutos, crie no teste uma fila com nome exclusivo, TTL curto (por exemplo, 100 ms) e os mesmos argumentos de DLX. Use a exchange real e uma routing key exclusiva para a entrada de teste. Receba na DLQ de destino com timeout e confira o UUID. Apague a fila temporária ao terminar.

Não redeclare uma fila existente com outro TTL: o RabbitMQ rejeita argumentos incompatíveis. Para testar os TTLs exatos de produção, inspecione as definições das filas separadamente.

Para comprovar um consumidor completo, habilite somente o listener relevante no contexto desse teste, publique sua mensagem e aguarde o estado no banco com prazo máximo. Não habilite todos os listeners indiscriminadamente: o consumidor de pagamento pode tentar falar com Stripe e o de logística com a transportadora.

Também vale testar a política de reentrega, mensagem inválida e duplicação. A suíte atual verifica a idempotência sequencial da expiração e da aprovação no domínio; isso não demonstra segurança em chamadas concorrentes.

## E-mail: o que foi implementado e o que ainda é diferente

No teste de pedidos, EmailService é mockado: verificamos se o pedido pediu o e-mail correto, não o conteúdo dele.

`EmailServiceTest` acrescenta um nível: o EmailService é real e o JavaMailSender é mockado. `ArgumentCaptor<SimpleMailMessage>` permite inspecionar remetente, destinatários, assunto e texto. O caso de entrega confere as mensagens destinadas ao administrador e ao cliente. Também simula `MailSendException` nos quatro fluxos usados por pedidos e verifica a política atual: registrar a falha sem propagá-la ao chamador.

Isso não demonstra entrega por SMTP. Para esse nível, crie uma classe separada com servidor SMTP de teste, como GreenMail ou um container de captura de e-mails:

1. Use um contexto sem o mock de EmailService herdado da classe-base atual.
2. Configure `JavaMailSender` para host e porta do servidor temporário; configure remetente/administrador fictícios no perfil ou nas propriedades dinâmicas de teste.
3. Execute o EmailService real.
4. Consulte as mensagens capturadas pelo servidor e verifique destinatário, assunto e corpo.
5. Teste timeout/falha de conexão e a política de erro esperada.

Um servidor local de captura não envia mensagens para caixas reais. Mesmo um teste SMTP bem-sucedido não comprova entrega final em uma caixa pública, filtragem antispam ou reputação do domínio. Nenhum SMTP externo real foi usado nesta implementação.

## Stripe e transportadora

Pedidos publica eventos para esses fluxos; não chama diretamente o SDK Stripe nem o cliente HTTP de entrega. Os consumidores ficam desabilitados na integração de pedidos. A suíte não faz cobranças e não solicita entregas reais.

Em suítes próprias, use respostas HTTP controladas (por exemplo, um servidor stub local) para simular sucesso, falha, timeout e payload inválido. Webhooks devem ter testes de assinatura válida/inválida e repetição de eventos. Isso deve ser separado do teste do domínio de pedidos.

## Como executar

Requisitos: Java 21, Docker acessível e Maven Wrapper do projeto. A primeira execução pode baixar dependências e imagens de PostgreSQL/RabbitMQ.

```bash
# Entidade, serviço e composição de e-mails: sem Docker.
./mvnw -Dtest=OrderTest,OrderServiceTest,EmailServiceTest test

# HTTP, autorização, PostgreSQL e falhas simuladas do broker.
./mvnw -Dtest=OrderIntegrationTest test

# PostgreSQL e RabbitMQ reais em containers.
./mvnw -Dtest=OrderMessagingIntegrationTest test

# Todos os testes, incluindo os de produtos.
./mvnw test
```

Na IDE, execute a classe ou o método pelo JUnit. Os relatórios Maven ficam em `target/surefire-reports`. Cada entrada de um teste parametrizado conta como uma execução independente.

Se aparecer `Could not find a valid Docker environment` ou acesso negado a `docker.sock`, corrija o acesso ao Docker antes de interpretar o resultado como falha da aplicação. Em ambientes restritos, Mockito também pode falhar ao anexar o agente Byte Buddy à JVM; nesta execução foi necessário usar o executor com as permissões adequadas, sem alterar o projeto para contornar a restrição.

## Resultado da execução realizada

Execução final: `./mvnw test`, em 10/11/2026, com **BUILD SUCCESS**.

| Suíte | Execuções |
|---|---:|
| OrderTest | 42 |
| OrderServiceTest | 53 |
| OrderIntegrationTest | 73 |
| OrderMessagingIntegrationTest | 2 |
| EmailServiceTest | 8 |
| Testes de produtos e inicialização já existentes | 118 |
| **Total** | **296** |

Resultado: **zero falhas, zero erros e zero testes ignorados**. O RabbitMQ real e o PostgreSQL foram iniciados em containers temporários; e-mail externo, Stripe e transportadora não foram acionados.
