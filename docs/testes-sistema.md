# Guia de estudo: testes da Order API

Este guia reúne os testes de todos os módulos e as correções desta ampliação. O [guia de pedidos](testes-orders.md) continua explicando a primeira etapa em detalhe; seus números de execução são históricos. Os resultados atuais estão ao final deste documento.

## 1. Como a suíte está organizada

```text
src/test/java/com/miguelpazatto/orderapi/
├── unit/
│   ├── auth/
│   │   ├── entities/UserTest.java
│   │   └── services/{AuthenticationServiceTest,AuthorizationServiceTest}.java
│   ├── customers/
│   │   ├── entities/CustomerTest.java
│   │   └── services/CustomerServiceTest.java
│   ├── products/{entities,services}/...
│   ├── orders/{entities,services,listeners}/...
│   ├── payments/{entities,services,listeners}/...
│   ├── delivery/{entities,services,listeners}/...
│   └── core/{security,services,listeners}/...
└── integration/
    ├── infra/{AbstractIntegrationTest,TestUserFactory,StripeWebhookFixtures}.java
    ├── auth/AuthenticationIntegrationTest.java
    ├── customers/CustomerIntegrationTest.java
    ├── products/ProductIntegrationTest.java
    ├── orders/{OrderIntegrationTest,OrderMessagingIntegrationTest}.java
    ├── payments/{PaymentIntegrationTest,StripeWebhookIntegrationTest}.java
    ├── delivery/{DeliveryIntegrationTest,DeliveryClientHttpIntegrationTest}.java
    ├── core/{EmailSmtpIntegrationTest,ErrorContractIntegrationTest}.java
    └── ApplicationIntegrationTest.java
```

Cada classe representa uma responsabilidade. Entidades são exercitadas diretamente; serviços usam Mockito; endpoints usam RestAssured e a aplicação Spring; protocolos externos têm testes específicos com servidores isolados.

## 2. O que cada tipo de teste demonstra

| Tipo | Exemplo | O que comprova | O que não comprova |
|---|---|---|---|
| Entidade | `OrderTest`, `DeliveryTest` | Regras e mudanças de estado | JPA, transações e HTTP |
| Serviço com mocks | `PaymentServiceTest` | Orquestração, argumentos, falhas e efeitos solicitados | Banco ou rede reais |
| HTTP com PostgreSQL | `CustomerIntegrationTest` | Validação, JWT, permissões, controller, serviço e persistência | Provedor externo real |
| Broker real | `OrderMessagingIntegrationTest` | Conexão, rotas, bindings e conversão de mensagens | Todo o ciclo dos consumidores |
| SMTP local | `EmailSmtpIntegrationTest` | EmailService + JavaMailSender + recepção SMTP | Entrega em caixas públicas |
| HTTP local | `DeliveryClientHttpIntegrationTest` | Feign, método/caminho, JSON e resposta de erro | Disponibilidade da transportadora real |
| Contrato MVC | `ErrorContractIntegrationTest` | Serialização de erros e status HTTP | Socket HTTP, filtros e autenticação real |

A mesma regra pode aparecer em dois níveis por razões diferentes. O teste de domínio demonstra a regra isoladamente; a integração demonstra que o fluxo público a aciona e persiste o resultado corretamente.

## 3. Padrão de escrita

Nos testes, a sequência é preparar, executar e verificar. Por exemplo:

```java
when(users.findByEmail(email)).thenReturn(user);

UserDetails result = service.loadUserByUsername(email);

assertThat(result).isSameAs(user);
```

`when(...).thenReturn(...)` define o comportamento de uma dependência simulada. `assertThat(...)` verifica um resultado. `verify(...)` verifica uma interação relevante, como solicitar um e-mail ou publicar um evento.

Nos caminhos tristes, confira a exceção/status e a ausência dos efeitos que não deveriam acontecer. Não é necessário comparar todos os campos da entidade em cada erro. Os testes de rollback conferem os dados que poderiam ter sido alterados.

