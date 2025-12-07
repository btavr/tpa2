# TPA2 - Implementação RabbitMQ

Implementação da parte RabbitMQ do TPA2 (Trabalho Prático de Avaliação).

## 📋 Estrutura do Projeto

O projeto contém três aplicações Maven:

1. **UserApp**: Aplicação cliente que envia pedidos e recebe respostas
2. **Worker**: Aplicação servidor que processa pedidos (work-queue pattern)
3. **TPA2-RabbitMQ-Configurator**: Utilitário para configurar o RabbitMQ

## 🏗️ Arquitetura

### Componentes RabbitMQ

- **Exchange**: `request-exchange` (tipo DIRECT, durable)
- **Queue**: `work-queue` (durable)
- **Binding**: `request-exchange` → `work-queue` (routing key = `work-queue`)

### Fluxo de Mensagens

```
UserApp → RabbitMQ (exchange) → Worker(s) → Processa → RabbitMQ → UserApp
```

1. **UserApp** publica pedidos no exchange `request-exchange` com routing key `work-queue`
2. **Worker(s)** consomem pedidos da queue `work-queue` (work-queue pattern = distribuição de carga)
3. **Worker** processa o pedido e publica resposta no exchange `request-exchange`
4. **UserApp** recebe a resposta na sua queue exclusiva

## 🚀 Início Rápido

### 1. Compilar Projetos

```bash
# Compilar todos de uma vez
mvn clean package -pl UserApp,Worker,TPA2-RabbitMQ-Configurator -am

# Ou compilar individualmente
cd UserApp && mvn clean package
cd ../Worker && mvn clean package
cd ../TPA2-RabbitMQ-Configurator && mvn clean package
```

### 2. Iniciar RabbitMQ

```bash
docker run -d --hostname rabbithost --name rabbitmg \
  -p 5672:5672 -p 15672:15672 rabbitmq:management
```

Interface web: http://localhost:15672 (user: `guest`, password: `guest`)

### 3. Preparar Ficheiros de Teste

```bash
mkdir -p /tmp/test-emails
# Copiar ficheiros .txt para /tmp/test-emails
```

### 4. Iniciar Worker

```bash
java -jar Worker/target/Worker-jar-with-dependencies.jar \
  localhost 5672 work-queue /tmp/test-emails
```

### 5. Iniciar UserApp

```bash
java -jar UserApp/target/UserApp-jar-with-dependencies.jar
```

## 📖 Uso

### UserApp - Menu Interativo

```
=== UserApp Menu ===
1 - Search substrings
2 - Get file content
3 - Get statistics
0 - Exit
```

#### Opção 1: Search substrings
- Pede substrings separadas por vírgula
- Exemplo: `gRPC em Java 21, GCP, Docker`
- Retorna lista de ficheiros que contêm **todas** as substrings

#### Opção 2: Get file content
- Pede nome do ficheiro
- Exemplo: `email017.txt`
- Retorna conteúdo completo do ficheiro

#### Opção 3: Get statistics
- Retorna estatísticas do worker (total de pedidos, sucessos, falhas)
- **Nota**: Versão simplificada (apenas do worker local). Ver [ESTATISTICAS.md](ESTATISTICAS.md)

## 🔧 Configuração

### Argumentos de Linha de Comando

#### UserApp
```bash
java -jar UserApp.jar [IP_RABBITMQ] [PORT] [EXCHANGE] [QUEUE]
```
Valores padrão: `localhost 5672 request-exchange work-queue`

#### Worker
```bash
java -jar Worker.jar [IP_RABBITMQ] [PORT] [QUEUE] [DIRECTORY]
```
Valores padrão: `localhost 5672 work-queue /var/sharedfiles`

#### Configurador
```bash
java -jar Configurator.jar [IP_RABBITMQ] [PORT]
```
Valores padrão: `localhost 5672`

### Configuração Automática

O Worker cria automaticamente as estruturas necessárias (exchange, queue, binding) ao iniciar. O Configurador é opcional.

## 🧪 Testes

### Teste Local (Localhost)

Ver [TESTES.md](TESTES.md) para guia completo.

**Resumo:**
1. RabbitMQ a correr (Docker)
2. Worker: `java -jar Worker.jar localhost 5672 work-queue /tmp/test-emails`
3. UserApp: `java -jar UserApp.jar`

### Teste com Múltiplos Workers

Use o script `start-workers.sh`:

