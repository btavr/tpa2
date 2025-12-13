# Deploy nas VMs da Cloud (GCP)

## 📍 Onde Substituir os IPs

### 1. Obter IPs das VMs

**Na VM onde está o RabbitMQ (VM-1):**
```bash
# IP interno (para Workers na mesma rede)
hostname -I
# Exemplo: 10.128.0.8

# IP público (para UserApp no PC local)
curl ifconfig.me
# Exemplo: 34.123.45.67
```

**Nota:** No GCP Console também pode ver os IPs em:
- **Compute Engine** → **VM instances** → Ver colunas "Internal IP" e "External IP"

## 🔧 Configuração por Componente

### VM-1: RabbitMQ

**Iniciar RabbitMQ:**
```bash
docker run -d --hostname rabbithost --name rabbitmg \
  -p 5672:5672 -p 15672:15672 rabbitmq:management
```

**Não precisa de IP aqui** - RabbitMQ escuta em todas as interfaces (0.0.0.0)

**Verificar que está a correr:**
```bash
docker ps
```

### VM-2, VM-3: Workers

**Substituir:** `<IP_INTERNO_VM1>` pelo IP interno da VM-1

```bash
java -jar Worker-jar-with-dependencies.jar \
  <IP_INTERNO_VM1> 5672 work-queue /var/sharedfiles
```

**Exemplo:**
```bash
java -jar Worker-jar-with-dependencies.jar \
  10.128.0.8 5672 work-queue /var/sharedfiles
```

**Porquê IP interno?**
- Workers estão na mesma rede que RabbitMQ
- Mais rápido e eficiente
- Não precisa de firewall rules para IPs internos

### PC Local: UserApp

**Substituir:** `<IP_PUBLICO_VM1>` pelo IP público da VM-1

```bash
java -jar UserApp-jar-with-dependencies.jar \
  <IP_PUBLICO_VM1> 5672 request-exchange work-queue
```

**Exemplo:**
```bash
java -jar UserApp-jar-with-dependencies.jar \
  34.123.45.67 5672 request-exchange work-queue
```

**Porquê IP público?**
- UserApp está no PC local (fora da rede GCP)
- Precisa de IP público para aceder ao RabbitMQ na VM

## 🔥 Configurar Firewall GCP

**IMPORTANTE:** Precisa criar regras de firewall no GCP para permitir conexões externas.

### No GCP Console:

1. **VPC Network** → **Firewall Rules** → **Create Firewall Rule**

2. **Regra para RabbitMQ (porto 5672):**
   - Nome: `allow-rabbitmq`
   - Direction: **Ingress**
   - Targets: **All instances in the network**
   - Source IP ranges: `0.0.0.0/0` (ou IP do seu PC para mais segurança)
   - Protocols and ports: **TCP** → `5672`
   - Create

3. **Regra para RabbitMQ Management (porto 15672):**
   - Nome: `allow-rabbitmq-management`
   - Direction: **Ingress**
   - Targets: **All instances in the network**
   - Source IP ranges: `0.0.0.0/0` (ou IP do seu PC)
   - Protocols and ports: **TCP** → `15672`
   - Create

**Ou via gcloud CLI:**
```bash
# Regra para RabbitMQ
gcloud compute firewall-rules create allow-rabbitmq \
  --allow tcp:5672 \
  --source-ranges 0.0.0.0/0 \
  --description "Allow RabbitMQ connections"

# Regra para Management
gcloud compute firewall-rules create allow-rabbitmq-management \
  --allow tcp:15672 \
  --source-ranges 0.0.0.0/0 \
  --description "Allow RabbitMQ Management UI"
```

## 📋 Checklist de Deploy

### Na VM-1 (RabbitMQ):

- [ ] RabbitMQ iniciado (Docker)
- [ ] Verificar que está a correr: `docker ps`
- [ ] Obter IP interno: `hostname -I`
- [ ] Obter IP público: `curl ifconfig.me`
- [ ] Testar interface web: `http://<IP_PUBLICO>:15672`

