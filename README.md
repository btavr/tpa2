# TPA2 - Sistema Distribuído de Pesquisa em Emails

## 1. Objetivo

Este trabalho implementa um sistema distribuído para pesquisa de mensagens de email em um repositório distribuído, utilizando os paradigmas de interação e middleware estudados nas aulas práticas. O sistema permite que múltiplos utilizadores submetam pedidos de pesquisa de substrings específicas em ficheiros de email, obtenham o conteúdo de ficheiros específicos e consultem estatísticas sobre o comportamento do sistema.

## 2. Arquitetura do Sistema

O sistema segue uma arquitetura baseada em **publish-subscribe** e **work-queue**, distribuída por múltiplas máquinas virtuais (VMs) na Google Cloud Platform (GCP). Os principais componentes são:

- **UserApp**: Aplicação cliente que permite aos utilizadores submeter pedidos
- **RabbitMQ Broker**: Broker publish-subscribe que gerencia a distribuição de mensagens
- **Workers**: Instâncias que processam os pedidos distribuídos através de uma work-queue
- **GlusterFS**: Sistema de ficheiros distribuído que replica os ficheiros de email entre VMs

### 2.1 Fluxo de Comunicação

1. **Pedido de Pesquisa/Obtenção de Ficheiro/Estatísticas**: 
   - UserApp → RabbitMQ Exchange (DIRECT) → Work Queue → Worker

2. **Resposta**:
   - Worker → RabbitMQ Exchange → Response Queue → UserApp

3. **Armazenamento**:
   - Workers acedem a ficheiros replicados via GlusterFS montado em `/var/sharedfiles`

## 3. Componentes Desenvolvidos

### 3.1 TPA2-RabbitMQ-Configurator

**Objetivo**: Configurar a infraestrutura RabbitMQ necessária para o funcionamento do sistema.

**Classe Principal**: `configurator.TPA2Configurator`

**Funcionalidades**:
- Cria um exchange do tipo FANOUT chamado `request-exchange` (durable)
- Cria uma queue chamada `work-queue` (durable)
- Estabelece binding entre o exchange FANOUT e a queue (routing key vazia para FANOUT)

**Uso**:
```bash
java -jar configurator.jar <ipRabbitMQ> <portRabbitMQ> [exchange] [queue]
```

**Exemplo**:
```bash
java -jar configurator.jar 10.128.0.8 5672 request-exchange work-queue
```

**Explicação do Código**:
- Utiliza a biblioteca `com.rabbitmq.client` para interagir com RabbitMQ
- Cria conexão e canal com o broker
- Declara exchange do tipo FANOUT como `durable` (persiste após reinício do broker)
- Declara queue como `durable` (persiste após reinício do broker)
- O binding com routing key vazia (`""`) é apropriado para exchanges FANOUT, que distribuem mensagens para todas as queues ligadas

### 3.2 UserApp

**Objetivo**: Aplicação cliente que permite aos utilizadores interagir com o sistema através de um menu interativo.

**Classe Principal**: `userapp.UserApp`

**Funcionalidades**:
1. **Pesquisa de Substrings**: Permite pesquisar ficheiros que contêm um conjunto de substrings
2. **Obtenção de Conteúdo de Ficheiro**: Permite obter o conteúdo completo de um ficheiro específico
3. **Consulta de Estatísticas**: Permite consultar estatísticas globais do sistema

**Estrutura de Classes**:

#### 3.2.1 `userapp.UserApp`
- **Conexão RabbitMQ**: Estabelece conexão com o broker usando parâmetros configuráveis
- **Queue de Respostas**: Cria uma queue exclusiva e auto-deletável para receber respostas
- **Consumer de Respostas**: Configura um consumer assíncrono que recebe respostas e as armazena num `ConcurrentHashMap` usando o `requestId` como chave
- **Menu Interativo**: Interface de linha de comando que permite ao utilizador escolher operações
- **Sincronização**: Utiliza `synchronized` e `wait/notify` para esperar respostas de forma síncrona (com timeout de 30 segundos)

