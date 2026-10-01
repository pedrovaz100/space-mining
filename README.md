# Space Mining

## Sobre o projeto

Sistema de coordenação de uma frota de robôs mineradores em um asteroide. A API recebe comandos de movimentação/ação do robô (`RIGHT`, `LEFT`, `FRONT`, `BACK`, `OPEN`, `CLOSE`), valida-os de forma assíncrona e resiliente, e registra a execução e a contagem de cada comando processado, garantindo que:

- os robôs não sejam sobrecarregados (backpressure no consumo da fila);
- não ocorram conflitos entre ordens (processamento serializado no Mining Service);
- falhas de comunicação sejam tratadas automaticamente (retry com exponential backoff no Validator Service).

## Arquitetura

```
CLIENT
  |
  | POST /command
  v
Command Service  --(RestTemplate + Eureka)-->  Validator Service
  (porta 8080)                                    (porta 8081)
                                                       |
                                          valida comando (RIGHT/LEFT/FRONT/BACK/OPEN/CLOSE)
                                          simula falha (~50%) -> retry com exponential backoff
                                                       |
                                                       v
                                                  RabbitMQ (exchange mining-exchange / fila mining-queue)
                                                       |
                                                       v
                                                Mining Service (porta 8082)
                                                  consome a fila (backpressure: 1 mensagem por vez)
                                                  loga a execução do comando no robô
                                                  grava/atualiza contagem no banco (H2)

Eureka Server (porta 8761): service discovery para Command, Validator e Mining Service
```

## Fluxo da aplicação

1. Um cliente envia `POST /command` para o **Command Service** com o comando desejado.
2. O Command Service encaminha o comando ao **Validator Service** via `RestTemplate` com `@LoadBalanced` (descoberto via Eureka).
3. O Validator Service verifica se o comando é um dos válidos. Se não for, responde `400 Bad Request` imediatamente (sem retry).
4. Se o comando for válido, o Validator Service simula uma falha de comunicação com ~50% de chance. Em caso de falha, tenta novamente automaticamente com **exponential backoff** (delay inicial de 500ms, multiplicador 2, até 5 tentativas, teto de 10s, com jitter).
5. Quando a validação é bem-sucedida (ou depois de esgotar as tentativas), o Validator Service publica o comando no **RabbitMQ** (ou retorna `503` ao Command Service, que repassa o erro ao cliente).
6. O **Mining Service** consome a fila (um comando por vez, para não sobrecarregar o robô), loga a execução do comando e incrementa a contagem daquele comando no banco de dados.

## Tecnologias

- Java 17
- Spring Boot 4.1.1 / Spring Cloud 2025.1.3
- Spring Cloud Netflix Eureka (service discovery)
- Spring Cloud LoadBalancer (`@LoadBalanced RestTemplate`)
- Spring Resilience (`@Retryable` / `@EnableResilientMethods`) para retry com exponential backoff
- Spring AMQP (RabbitMQ)
- Spring Data JPA + H2 (banco em memória)
- Lombok
- Gradle (wrapper) por serviço

## Microsserviços

### Eureka Server
- Porta: `8761`
- Servidor de service discovery. Não se registra nem busca o próprio registro (`eureka.client.register-with-eureka=false`, `eureka.client.fetch-registry=false`).

### Command Service
- Porta: `8080`
- Expõe `POST /command`, recebendo `{ "command": "LEFT" }`.
- Encaminha o comando ao Validator Service via `RestTemplate` balanceado por Eureka (`http://VALIDATOR-SERVICE/validate`).
- Repassa ao cliente o status/erro devolvido pelo Validator (ex.: `400` para comando inválido) e retorna `503` se o Validator estiver indisponível.

### Validator Service
- Porta: `8081`
- Expõe `POST /validate`.
- Aceita apenas `RIGHT`, `LEFT`, `FRONT`, `BACK`, `OPEN`, `CLOSE`; qualquer outro valor retorna `400`.
- Simula falha de validação com ~50% de chance e tenta novamente com exponential backoff (`ValidationRetryService`).
- Após validar com sucesso, publica o comando no RabbitMQ.

### Mining Service
- Porta: `8082`
- Consome a fila `mining-queue` do RabbitMQ.
- Loga a execução do comando ("robô executando comando: X").
- Persiste/atualiza a contagem de execuções por comando na tabela `command_count` (H2 em memória).
- Consumo configurado com concorrência 1 e prefetch 1 (backpressure), evitando sobrecarregar o robô e conflitos de atualização simultânea no banco.

## RabbitMQ

- Exchange: `mining-exchange` (topic)
- Fila: `mining-queue` (durável)
- Routing key: `mining-key`
- Sobe via Docker Compose (`validator-service/compose.yaml`), usuário `myuser` / senha `secret`, portas `5672` (AMQP) e `15672` (management UI).

## Banco de dados

- H2 em memória (`jdbc:h2:mem:mining-service`), usado apenas pelo Mining Service.
- Console H2 disponível em `http://localhost:8082/h2-console` (usuário `sa`, senha em branco).
- Tabela `command_count(command VARCHAR PRIMARY KEY, total_executions INTEGER)`.

> Observação: por ser H2 em memória, os dados são perdidos ao reiniciar o Mining Service. Isso é aceitável para este exercício acadêmico.

## Retry e Exponential Backoff

Implementado no **Validator Service** (`ValidationRetryService`), usando o mecanismo de resiliência nativo do Spring Framework (`org.springframework.resilience.annotation.Retryable`, habilitado via `@EnableResilientMethods`):

```java
@Retryable(
        includes = ValidationFailedException.class,
        maxRetries = 5,
        delay = 500,
        multiplier = 2,
        maxDelay = 10_000,
        jitter = 100
)
public void validateWithRetry(String command) {
    if (Math.random() < 0.5) {
        throw new ValidationFailedException(command);
    }
}
```

- Apenas a falha simulada (`ValidationFailedException`) é retentada — comandos inválidos retornam `400` imediatamente, sem retry.
- Backoff exponencial: 500ms, 1s, 2s, 4s, 8s (limitado a 10s), com jitter.

## Pré-requisitos

- JDK 17
- Docker (para subir o RabbitMQ)
- Não é necessário instalar Gradle: cada serviço tem o Gradle Wrapper (`gradlew` / `gradlew.bat`)

## Como executar

Suba os serviços **nesta ordem**, cada um em um terminal:

```bash
# 1. RabbitMQ (via Docker)
cd validator-service
docker compose up -d

# 2. Eureka Server
cd ../eureka
./gradlew bootRun          # Windows: gradlew.bat bootRun

# 3. Validator Service (aguarde o Eureka subir)
cd ../validator-service
./gradlew bootRun

# 4. Mining Service
cd ../mining-service
./gradlew bootRun

# 5. Command Service
cd ../command-service
./gradlew bootRun
```

Verifique o registro dos serviços no Eureka em `http://localhost:8761`.

## Como testar

```
POST http://localhost:8080/command
Content-Type: application/json

{
  "command": "LEFT"
}
```

Uma collection e um environment do Postman prontos para uso estão em [`docs/postman/`](docs/postman/). Também há exemplos avulsos em [`request.http`](request.http).

Como a falha de validação é simulada com ~50% de chance, envie várias requisições seguidas para observar o retry com exponential backoff nos logs do Validator Service.

## Comandos válidos

```
RIGHT
LEFT
FRONT
BACK
OPEN
CLOSE
```

Qualquer outro valor retorna `400 Bad Request`.

## Evidências

Screenshots e logs reais dos testes executados estão em [`docs/evidencias/`](docs/evidencias/).
