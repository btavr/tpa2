# TPA2 - Sistema Distribuído com RabbitMQ e Spread

## Introdução

Este projeto implementa um sistema distribuído de processamento de pedidos utilizando **RabbitMQ** para comunicação assíncrona e **Spread** para coordenação entre workers. O sistema permite:

- **Pesquisa de substrings** em ficheiros de email (.txt)
- **Obtenção de conteúdo** de ficheiros específicos
- **Consulta de estatísticas** agregadas de todos os workers

### Componentes

- **UserApp**: Aplicação cliente que envia pedidos e recebe respostas
- **Worker**: Processa pedidos, pesquisa ficheiros e agrega estatísticas via Spread
- **TPA2-RabbitMQ-Configurator**: Configura exchanges e queues no RabbitMQ

O sistema utiliza um exchange **FANOUT** para distribuir pedidos a todos os workers e um algoritmo de **eleição de líder** via Spread para coordenar a agregação de estatísticas.

---

# Como Compilar

## IntelliJ IDEA

### Método 1: Maven Tool Window

1. `View` → `Tool Windows` → `Maven`
2. Para cada projeto (UserApp, Worker, Configurator):
   - Expandir `Lifecycle`
   - Clicar duas vezes em `clean`
   - Clicar duas vezes em `package`

### Método 2: Terminal Integrado

```bash
# Compilar todos de uma vez
mvn clean package -pl UserApp,Worker,TPA2-RabbitMQ-Configurator -am

# Ou individualmente
cd UserApp && mvn clean package
cd ../Worker && mvn clean package
cd ../TPA2-RabbitMQ-Configurator && mvn clean package
```

## Onde estão os JARs?

```
UserApp/target/UserApp-jar-with-dependencies.jar
Worker/target/Worker-jar-with-dependencies.jar
TPA2-RabbitMQ-Configurator/target/TPA2-RabbitMQ-Configurator-jar-with-dependencies.jar
```

## Troubleshooting

### Erro: "Maven project not found"
- `File` → `Invalidate Caches / Restart`
- Maven tool window → `Reload All Maven Projects`

### Erro: "Dependencies not found"
- Verificar ligação à internet
- Verificar `pom.xml`