**Fluxo de Processamento**:
1. Utilizador escolhe uma opção no menu
2. Aplicação cria um objeto `Request` com um `requestId` único (UUID)
3. Serializa o request para JSON usando Gson e converte para `byte[]`
4. Publica no exchange FANOUT `request-exchange` com routing key vazia (`""`)
5. Cria uma queue exclusiva para receber respostas (não precisa de bind - usa default exchange)
6. Worker processa o pedido e publica resposta no **default exchange (DIRECT)** com routing key = nome da queue de respostas
7. Espera pela resposta correspondente (correlacionada pelo `requestId`)
8. Exibe a resposta formatada ao utilizador

**Uso**:
```bash
java -jar userapp.jar <ipRabbitMQ> <portRabbitMQ> <requestExchange> <workQueue>
```

**Exemplo**:
```bash
java -jar userapp.jar 10.128.0.8 5672 request-exchange work-queue
```

#### 3.2.2 `userapp.messages.Request`
Classe que representa um pedido enviado pelo UserApp. Contém:
- `RequestType`: Enum com tipos `SEARCH`, `GET_FILE`, `GET_STATISTICS`
- `requestId`: Identificador único para correlação request/response
- `replyTo`: Nome da queue onde enviar a resposta
- `replyExchange`: Exchange onde publicar a resposta
- Campos específicos por tipo:
  - `substrings`: Lista de substrings (para SEARCH)
  - `filename`: Nome do ficheiro (para GET_FILE)

#### 3.2.3 `userapp.messages.Response`
Classe que representa uma resposta recebida do Worker. Contém:
- `ResponseType`: Enum com tipos `SEARCH_RESULT`, `FILE_CONTENT`, `STATISTICS`
- `requestId`: ID do pedido original
- Campos específicos por tipo:
  - `filenames`: Lista de nomes de ficheiros (para SEARCH_RESULT)
  - `filename` e `content`: Nome e conteúdo do ficheiro (para FILE_CONTENT)
  - `totalRequests`, `successfulRequests`, `failedRequests`: Estatísticas (para STATISTICS)
- `success`: Indica se a operação foi bem-sucedida
- `errorMessage`: Mensagem de erro (se `success = false`)

#### 3.2.4 `userapp.util.MessageSerializer`
Utilitário para serialização/deserialização de mensagens:
- Utiliza a biblioteca **Gson** para conversão objeto ↔ JSON
- `toBytes(Object)`: Converte objeto para `byte[]` (JSON → UTF-8 bytes)
- `requestFromBytes(byte[])`: Deserializa `Request` a partir de `byte[]`
- `responseFromBytes(byte[])`: Deserializa `Response` a partir de `byte[]`

### 3.3 Worker

**Objetivo**: Processa pedidos distribuídos através da work-queue, realizando pesquisas em ficheiros e retornando resultados.

**Classe Principal**: `worker.Worker`

**Funcionalidades**:
1. **Processamento de Pedidos**: Consome mensagens da work-queue e processa cada pedido
2. **Pesquisa de Substrings**: Utiliza `FileSearcher` para encontrar ficheiros que contêm todas as substrings especificadas
3. **Leitura de Ficheiros**: Lê o conteúdo de ficheiros específicos do GlusterFS
4. **Estatísticas Locais**: Mantém contadores de pedidos processados (total, bem-sucedidos, falhados)
5. **Envio de Respostas**: Envia respostas de volta para o UserApp através do RabbitMQ

**Estrutura de Classes**:

#### 3.3.1 `worker.Worker`
- **Conexão RabbitMQ**: Conecta-se ao broker usando parâmetros configuráveis
- **QoS (Quality of Service)**: Configura `basicQos(1)` para garantir distribuição justa de mensagens (um worker não recebe múltiplas mensagens enquanto processa uma)
- **Consumer de Pedidos**: Configura um consumer que:
  - Deserializa mensagens recebidas
  - Processa o pedido de acordo com o tipo
  - Envia resposta
  - Faz acknowledgment da mensagem (ou nack em caso de erro)
- **Estatísticas**: Mantém contadores thread-safe usando `synchronized` blocks

**Fluxo de Processamento**:
1. Recebe mensagem da work-queue
2. Deserializa para objeto `Request`
3. Processa de acordo com o tipo:
   - **SEARCH**: Chama `FileSearcher.getMatchingFilenames()`
   - **GET_FILE**: Chama `FileSearcher.getFileContent()`
   - **GET_STATISTICS**: Retorna estatísticas locais (nota: versão simplificada, sem agregação via Spread)
