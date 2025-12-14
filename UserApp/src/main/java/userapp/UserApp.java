package userapp;

import com.rabbitmq.client.*;
import userapp.messages.Request;
import userapp.messages.Response;
import userapp.util.MessageSerializer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aplicação cliente que envia pedidos para o sistema e recebe respostas
 * <p>
 * Uso: java -jar userapp.jar <ipRabbitMQ> <portRabbitMQ> <requestExchange> <workQueue>
 * Exemplo: java -jar userapp.jar 10.128.0.8 5672 request-exchange work-queue
 */
public class UserApp {

    private static String IP_BROKER = "localhost";
    private static int PORT_BROKER = 5672;
    private static String REQUEST_EXCHANGE = "request-exchange";
    private static String WORK_QUEUE = "work-queue";
    private static final String DEFAULT_EXCHANGE = "";
    private static final String EMPTY_ROUTING_KEY = "";

    private static Connection connection;
    private static Channel channel;
    private static String responseQueue;
    private static Map<String, Response> pendingResponses = new ConcurrentHashMap<>();
    private static final Object responseLock = new Object();

    public static void main(String[] args) {
        try {
            // Parse argumentos
            if (args.length >= 1) IP_BROKER = args[0];
            if (args.length >= 2) PORT_BROKER = Integer.parseInt(args[1]);
            if (args.length >= 3) REQUEST_EXCHANGE = args[2];
            if (args.length >= 4) WORK_QUEUE = args[3];

            System.out.println("UserApp starting...");
            System.out.println("RabbitMQ: " + IP_BROKER + ":" + PORT_BROKER);
            System.out.println("Request Exchange: " + REQUEST_EXCHANGE);
            System.out.println("Work Queue: " + WORK_QUEUE);

            // Conectar ao RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(IP_BROKER);
            factory.setPort(PORT_BROKER);

            connection = factory.newConnection();
            channel = connection.createChannel();

            // Criar queue exclusiva para respostas (auto-delete quando conexão fechar)
            responseQueue = channel.queueDeclare().getQueue();
            System.out.println("Response queue created: " + responseQueue);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    byte[] body = delivery.getBody();
                    Response response = MessageSerializer.responseFromBytes(body);

                    synchronized (responseLock) {
                        pendingResponses.put(response.getRequestId(), response);
                        responseLock.notifyAll();
                    }

                } catch (Exception e) {
                    System.err.println("Error processing response: " + e.getMessage());
                    e.printStackTrace();
                }
            };

            CancelCallback cancelCallback = (consumerTag) -> {
                System.out.println("Response consumer cancelled: " + consumerTag);
            };

            channel.basicConsume(responseQueue, true, deliverCallback, cancelCallback);
            System.out.println("Response consumer started.");

