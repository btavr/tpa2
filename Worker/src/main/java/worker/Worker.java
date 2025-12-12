package worker;

import com.rabbitmq.client.*;
import worker.messages.Request;
import worker.messages.Response;
import worker.util.FileSearcher;
import worker.util.MessageSerializer;

import java.io.IOException;
import java.util.List;

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

            System.out.println("Waiting for messages. To exit press CTRL+C");

            // Callback para processar mensagens
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    // Deserializar request
                    byte[] body = delivery.getBody();
                    Request request = MessageSerializer.requestFromBytes(body);

                    System.out.println("Received request: " + request.getType() + " (ID: " + request.getRequestId() + ")");

                    // Processar request
                    Response response = processRequest(request);

                    // Enviar resposta
                    sendResponse(channel, request, response);

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
    private static Response processRequest(Request request) {
        synchronized (statsLock) {
            totalRequests++;
        }

        try {
            Response response;

            switch (request.getType()) {
                case SEARCH:
                    response = processSearchRequest(request);
                    break;
                case GET_FILE:
                    response = processGetFileRequest(request);
                    break;
                case GET_STATISTICS:
                    response = processGetStatisticsRequest(request);
                    break;
                default:
                    response = new Response();
                    response.setType(Response.ResponseType.SEARCH_RESULT);
                    response.setRequestId(request.getRequestId());
                    response.setSuccess(false);
                    response.setErrorMessage("Unknown request type");
            }

            if (response.isSuccess()) {
                synchronized (statsLock) {
                    successfulRequests++;
                }
            } else {
                synchronized (statsLock) {
                    failedRequests++;
                }
            }

            return response;

        } catch (Exception e) {
            synchronized (statsLock) {
                failedRequests++;
            }
            Response errorResponse = new Response();
            errorResponse.setType(Response.ResponseType.SEARCH_RESULT);
            errorResponse.setRequestId(request.getRequestId());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Error processing request: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Processa pedido de pesquisa de substrings
     */
    private static Response processSearchRequest(Request request) throws IOException {
        List<String> filenames = FileSearcher.getMatchingFilenames(DIRECTORY_PATH, request.getSubstrings());

        Response response = new Response();
        response.setType(Response.ResponseType.SEARCH_RESULT);
        response.setRequestId(request.getRequestId());
        response.setFilenames(filenames);
        response.setSuccess(true);

        return response;
    }

    /**
     * Processa pedido de obtenção de conteúdo de ficheiro
     */
    private static Response processGetFileRequest(Request request) throws IOException {
        String content = FileSearcher.getFileContent(DIRECTORY_PATH, request.getFilename());

        Response response = new Response();
        response.setType(Response.ResponseType.FILE_CONTENT);
        response.setRequestId(request.getRequestId());
        response.setFilename(request.getFilename());
        response.setContent(content);
        response.setSuccess(true);

        return response;
    }

    /**
     * Processa pedido de estatísticas
     * Nota: Esta é uma versão simplificada. No sistema completo, o worker coordenador
     * agregaria estatísticas de todos os workers via Spread.
     */
    private static Response processGetStatisticsRequest(Request request) {
        Response response = new Response();
        response.setType(Response.ResponseType.STATISTICS);
        response.setRequestId(request.getRequestId());

        synchronized (statsLock) {
            response.setTotalRequests(totalRequests);
            response.setSuccessfulRequests(successfulRequests);
            response.setFailedRequests(failedRequests);
        }

        response.setSuccess(true);
        return response;
    }

    /**
     * Envia resposta para o exchange/queue especificado no request
     */
    private static void sendResponse(Channel channel, Request request, Response response)
            throws IOException {
        byte[] responseBytes = MessageSerializer.toBytes(response);

        // Publicar resposta no exchange especificado, com routing key = replyTo (nome da queue)
        channel.basicPublish(
                request.getReplyExchange(),
                request.getReplyTo(),
                null,
                responseBytes
        );

        System.out.println("Response sent for request: " + request.getRequestId());
    }
}