4. Cria objeto `Response` com os resultados
5. Serializa resposta e publica no exchange especificado no request (`replyExchange`) com routing key = `replyTo`
6. Faz acknowledgment da mensagem original

**Uso**:
```bash
java -jar worker.jar <ipRabbitMQ> <portRabbitMQ> <workQueue> <directoryPath>
```

**Exemplo**:
```bash
java -jar worker.jar 10.128.0.8 5672 work-queue /var/sharedfiles
```

#### 3.3.2 `worker.messages.Request` e `worker.messages.Response`
Estruturas idênticas às do UserApp para garantir compatibilidade na serialização/deserialização.

#### 3.3.3 `worker.util.FileSearcher`
Utilitário para pesquisa em ficheiros, baseado no Anexo 2 do enunciado:

- **`searchInsideEmails(String directoryPath, List<String> substringsList)`**:
  - Percorre todos os ficheiros `.txt` na diretoria
  - Lê o conteúdo de cada ficheiro
  - Verifica se contém todas as substrings (case-insensitive)
  - Retorna um `Map<String, String>` com nome do ficheiro → conteúdo

- **`containsAllSubstrings(String message, List<String> substringsList)`**:
  - Verifica se a mensagem contém todas as substrings especificadas
  - Comparação case-insensitive (converte tudo para lowercase)

- **`getFileContent(String directoryPath, String filename)`**:
  - Lê o conteúdo de um ficheiro específico
  - Lança exceção se o ficheiro não existir

- **`getMatchingFilenames(String directoryPath, List<String> substringsList)`**:
  - Wrapper que retorna apenas a lista de nomes de ficheiros (chaves do Map)

#### 3.3.4 `worker.util.MessageSerializer`
Idêntico ao do UserApp, mas para as classes do pacote `worker.messages`.

## 4. Tecnologias e Dependências

### 4.1 Linguagem e Runtime
- **Java 21** (OpenJDK)
- **Maven** para gestão de dependências e build

### 4.2 Bibliotecas Principais

#### RabbitMQ Client
- **`com.rabbitmq:amqp-client:5.26.0`**
- Cliente Java para RabbitMQ
- Utilizado para publish-subscribe e work-queue patterns

#### Gson
- **`com.google.code.gson:gson:2.13.2`**
- Biblioteca para serialização/deserialização JSON
- Utilizada para converter objetos Java ↔ JSON ↔ `byte[]`

#### SLF4J Simple
- **`org.slf4j:slf4j-simple:2.0.16`**
- Framework de logging simples
- Utilizado pelo RabbitMQ client para logging

### 4.3 Middleware e Infraestrutura

#### RabbitMQ
- Broker publish-subscribe executando em Docker container
- Exchange tipo **FANOUT** 
- Work-queue para distribuição de carga entre workers
- FANOUT distribui mensagens para todas as queues ligadas ao exchange

#### GlusterFS
- Sistema de ficheiros distribuído
- Replicação de ficheiros entre múltiplas VMs
- Montado em `/var/sharedfiles` em cada VM

#### Spread Toolkit
- **Nota**: Embora mencionado no enunciado para comunicação multicast entre workers e eleição de coordenador para estatísticas, a implementação atual apresenta uma versão simplificada das estatísticas (apenas locais ao worker). A integração completa com Spread seria necessária para agregação global de estatísticas.

## 5. Estrutura do Projeto

```
final-tpa2/
├── TPA2-RabbitMQ-Configurator/
│   ├── src/main/java/configurator/
│   │   └── TPA2Configurator.java
│   ├── pom.xml
│   └── assembly.xml
│
├── UserApp/
│   ├── src/main/java/userapp/
│   │   ├── UserApp.java
│   │   ├── messages/
│   │   │   ├── Request.java
│   │   │   └── Response.java
│   │   └── util/
│   │       └── MessageSerializer.java
│   ├── src/main/resources/
│   │   └── simplelogger.properties
│   ├── pom.xml
│   └── assembly.xml
│
├── Worker/
│   ├── src/main/java/worker/
│   │   ├── Worker.java
│   │   ├── messages/
│   │   │   ├── Request.java
│   │   │   └── Response.java
│   │   └── util/
│   │       ├── FileSearcher.java
│   │       └── MessageSerializer.java
│   ├── src/main/resources/
│   │   └── simplelogger.properties
│   ├── pom.xml
│   └── assembly.xml
│
└── EmailFiles/
    └── email001.txt ... email020.txt
```

