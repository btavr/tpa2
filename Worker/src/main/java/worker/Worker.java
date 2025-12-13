package worker;

import com.rabbitmq.client.*;
import spread.SpreadException;
import worker.messages.RequestStatistics;
import worker.messages.RequestUserApp;
import worker.messages.ResponseUserApp;
import worker.spread.GroupMember;
import worker.util.FileSearcher;
import worker.util.MessageStatisticsSerializer;
import worker.util.MessageUserAppSerializer;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Worker que processa pedidos de pesquisa, obtenção de ficheiros e estatísticas
 * <p>
 * Uso: java -jar worker.jar <ipRabbitMQ> <portRabbitMQ> <workQueue> <directoryPath>
 * Exemplo: java -jar worker.jar 10.128.0.8 5672 work-queue /var/sharedfiles
 */
public class Worker {

    private static String IP_BROKER = "localhost";
    private static int PORT_BROKER = 5672;
    private static String WORK_QUEUE = "work-queue";
    private static String DIRECTORY_PATH = "/var/sharedfiles";
    private static String DAEMON_IP = "localhost";
    private static int DAEMON_PORT = 5672;
    private static String SPREAD_GROUP = "group";
    private static String WORKER_STRING = "Worker-";

    // Estatísticas locais do worker
    private static int totalRequests = 0;
    private static int successfulRequests = 0;
    private static int failedRequests = 0;
    private static final Object statsLock = new Object();

