package configurator;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Configurador do RabbitMQ para o TPA2
 * 
 * Cria:
 * - Exchange "request-exchange" (DIRECT, durable)
 * - Queue "work-queue" (durable)
 * - Binding entre exchange e queue
 * 
 * Uso: java -jar configurator.jar <ipRabbitMQ> <portRabbitMQ>
 */
public class TPA2Configurator {

    private static String IP_BROKER = "localhost";
    private static int PORT_BROKER = 5672;
    private static String REQUEST_EXCHANGE = "request-exchange";
    private static String WORK_QUEUE = "work-queue";

    public static void main(String[] args) {
        try {
            if (args.length >= 1) IP_BROKER = args[0];
            if (args.length >= 2) PORT_BROKER = Integer.parseInt(args[1]);
            if (args.length >= 3) REQUEST_EXCHANGE = args[2];
            if (args.length >= 4) WORK_QUEUE = args[3];

            System.out.println("TPA2 RabbitMQ Configurator");
            System.out.println("Connecting to: " + IP_BROKER + ":" + PORT_BROKER);

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(IP_BROKER);
            factory.setPort(PORT_BROKER);

            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // Criar exchange DIRECT para pedidos (durable)
            channel.exchangeDeclare(REQUEST_EXCHANGE, BuiltinExchangeType.DIRECT, true);
            System.out.println("Exchange created: " + REQUEST_EXCHANGE);

            // Criar queue de trabalho (durable)
            channel.queueDeclare(WORK_QUEUE, true, false, false, null);
            System.out.println("Queue created: " + WORK_QUEUE);

            // Bind queue ao exchange com routing key = nome da queue
            channel.queueBind(WORK_QUEUE, REQUEST_EXCHANGE, WORK_QUEUE);
            System.out.println("Binding created: " + REQUEST_EXCHANGE + " -> " + WORK_QUEUE + " (routing key: " + WORK_QUEUE + ")");

            channel.close();
            connection.close();

            System.out.println("Configuration completed successfully!");

        } catch (Exception e) {
            System.err.println("Configuration error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