## 6. Compilação e Execução

### 6.1 Compilação

Para compilar cada módulo:

```bash
# Configurator
cd TPA2-RabbitMQ-Configurator
mvn clean package

# UserApp
cd UserApp
mvn clean package

# Worker
cd Worker
mvn clean package
```

Os JARs com dependências serão gerados em `target/*-jar-with-dependencies.jar`.

### 6.2 Execução

**1. Configurar RabbitMQ** (executar uma vez):
```bash
java -jar TPA2-RabbitMQ-Configurator/target/TPA2-RabbitMQ-Configurator-1.0-jar-with-dependencies.jar \
  10.128.0.8 5672 request-exchange work-queue
```

**2. Iniciar Workers** (em múltiplas VMs):
```bash
java -jar Worker/target/Worker-1.0-jar-with-dependencies.jar \
  10.128.0.8 5672 work-queue /var/sharedfiles
```

**3. Iniciar UserApp**:
```bash
java -jar UserApp/target/UserApp-1.0-jar-with-dependencies.jar \
  10.128.0.8 5672 request-exchange work-queue
```

## 7. Padrões de Design e Decisões de Implementação

### 7.1 Publish-Subscribe Pattern
- UserApp publica pedidos no exchange FANOUT
- Exchange FANOUT distribui mensagens para todas as queues ligadas (incluindo work-queue)
- Workers subscrevem à work-queue
- **Respostas são publicadas no default exchange (DIRECT)** com routing key = nome da queue de respostas
- O default exchange roteia diretamente para a queue cujo nome corresponde à routing key

### 7.2 Work-Queue Pattern
- Múltiplos workers consomem da mesma queue
- RabbitMQ distribui mensagens de forma round-robin (com QoS=1 para distribuição justa)
- Cada mensagem é processada por apenas um worker

### 7.3 Serialização JSON
- Utilização de Gson para serialização/deserialização
- Objetos Java são convertidos para JSON e depois para `byte[]` (UTF-8)
- Permite flexibilidade na estrutura de mensagens

### 7.4 Correlação Request/Response
- Cada request possui um `requestId` único (UUID)
- UserApp mantém um mapa de respostas pendentes indexado por `requestId`
- Worker inclui o mesmo `requestId` na resposta
- UserApp espera pela resposta correspondente usando wait/notify

### 7.5 Thread Safety
- `ConcurrentHashMap` para armazenar respostas pendentes
- `synchronized` blocks para acesso a estatísticas no Worker
- Consumer de respostas executa em thread separada (RabbitMQ)

## 8. Limitações e Melhorias Futuras

### 8.1 Estatísticas Globais
A implementação atual de estatísticas retorna apenas valores locais ao worker. Para uma implementação completa conforme o enunciado, seria necessário:
- Integração com Spread Toolkit para comunicação multicast
- Algoritmo de eleição de coordenador (leader election)
- Agregação de estatísticas de todos os workers
- Consenso sobre qual worker responde ao pedido de estatísticas

### 8.2 Tratamento de Erros
- Implementação atual rejeita mensagens com erro (nack sem requeue)
- Poderia ser implementado um mecanismo de retry ou dead-letter queue

### 8.3 Persistência de Estatísticas
- Estatísticas são mantidas apenas em memória
- Perdidas quando o worker é reiniciado
- Poderia ser implementada persistência em ficheiro ou base de dados

### 8.4 Validação de Entrada
- Validação básica de parâmetros de linha de comando
- Poderia ser adicionada validação mais robusta de substrings e nomes de ficheiros

## 9. Conclusões

Este trabalho implementa com sucesso um sistema distribuído para pesquisa em emails utilizando RabbitMQ como middleware de mensagens. A arquitetura baseada em publish-subscribe e work-queue permite distribuição de carga e escalabilidade horizontal através de múltiplos workers.

A utilização de Gson para serialização JSON facilita a comunicação entre componentes, enquanto a estrutura modular do código (separação em UserApp, Worker e Configurator) facilita manutenção e extensão.

A implementação demonstra compreensão dos conceitos de sistemas distribuídos, comunicação assíncrona e padrões de middleware estudados nas aulas práticas.

---

**Desenvolvido para**: CD2526 - Sistemas Distribuídos  
**Data**: 2025  
**Versão**: 1.0