### Testes parametrizados

- `@ValueSource`: um valor simples por execução, como quantidade ou flag de administrador.
- `@EnumSource`: todos ou alguns estados de um enum.
- `@CsvSource`: combinações simples, como tipo de erro e status esperado.
- `@MethodSource`: DTOs, valores nulos ou combinações construídas em Java.
- `Arguments.of(...)`: uma linha de parâmetros; a ordem corresponde à assinatura do método de teste.
- `Stream<Arguments>`: sequência dessas linhas, sem execução paralela implícita.
- `name = "{index} - {0}"`: identifica cada caso no relatório.

O `@BeforeEach` roda antes de cada execução parametrizada. Mocks são reinicializados pelo suporte Mockito/Spring; contexto de autenticação manual é limpo no fim do teste.

## 4. Autenticação e segurança

### Unidade

`UserTest` verifica ativação, desativação, papéis e troca de senha. `AuthenticationServiceTest` verifica hash da senha, papel CUSTOMER, evento de registro, e-mail duplicado, login e sincronização de status. `AuthorizationServiceTest` verifica usuário existente e inexistente.

`TokenServiceTest` usa JWT real para verificar sujeito, emissor, expiração em duas horas e rejeição de token inválido/expirado. O caminho de falha de criação é simulado com mock estático, fechado com try-with-resources.

`SecurityFilterTest` usa requisições servlet em memória. Confere ausência de cabeçalho, prefixo incorreto, token inválido, usuário removido, desativado e ativo. `SecurityUtilsTest` cobre contexto ausente, autenticação incompleta e principal incompatível.

### Integração

`AuthenticationIntegrationTest` faz cadastro e login por HTTP com PasswordEncoder, JWT e PostgreSQL reais. O cadastro deve gerar tanto User quanto Customer, ligados pelo mesmo userId. A senha persistida deve corresponder ao hash e nunca ao texto enviado.

Os caminhos tristes incluem campos nulos/em branco, e-mail inválido, senha curta, e-mail duplicado, senha incorreta, usuário inexistente/inativo e token expirado, inválido, com prefixo incorreto ou referente a usuário removido/desativado.

Uma credencial válida com permissão insuficiente deve retornar `403`. Credenciais inválidas no endpoint de login retornam `401`. Uma chamada protegida sem autenticação continua retornando `403`, conforme a configuração atual da aplicação.

## 5. Clientes

A entidade permite atualizar nome e telefone e preservar campos quando a chamada direta recebe valores em branco. O endpoint PATCH, por sua vez, exige ambos preenchidos e telefone no padrão do DTO. Essa distinção entre domínio e contrato HTTP é intencional nos testes.

A suíte cobre listagem apenas de ativos, lista vazia, consulta/atualização pelo proprietário ou administrador, alteração de status, validação dos campos, UUID inválido, ID inexistente e permissões.

A integração de desativação/ativação verifica também o User correspondente no banco. Isso demonstra a comunicação síncrona por `ApplicationEventPublisher`: CustomerService publica `CustomerStatusChangedEvent`, AuthenticationService atualiza User e a transação persiste o conjunto.

Esse mecanismo de eventos Spring é diferente de RabbitMQ: não atravessa um broker e não é um teste de entrega de mensagens pela rede.

## 6. Produtos e pedidos

As suítes anteriores continuam fazendo parte da execução completa. Produtos cobrem consulta, cadastro, SKU duplicado, PATCHs, validação e autorização. Pedidos cobrem cálculos, estados, proprietário, estoque, agrupamento de itens, rollback, notificações e expiração.

Consulte [testes-orders.md](testes-orders.md) para a explicação detalhada de agrupamento, estoque, serviço sem autenticação em listeners e `ArgumentCaptor`.

## 7. Pagamentos e Stripe

### Criação de cobrança sem cobrança real