```bash
./start-workers.sh [num_workers] [directory]
# Exemplo: ./start-workers.sh 3 /tmp/test-emails
```

Para parar: `./stop-workers.sh`

### Teste em VMs (GCP)

**UserApp no PC local, Workers nas VMs:**

1. **VM-1**: RabbitMQ (Docker)
   ```bash
   docker run -d --hostname rabbithost --name rabbitmg \
     -p 5672:5672 -p 15672:15672 rabbitmq:management
   ```

2. **VM-2, VM-3**: Workers
   ```bash
   java -jar Worker.jar <IP_INTERNO_VM1> 5672 work-queue /var/sharedfiles
   ```

3. **PC Local**: UserApp
   ```bash
   java -jar UserApp.jar <IP_PUBLICO_VM1> 5672 request-exchange work-queue
   ```

**Firewall GCP:** Criar regras para portos 5672 e 15672.

## 📊 Verificação

### Interface Web RabbitMQ

Aceder a http://localhost:15672 e verificar:

- **Connections**: Múltiplas conexões (Workers + UserApps)
- **Queues → work-queue**: 
  - **Consumers**: Número de workers
  - **Messages**: Deve estar próximo de 0
- **Exchanges → request-exchange**: Bindings e tráfego

### Logs dos Workers

Cada worker deve receber mensagens diferentes (distribuição round-robin).

## 📝 Tipos de Mensagens

### Request (UserApp → Worker)

```json
{
  "type": "SEARCH" | "GET_FILE" | "GET_STATISTICS",
  "requestId": "uuid",
  "replyTo": "queue-name",
  "replyExchange": "exchange-name",
  "substrings": ["sub1", "sub2"],  // para SEARCH
  "filename": "file.txt"            // para GET_FILE
}
```

### Response (Worker → UserApp)

```json
{
  "type": "SEARCH_RESULT" | "FILE_CONTENT" | "STATISTICS",
  "requestId": "uuid",
  "success": true/false,
  "filenames": ["file1", "file2"], // para SEARCH_RESULT
  "content": "...",                // para FILE_CONTENT
  "totalRequests": 100,            // para STATISTICS
  "successfulRequests": 95,        // para STATISTICS
  "failedRequests": 5              // para STATISTICS
}
```

## 🔍 Funcionalidades Implementadas

- ✅ Pesquisa de substrings em ficheiros .txt
- ✅ Obtenção de conteúdo de ficheiro
- ✅ Estatísticas (versão simplificada - apenas worker local)
- ✅ Work-queue pattern (distribuição de carga)
- ✅ Comunicação assíncrona request/response
- ✅ Serialização JSON com Gson
- ✅ Parametrização via linha de comando

## ⚠️ Limitações Conhecidas

1. **Estatísticas**: Versão simplificada - apenas do worker local. Para sistema completo, ver [ESTATISTICAS.md](ESTATISTICAS.md)
2. **Gluster**: Caminho configurável, mas falta setup nas VMs
3. **Spread**: Não integrado (necessário para estatísticas globais)

## 🚀 Próximos Passos (Sistema Completo)

Para completar o TPA2, falta:

1. **Integração com Spread Toolkit**
   - Comunicação multicast entre workers
   - Membership do grupo
   - Eleição de coordenador

2. **Algoritmo de Consenso**
   - Eleger worker coordenador para estatísticas
   - Agregar estatísticas de todos os workers

3. **Integração com Gluster**
   - Acesso aos ficheiros replicados
   - Garantir que todos os workers acedem ao mesmo volume

## 📚 Documentação Adicional

- [TESTES.md](TESTES.md) - Guia completo de testes
- [ESTATISTICAS.md](ESTATISTICAS.md) - Status da implementação de estatísticas
- [COMPILACAO.md](COMPILACAO.md) - Como compilar no IntelliJ

## 🐛 Troubleshooting

### UserApp não consegue conectar
- Verificar que RabbitMQ está a correr: `docker ps`
- Verificar firewall (se em VMs)
- Verificar IP/porto

### Worker não recebe mensagens
- Verificar que Worker está conectado (interface web)
- Verificar que queue `work-queue` existe
- Verificar binding

### Mensagens não são entregues
- Verificar que exchange `request-exchange` existe (DIRECT)
- Verificar routing key (`work-queue`)
- Verificar logs: `docker logs rabbitmg`

## 📄 Licença

Trabalho académico - ISEL/CD-2526
