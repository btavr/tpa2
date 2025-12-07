# Guia de Testes - TPA2 RabbitMQ

## 🎯 Teste Local (Localhost)

### Passo a Passo

1. **Iniciar RabbitMQ**
   ```bash
   docker run -d --hostname rabbithost --name rabbitmg \
     -p 5672:5672 -p 15672:15672 rabbitmq:management
   ```

2. **Preparar Ficheiros**
   ```bash
   mkdir -p /tmp/test-emails
   # Copiar ficheiros .txt para /tmp/test-emails
   ```

3. **Iniciar Worker**
   ```bash
   java -jar Worker/target/Worker-jar-with-dependencies.jar \
     localhost 5672 work-queue /tmp/test-emails
   ```

4. **Iniciar UserApp**
   ```bash
   java -jar UserApp/target/UserApp-jar-with-dependencies.jar
   ```

### Teste com Múltiplos Workers

**Opção 1: Scripts**
```bash
./start-workers.sh 3 /tmp/test-emails
# Para parar: ./stop-workers.sh
```

**Opção 2: Terminais Manuais**
- Abrir múltiplos terminais
- Cada terminal executa um Worker com os mesmos parâmetros

### Verificar Distribuição

1. **Interface Web RabbitMQ** (http://localhost:15672)
   - **Queues → work-queue → Consumers**: Deve mostrar número de workers
   - **Connections**: Múltiplas conexões

2. **Logs dos Workers**
   - Cada worker deve receber mensagens diferentes
   - Distribuição round-robin

## 🌐 Teste em VMs (GCP)

### Cenário: UserApp Local, Workers nas VMs

**VM-1 (RabbitMQ):**
```bash
docker run -d --hostname rabbithost --name rabbitmg \
  -p 5672:5672 -p 15672:15672 rabbitmq:management
```

**VM-2, VM-3 (Workers):**
```bash
java -jar Worker.jar <IP_INTERNO_VM1> 5672 work-queue /var/sharedfiles
```

**PC Local (UserApp):**
```bash
java -jar UserApp.jar <IP_PUBLICO_VM1> 5672 request-exchange work-queue
```

### Firewall GCP

Criar regras para:
- **Porto 5672** (RabbitMQ)
- **Porto 15672** (Management)

## ✅ Checklist de Verificação

- [ ] RabbitMQ a correr
- [ ] Workers iniciados
- [ ] UserApp iniciado
- [ ] Interface web mostra conexões
- [ ] Mensagens são distribuídas entre workers
- [ ] Respostas chegam corretamente
- [ ] Não há perda de mensagens

## 🧪 Testes Específicos

### Teste 1: Round-Robin Distribution
1. Iniciar 3 workers
2. Enviar 9 pedidos sequenciais
3. Verificar que cada worker processou ~3 pedidos

### Teste 2: Múltiplos UserApps
1. Iniciar 2 workers
2. Iniciar 3 UserApps
3. Cada UserApp envia pedidos simultaneamente
4. Verificar que todas as respostas chegam corretamente

### Teste 3: Worker Desconecta
1. Iniciar 3 workers
2. Enviar pedidos
3. Parar um worker (Ctrl+C)
4. Verificar que outros workers continuam a processar

## 🐛 Troubleshooting

### Apenas um worker recebe mensagens
- Verificar que todos usam a mesma queue name
- Verificar binding na interface web

### UserApp não recebe respostas
- Verificar que `replyTo` está correto
- Verificar que queue de resposta foi criada

### Mensagens acumulam na queue
- Adicionar mais workers
- Verificar logs de erros