`PaymentServiceTest` simula `PaymentIntent.create(...)` com `MockedStatic`. O teste verifica conversão de `10.25` para `1025` centavos, moeda BRL e metadado payment_id. Também simula indisponibilidade do SDK.

O mock estático fica dentro de try-with-resources, para não vazar para outros testes. O valor global `Stripe.apiKey` é restaurado ao final. Nenhum cartão ou conta Stripe real é utilizado.

O comportamento atual em falha de criação da cobrança é registrar o erro e manter Payment pendente. O teste documenta esse comportamento; não foi adicionado retry automático.

### Resultado de pagamento

Os testes unitários cobrem aprovação, recusa, pagamento inexistente e pedido inexistente. `PaymentIntegrationTest` usa banco real e executa o serviço sem contexto de login, como acontece num consumidor de fila.

Se a consulta ao pedido ou a publicação falhar, a mudança de status do pagamento deve ser revertida. Essa é uma verificação de transação PostgreSQL, feita consultando o repositório após a exceção.

### Assinatura de webhook real

`StripeWebhookFixtures` gera um payload compatível com a versão de API do SDK instalado e assina localmente `timestamp + "." + payload` com HMAC-SHA256 e um segredo fictício de teste. O cabeçalho tem formato `t=...,v1=...`.

`StripeWebhookIntegrationTest` deixa `Webhook.constructEvent(...)` real. Cobre aprovação/recusa, assinatura incorreta ou antiga, corpo adulterado, JSON inválido, cabeçalho ausente, API incompatível e metadado de pagamento ausente/inválido. Eventos de tipos não suportados são ignorados.

Um teste assinado real é mais forte que mockar a validação: um erro no formato do cabeçalho ou no conteúdo assinado causa falha. Ainda assim, ele não chama a API Stripe e não testa a infraestrutura pública de entrega/repetição dos webhooks.

## 8. Entregas e transportadora

`DeliveryTest` cobre rastreio obrigatório, atribuição única, status repetido, status nulo e proibição de regressão depois de DELIVERED.

`DeliveryServiceTest` cobre persistência antes da chamada à transportadora, falha externa, atualizações e publicação da conclusão somente na primeira transição para DELIVERED. Uma notificação repetida sequencial não deve gerar outro evento de conclusão.

`DeliveryIntegrationTest` chama o webhook via HTTP, verifica validação e banco e garante que um erro não altera o status anterior. O rastreio é obrigatório no primeiro envio; nas atualizações seguintes pode ser omitido.

`DeliveryClientHttpIntegrationTest` inicia um servidor HTTP do JDK em `127.0.0.1`, com porta aleatória. O Feign real envia POST para `/envios`; o teste inspeciona o orderId recebido e deserializa trackingCode. Uma resposta `503` deve ser absorvida pelo serviço sem apagar a entrega já salva.

O webhook de entrega continua público, como na configuração original. A suíte não adiciona nem comprova uma assinatura da transportadora: isso depende de definir um contrato com esse provedor.

## 9. Listeners RabbitMQ

Há testes de listeners de pagamento, webhook Stripe, atualização de status do pedido, expiração/aviso, despacho, conclusão de entrega e notificação de e-mail.

Eles são chamados diretamente com eventos de teste e dependências simuladas. Cumprem dois objetivos: verificar tradução dos dados recebidos e documentar a política de erro. Por exemplo, PaymentProcessedListener ignora status desconhecido, enquanto DeliveryCompletedListener propaga falhas do serviço para permitir o tratamento do container de mensagens.

Esses testes não comprovam redelivery, ack/nack, DLQ, ordering nem concorrência. Os testes de RabbitMQ real já existentes comprovam publicação, bindings e conversão. Um teste do fluxo completo deve habilitar só os consumidores necessários e aguardar o efeito final com prazo máximo.

Uma mensagem já publicada não é desfeita por rollback do banco. A suíte não promete atomicidade banco/broker. Outbox e testes de concorrência/reentrega continuam sendo evoluções separadas.