### Nas VMs-2, VM-3 (Workers):

- [ ] JAR do Worker copiado para a VM
- [ ] Ficheiros de teste em `/var/sharedfiles` (ou Gluster montado)
- [ ] Worker iniciado com IP interno da VM-1
- [ ] Verificar logs do Worker

### No PC Local (UserApp):

- [ ] JAR do UserApp compilado
- [ ] Firewall GCP configurado (portos 5672 e 15672)
- [ ] UserApp iniciado com IP público da VM-1
- [ ] Testar conexão

## 🧪 Testar Conexão

### Do PC Local para RabbitMQ:

```bash
# Testar conectividade
telnet <IP_PUBLICO_VM1> 5672
# ou
nc -zv <IP_PUBLICO_VM1> 5672

# Se funcionar, deve conectar
```

### Da VM-2 para RabbitMQ (VM-1):

```bash
# Testar conectividade interna
telnet <IP_INTERNO_VM1> 5672
# ou
nc -zv <IP_INTERNO_VM1> 5672
```

## 📝 Exemplo Completo

**Assumindo:**
- VM-1 (RabbitMQ): IP interno `10.128.0.8`, IP público `34.123.45.67`
- VM-2 (Worker 1): IP interno `10.128.0.10`
- VM-3 (Worker 2): IP interno `10.128.0.11`

### VM-1:
```bash
docker run -d --hostname rabbithost --name rabbitmg \
  -p 5672:5672 -p 15672:15672 rabbitmq:management
```

### VM-2:
```bash
java -jar Worker-jar-with-dependencies.jar \
  10.128.0.8 5672 work-queue /var/sharedfiles
```

### VM-3:
```bash
java -jar Worker-jar-with-dependencies.jar \
  10.128.0.8 5672 work-queue /var/sharedfiles
```

### PC Local:
```bash
java -jar UserApp-jar-with-dependencies.jar \
  34.123.45.67 5672 request-exchange work-queue
```

## 🐛 Troubleshooting

### UserApp não consegue conectar

1. **Verificar firewall GCP:**
   ```bash
   gcloud compute firewall-rules list
   # Verificar que allow-rabbitmq existe
   ```

2. **Verificar IP público:**
   ```bash
   # Na VM-1
   curl ifconfig.me
   ```

3. **Testar conectividade:**
   ```bash
   # Do PC local
   telnet <IP_PUBLICO> 5672
   ```

4. **Verificar que RabbitMQ está a correr:**
   ```bash
   # Na VM-1
   docker ps
   docker logs rabbitmg
   ```

### Workers não conseguem conectar

1. **Verificar IP interno:**
   ```bash
   # Na VM-1
   hostname -I
   ```

2. **Verificar que estão na mesma rede:**
   - Todas as VMs devem estar no mesmo VPC network

3. **Testar conectividade interna:**
   ```bash
   # Na VM-2
   telnet <IP_INTERNO_VM1> 5672
   ```

### Interface Web não acessível

1. **Verificar firewall (porto 15672)**
2. **Verificar que RabbitMQ está a correr**
3. **Aceder:** `http://<IP_PUBLICO_VM1>:15672`

## 💡 Dicas

1. **Usar IPs estáticos** no GCP para não mudarem
2. **Guardar IPs num ficheiro** para referência rápida
3. **Testar localmente primeiro** antes de deploy nas VMs
4. **Verificar logs** se algo não funcionar

## 📄 Resumo dos IPs

| Componente | Onde Usar | Tipo de IP | Exemplo |
|------------|-----------|------------|---------|
| Worker | Argumento 1 | IP interno da VM-1 | `10.128.0.8` |
| UserApp | Argumento 1 | IP público da VM-1 | `34.123.45.67` |
| RabbitMQ | Não precisa | - | - |

**Regra geral:**
- **Workers** → IP interno (mesma rede)
- **UserApp** → IP público (fora da rede)

