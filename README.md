# Order Processing System

Implementação de referência para o desafio de processamento de pedidos utilizando Java e Spring Boot, com validações por tipo de produto, publicação/consumo de eventos e persistência em PostgreSQL.

## Stack
- Java 21
- Spring Boot 3
- PostgreSQL
- Kafka/Redpanda
- Docker + Docker Compose
- Makefile para automação

## Como executar

### Pré-requisitos
- Docker e Docker Compose
- Make (opcional, mas recomendado)

### Subir tudo (app + dependências)
```bash
make up
# ou
docker-compose up --build
```
A aplicação sobe em `http://localhost:8080` e o Swagger em `/swagger-ui.html`.
Endpoints de health/métricas via Actuator: `/actuator/health`, `/actuator/metrics`.

### Derrubar serviços
```bash
make down
# ou
docker-compose down -v
```

### Build e testes locais
```bash
make build   # mvn -DskipTests package
make test    # mvn test
```
> Se não tiver Maven instalado, use o fluxo Docker (`make up`) que realiza o build dentro do contêiner. Testes de integração usam Testcontainers e exigem Docker em execução; sem Docker eles serão automaticamente ignorados.

## Endpoints principais
- `POST /api/orders` — cria pedidos validando catálogo, aplica snapshot de preço e publica evento.
- `GET /api/orders/{orderId}` — consulta pedido por ID.
- `GET /api/orders?customerId=` — lista pedidos por cliente (ordem decrescente de criação).

## Notas
- Credenciais e URLs são definidas via variáveis de ambiente (ver `docker-compose.yml` / `application.yml`).
- Commits seguem Conventional Commits; branch principal: `develop`.