            // Menu interativo
            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        handleSearchRequest(scanner);
                        break;
                    case "2":
                        handleGetFileRequest(scanner);
                        break;
                    case "3":
                        handleGetStatisticsRequest(scanner);
                        break;
                    case "0":
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            }

            // Terminar e fechar conexões
            channel.close();
            connection.close();
            System.out.println("UserApp closed.");
        } catch (Exception e) {
            System.err.println("UserApp error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println("\n=== UserApp Menu ===");
        System.out.println("1 - Search substrings");
        System.out.println("2 - Get file content");
        System.out.println("3 - Get statistics");
        System.out.println("0 - Exit");
        System.out.print("Choose an option: ");
    }

    /**
     * Configura consumer para receber respostas
     */
    private static void setupResponseConsumer() throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                byte[] body = delivery.getBody();
                Response response = MessageSerializer.responseFromBytes(body);

                synchronized (responseLock) {
                    pendingResponses.put(response.getRequestId(), response);
                    responseLock.notifyAll();
                }

            } catch (Exception e) {
                System.err.println("Error processing response: " + e.getMessage());
                e.printStackTrace();
            }
        };

        CancelCallback cancelCallback = (consumerTag) -> {
            System.out.println("Response consumer cancelled: " + consumerTag);
        };

        channel.basicConsume(responseQueue, true, deliverCallback, cancelCallback);
        System.out.println("Response consumer started.");
    }

    /**
     * Processa pedido de pesquisa de substrings
     */
    private static void handleSearchRequest(Scanner scanner) throws IOException {
        System.out.print("Enter substrings (comma-separated): ");
        String input = scanner.nextLine().trim();
        String[] substringsArray = input.split(",");
        List<String> substrings = new ArrayList<>();
        for (String s : substringsArray) {
            substrings.add(s.trim());
        }

        if (substrings.isEmpty()) {
            System.out.println("Error: At least one substring is required.");
            return;
        }

        String requestId = UUID.randomUUID().toString();
        Request request = new Request(
                Request.RequestType.SEARCH,
                requestId,
                responseQueue,
                DEFAULT_EXCHANGE,
                substrings
        );

        sendRequest(request);
        waitForResponse(requestId);
    }

    /**
     * Processa pedido de obtenção de conteúdo de ficheiro
     */
    private static void handleGetFileRequest(Scanner scanner) throws IOException {
        System.out.print("Enter filename: ");
        String filename = scanner.nextLine().trim();

        if (filename.isEmpty()) {
            System.out.println("Error: Filename is required.");
            return;
        }

        String requestId = UUID.randomUUID().toString();
        Request request = new Request(
                Request.RequestType.GET_FILE,
                requestId,
                responseQueue,
                DEFAULT_EXCHANGE,
                filename
        );

        sendRequest(request);
        waitForResponse(requestId);
    }

    /**
     * Processa pedido de estatísticas
     */
    private static void handleGetStatisticsRequest(Scanner scanner) throws IOException {
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(
                Request.RequestType.GET_STATISTICS,
                requestId,
                responseQueue,
                DEFAULT_EXCHANGE
        );

        sendRequest(request);
        waitForResponse(requestId);
    }

    /**
     * Envia request para o exchange FANOUT
     */
    private static void sendRequest(Request request) throws IOException {
        byte[] requestBytes = MessageSerializer.toBytes(request);

        // Publicar no exchange FANOUT (routing key vazia para FANOUT)
        channel.basicPublish(
                REQUEST_EXCHANGE,
                EMPTY_ROUTING_KEY,  // routing key vazia para FANOUT exchange
                null,
                requestBytes
        );

        System.out.println("Request sent (ID: " + request.getRequestId() + ")");
    }

    /**
     * Espera pela resposta (timeout de 30 segundos)
     */
    private static void waitForResponse(String requestId) {
        synchronized (responseLock) {
            try {
                long startTime = System.currentTimeMillis();
                long timeout = 30000; // 30 segundos

                while (!pendingResponses.containsKey(requestId)) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > timeout) {
                        System.out.println("Timeout waiting for response.");
                        return;
                    }
                    responseLock.wait(timeout - elapsed);
                }

                Response response = pendingResponses.remove(requestId);

                // Imprimir resposta agora (na thread principal)
                System.out.println("=== Response Received ===");
                System.out.println("Request ID: " + response.getRequestId());
                printResponse(response);

            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for response.");
            }
        }
    }

    /**
     * Imprime resposta formatada
     */
    private static void printResponse(Response response) {
        if (!response.isSuccess()) {
            System.out.println("ERROR: " + response.getErrorMessage());
            return;
        }

        switch (response.getType()) {
            case SEARCH_RESULT:
                System.out.println("Search Results:");
                if (response.getFilenames() != null && !response.getFilenames().isEmpty()) {
                    System.out.println("Found " + response.getFilenames().size() + " file(s):");
                    for (String filename : response.getFilenames()) {
                        System.out.println("  - " + filename);
                    }
                } else {
                    System.out.println("No files found.");
                }
                break;

            case FILE_CONTENT:
                System.out.println("File: " + response.getFilename());
                System.out.println("Content:");
                System.out.println("---");
                System.out.println(response.getContent());
                System.out.println("---");
                break;

            case STATISTICS:
                System.out.println("Statistics:");
                System.out.println("  Total Requests: " + response.getTotalRequests());
                System.out.println("  Successful: " + response.getSuccessfulRequests());
                System.out.println("  Failed: " + response.getFailedRequests());
                break;
        }
    }
}