## 10. E-mail em três níveis

1. Em testes de pedidos, EmailService é mock: verifica se o fluxo solicitou a notificação correta.
2. Em `EmailServiceTest`, o serviço é real e JavaMailSender é mock: inspeciona mensagens e simula falha de envio.
3. Em `EmailSmtpIntegrationTest`, ambos são reais e o servidor SMTP local é GreenMail: verifica mensagens efetivamente recebidas por SMTP.

O SMTP local usa porta dinâmica e é encerrado no `@AfterEach`. Os seis cenários cobrem pedido enviado, entrega, aviso, expiração, pagamento aprovado e recusado. Aprovação e entrega produzem duas mensagens, para cliente e administrador.

Não se usa `.env`, credenciais de e-mail reais, Mailtrap remoto ou caixa postal pública. A entrega SMTP local não comprova antispam ou entrega na caixa final de um destinatário externo.

## 11. Contratos de erro

`ErrorContractIntegrationTest` usa MockMvc standalone com um controller exclusivo do código de teste e o ResourceExceptionHandler real. Verifica JSON `application/problem+json`, status, timestamp e ausência de detalhes internos em falhas inesperadas.

São cobertos conflitos de integridade e de atualização otimista (`409`), falta de autenticação (`401`), acesso negado (`403`), regra de negócio (`400`), recurso inexistente (`404`) e falha inesperada (`500`). Isso não simula uma corrida de duas transações: comprova o contrato HTTP quando a exceção de concorrência ocorre.

## 12. Correções de produção desta etapa

| Local | Antes | Depois |
|---|---|---|
| AuthorizationService | Retornava null para usuário desconhecido | Lança UsernameNotFoundException, como esperado pelo Spring Security |
| SecurityFilter | Aceitava qualquer prefixo, não conferia usuário ativo e podia acessar usuário nulo | Exige Bearer e só autentica usuário existente e habilitado |
| TokenService | Calculava expiração convertendo hora local com offset fixo | Usa Instant + 7200 segundos, sem depender do fuso do servidor |
| ResourceExceptionHandler | Credenciais inválidas/cabeçalho ausente podiam cair em erro genérico | Trata autenticação como 401 e cabeçalho obrigatório ausente como 400 |
| CustomerController | Não verificava propriedade de perfis | Cliente acessa/altera apenas seu perfil; administrador mantém acesso global |
| SecurityConfig | Qualquer usuário autenticado podia ativar perfil | Ativação exige ADMIN |
| CustomerService | Ativação/desativação inexistente lançava exceção genérica | Usa ResourceNotFoundException, retornando 404 |
| PaymentService | Consultava pedido via método que exige usuário HTTP | Usa OrderRepository no processamento interno, sem login artificial |
| PaymentService | Atualização de resultado sem transação abrangente | Aprovação/recusa são transacionais, com rollback demonstrado |
| StripeWebhookService | Metadado ausente/inválido podia seguir para o consumidor | Rejeita IDs inválidos nos eventos de resultado antes de publicar |
| DeliveryUpdatePayloadDTO | @Valid não tinha restrições a executar | Exige orderId e status reconhecido |
| Delivery | Aceitava rastreio vazio e status nulo | Rejeita esses dados com regra de negócio |
| DeliveryService | Webhook DELIVERED repetido publicava novamente | Publica somente na primeira transição sequencial |

No build foram adicionados JaCoCo e GreenMail. GreenMail é dependência somente de teste. O plugin JaCoCo instrumenta a execução de testes e gera relatório no verify; não adiciona o agente à aplicação em produção. Não houve mudanças de schema/migrations nesta ampliação.

## 13. Como executar e estudar os resultados

Requisitos: Java 21 e Docker acessível para os testes com PostgreSQL/RabbitMQ. O Maven Wrapper baixa as dependências. As primeiras execuções também podem baixar as imagens.