    public static void main(String[] args) {
        try {
            // Parse argumentos
            if (args.length >= 1) IP_BROKER = args[0];
            if (args.length >= 2) PORT_BROKER = Integer.parseInt(args[1]);
            if (args.length >= 3) WORK_QUEUE = args[2];
            if (args.length >= 4) DIRECTORY_PATH = args[3];
            if (args.length >= 5) DAEMON_IP = args[4];
            if (args.length >= 6) DAEMON_PORT = Integer.parseInt(args[5]);
            if (args.length >= 7) SPREAD_GROUP = args[6];

            System.out.println("Worker starting...");
            System.out.println("RabbitMQ: " + IP_BROKER + ":" + PORT_BROKER);
            System.out.println("Work Queue: " + WORK_QUEUE);
            System.out.println("Directory: " + DIRECTORY_PATH);

            // Conectar ao RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(IP_BROKER);
            factory.setPort(PORT_BROKER);

            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // Configurar QoS: apenas 1 mensagem por vez (fair dispatch)
            channel.basicQos(1);

            final String userName = WORKER_STRING.concat(String.valueOf(UUID.randomUUID()));
            final GroupMember member = new GroupMember(userName, DAEMON_IP, DAEMON_PORT);

            member.addMemberToGroup(SPREAD_GROUP);


            System.out.println("Waiting for messages. To exit press CTRL+C");

            // Callback para processar mensagens
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    // Deserializar request
                    byte[] body = delivery.getBody();
                    RequestUserApp requestUserApp = MessageUserAppSerializer.requestFromBytes(body);

                    System.out.println("Received request: " + requestUserApp.getType() + " (ID: " + requestUserApp.getRequestId() + ")");

                    // Processar request
                    ResponseUserApp responseUserApp = processRequest(requestUserApp, member);

                    // Enviar resposta
                    sendResponse(channel, requestUserApp, responseUserApp);

                    // Acknowledgment
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        // Rejeitar mensagem e não reenviar (dead letter)
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    } catch (IOException ex) {
                        System.err.println("Error nacking message: " + ex.getMessage());
                    }
                }
            };

            // Callback para cancelamento
            CancelCallback cancelCallback = (consumerTag) -> {
                System.out.println("Consumer cancelled: " + consumerTag);
            };

            // Iniciar consumo
            String consumerTag = channel.basicConsume(WORK_QUEUE, false, deliverCallback, cancelCallback);
            System.out.println("Consumer started with tag: " + consumerTag);

            // Manter worker em execução
            System.in.read();

            // Cleanup
            channel.basicCancel(consumerTag);
            channel.close();
            connection.close();

        } catch (Exception e) {
            System.err.println("Worker error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processa um request e retorna a resposta correspondente
     */
    private static ResponseUserApp processRequest(final RequestUserApp requestUserApp, final GroupMember member) {
        synchronized (statsLock) {
            totalRequests++;
        }

        try {
            ResponseUserApp responseUserApp;

            switch (requestUserApp.getType()) {
                case SEARCH:
                    responseUserApp = processSearchRequest(requestUserApp);
                    break;
                case GET_FILE:
                    responseUserApp = processGetFileRequest(requestUserApp);
                    break;
                case GET_STATISTICS:
                    responseUserApp = processGetStatisticsRequest(requestUserApp, member);
                    break;
                default:
                    responseUserApp = new ResponseUserApp();
                    responseUserApp.setType(ResponseUserApp.ResponseType.UNKNOWN);
                    responseUserApp.setRequestId(requestUserApp.getRequestId());
                    responseUserApp.setSuccess(false);
                    responseUserApp.setErrorMessage("Unknown request type");
            }

            if (responseUserApp.isSuccess()) {
                synchronized (statsLock) {
                    successfulRequests++;
                }
            } else {
                synchronized (statsLock) {
                    failedRequests++;
                }
            }

            return responseUserApp;

        } catch (Exception e) {
            synchronized (statsLock) {
                failedRequests++;
            }
            ResponseUserApp errorResponseUserApp = new ResponseUserApp();
            errorResponseUserApp.setType(ResponseUserApp.ResponseType.ERROR);
            errorResponseUserApp.setRequestId(requestUserApp.getRequestId());
            errorResponseUserApp.setSuccess(false);
            errorResponseUserApp.setErrorMessage("Error processing request: " + e.getMessage());
            return errorResponseUserApp;
        }
    }

    /**
     * Processa pedido de pesquisa de substrings
     */
    private static ResponseUserApp processSearchRequest(RequestUserApp requestUserApp) throws IOException {
        List<String> filenames = FileSearcher.getMatchingFilenames(DIRECTORY_PATH, requestUserApp.getSubstrings());

        System.out.println("Filenames: " + filenames);

        ResponseUserApp responseUserApp = new ResponseUserApp();
        responseUserApp.setType(ResponseUserApp.ResponseType.SEARCH_RESULT);
        responseUserApp.setRequestId(requestUserApp.getRequestId());
        responseUserApp.setFilenames(filenames);
        responseUserApp.setSuccess(true);

        return responseUserApp;
    }

    /**
     * Processa pedido de obtenção de conteúdo de ficheiro
     */
    private static ResponseUserApp processGetFileRequest(RequestUserApp requestUserApp) throws IOException {
        String content = FileSearcher.getFileContent(DIRECTORY_PATH, requestUserApp.getFilename());

        ResponseUserApp responseUserApp = new ResponseUserApp();
        responseUserApp.setType(ResponseUserApp.ResponseType.FILE_CONTENT);
        responseUserApp.setRequestId(requestUserApp.getRequestId());
        responseUserApp.setFilename(requestUserApp.getFilename());
        responseUserApp.setContent(content);
        responseUserApp.setSuccess(true);

        return responseUserApp;
    }

    /**
     * Processa pedido de estatísticas
     * Nota: Esta é uma versão simplificada. No sistema completo, o worker coordenador
     * agregaria estatísticas de todos os workers via Spread.
     */
    private static ResponseUserApp processGetStatisticsRequest(RequestUserApp requestUserApp, final GroupMember member) throws SpreadException {
        final RequestStatistics requestStatistics = new RequestStatistics();
        requestStatistics.setType(RequestStatistics.RequestType.NOT_LEADER);
        requestStatistics.setRequestId(requestUserApp.getRequestId());
        requestStatistics.setReplyExchange(requestUserApp.getReplyExchange());
        requestStatistics.setReplyTo(requestUserApp.getReplyTo());
        synchronized (statsLock) {
            requestStatistics.setTotalRequests(totalRequests);
            requestStatistics.setSuccessfulRequests(successfulRequests);
            requestStatistics.setFailedRequests(failedRequests);
        }

        member.sendMulticastMessage(MessageStatisticsSerializer.toBytes(requestStatistics));
        return null;
    }

    /**
     * Envia resposta para o default exchange (DIRECT)
     * O default exchange roteia diretamente para a queue cujo nome corresponde à routing key
     */
    private static void sendResponse(Channel channel, RequestUserApp requestUserApp, ResponseUserApp responseUserApp)
            throws IOException {
        if (responseUserApp == null) {
            System.out.println("There isn't any message to be send.");
        }

        System.out.println("filenames: " + response.getFilenames());
        System.out.println("request - replyToExchange: " + request.getReplyExchange() + " (default exchange)");
        System.out.println("request - replyTo: " + request.getReplyTo());

        // Publicar resposta no default exchange (DIRECT) com routing key = nome da queue
        // O default exchange ("" vazio) roteia diretamente para a queue com o nome igual à routing key
        channel.basicPublish(
            request.getReplyExchange(),  // "" = default exchange (DIRECT)
            request.getReplyTo(),         // routing key = nome da queue de respostas
            null,
            responseBytes
        );

        System.out.println("Response sent for request: " + requestUserApp.getRequestId());
    }
}

