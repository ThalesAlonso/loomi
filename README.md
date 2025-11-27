# Order Processing System

Implementação de referência para o desafio de processamento de pedidos utilizando Java (Spring Boot), arquitetura em camadas (MVC) e documentação via Swagger.

## Tecnologias
- Java 25
- Spring Boot 3
- PostgreSQL
- Apache Kafka/Redpanda
- Swagger (Springdoc OpenAPI)
- Docker e Docker Compose
- Makefile para automação

## Executando

### Pré-requisitos
- Docker e Docker Compose
- Make

### Subir infraestrutura e aplicação
```bash
make up
```

### Encerrar serviços
```bash
make down
```

### Build e testes locais
```bash
make build
make test
```

A aplicação sobe em `http://localhost:8080` e a documentação Swagger fica disponível em `/swagger-ui.html`.

## Endpoints principais
- `POST /api/orders` – cria pedidos com validações de catálogo e publica evento no Kafka.
- `GET /api/orders/{orderId}` – consulta pedido por ID.
- `GET /api/orders?customerId=` – lista pedidos por cliente.

## Testes
- **Unitários**: cobertura da lógica principal de criação de pedidos (`OrderServiceTest`).

## Uso de IA
Este projeto foi auxiliado por IA para geração de boilerplate e organização inicial. Todo o código foi revisado manualmente para garantir adequação às regras de negócio descritas no desafio.