```bash
# Todos os testes unitários, sem Docker.
./mvnw -Dtest='com.miguelpazatto.orderapi.unit.**.*Test' test

# Autenticação e autorização com PostgreSQL real.
./mvnw -Dtest=AuthenticationIntegrationTest,CustomerIntegrationTest test

# Protocolo SMTP local, sem PostgreSQL/Docker.
./mvnw -Dtest=EmailSmtpIntegrationTest test

# Transportadora HTTP local e persistência PostgreSQL.
./mvnw -Dtest=DeliveryClientHttpIntegrationTest test

# Assinatura real do webhook Stripe, sem chamar Stripe remoto.
./mvnw -Dtest=StripeWebhookIntegrationTest test

# Todos os testes e relatório de cobertura do zero.
./mvnw clean verify
```

`test` executa os testes e coleta cobertura; `verify` também gera o relatório. Abra `target/site/jacoco/index.html`. Para detalhes de uma falha, consulte `target/surefire-reports`. XML e CSV do JaCoCo ficam junto do HTML.

Linhas cobertas significam código executado; branches cobertos significam alternativas de decisão exercitadas. Nenhuma dessas métricas substitui a qualidade das assertivas. Não foram configuradas exclusões de classes para inflar o relatório nem uma meta artificial de 100%.

Ordem sugerida de estudo: entidade → serviço com Mockito → JWT/permissões → HTTP com banco → rollback → webhook assinado → HTTP/SMTP locais → broker real → relatório de cobertura.

## 14. Limites que continuam importantes

- A cobertura abrange todos os módulos; não significa provar todos os estados possíveis do sistema.
- Não há teste de carga nem de corridas de estoque/cancelamento/webhooks.
- Uma duplicação sequencial pode ser inofensiva e ainda haver risco em chamadas simultâneas.
- O ciclo completo Stripe → RabbitMQ → pedido → SMTP não é exercitado como um único teste ponta a ponta.
- TTL real de 20/30 minutos, DLQ e política de redelivery não são esperados pela suíte rápida.
- Timeout da transportadora não é simulado: o servidor local cobre sucesso e resposta 503.
- Não são feitas cobranças, entregas ou envios de e-mail públicos reais.
- Cancelar pedido pago continua sem implementar reembolso; resultado aprovado/recusado de Payment ainda precisa de uma política de transições/duplicação definida para casos mais complexos.

Fontes das ferramentas usadas: [JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/changes.html) e [GreenMail](https://greenmail-mail-test.github.io/greenmail/). Os exemplos e comandos acima correspondem ao código e às execuções deste projeto.

## 15. Resultado medido nesta implementação

Execução final em 10/11/2026: `./mvnw clean verify` — **BUILD SUCCESS**.

**511 testes, zero falhas, zero erros e zero testes ignorados.**

| Módulo | Testes | Linhas cobertas | Branches cobertos |
|---|---:|---:|---:|
| application | 1 | 33.33% | — |
| auth | 47 | 98.46% | 100.00% |
| core | 40 | 99.60% | 100.00% |
| customers | 43 | 100.00% | 100.00% |
| delivery | 46 | 100.00% | 100.00% |
| orders | 178 | 100.00% | 100.00% |
| payments | 39 | 100.00% | 100.00% |
| products | 117 | 100.00% | 100.00% |

Total: **916/920 linhas (99,57%)**, **193/193 branches (100%)** e **66/66 classes instrumentadas exercitadas**.

As quatro linhas não executadas pertencem ao `main` de `OrderapiApplication` (duas), ao construtor implícito de `SecurityUtils` e ao getter `UserRole.getRole()`. Não foram criados testes artificiais apenas para executar essas linhas. O teste de inicialização cobre o contexto da aplicação, sem chamar diretamente o método main.

O relatório completo está em `target/site/jacoco/index.html`; as métricas acima vêm do XML gerado na mesma execução. Cobertura de branches não significa que todos os cenários de concorrência, falhas de infraestrutura ou comportamento externo estejam provados. Consulte os limites da seção 14.
