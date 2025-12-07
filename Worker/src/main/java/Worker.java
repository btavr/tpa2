import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.exit;

public class Worker {

    private static final Boolean WORKER_AUTO_ACK_FALSE_VALUE = Boolean.FALSE;

    public static void main(String[] args) {
        for (final String arg : args) {
            System.out.println("argumento: " + arg);
        }

        final String directoryPath;
        final String operation;
        final String ipRabbitMQ;
        final int portRabbitMQ;
        final String workerQueueName;
        List<String> substringsList = new ArrayList<>();
        if (args.length > 0) {
            ipRabbitMQ = args[0];
            portRabbitMQ = Integer.parseInt(args[1]);
            workerQueueName = args[2];
            operation = "null"; // operation = args[0]; Para ser definido na mensagem
            directoryPath = "null"; // directoryPath = args[1]; Para ser definido na mensagem
            substringsList.addAll(Arrays.asList(args).subList(2, args.length));
        } else {
            ipRabbitMQ = null;
            portRabbitMQ = 0;
            workerQueueName = null;
            directoryPath = null;
            operation = null;
            System.out.println("Nothing to search!");
            exit(-1);
        }

        final Channel channel = configureRabbit(ipRabbitMQ, portRabbitMQ);

        if (channel == null) {
            System.out.println("Channel isn't completely configured. Returning...");
            return;
        }

        final DeliverCallback deliverCallbackWithoutAck = (consumerTag, delivery) -> {
            String recMessage = new String(delivery.getBody(), "UTF-8");
            String routingKey = delivery.getEnvelope().getRoutingKey();
            long deliverTag = delivery.getEnvelope().getDeliveryTag();
            System.out.println(consumerTag + ": Message Received:" + routingKey + ":" + recMessage);

            try {
                workerWork(operation, directoryPath, substringsList);

                channel.basicAck(deliverTag, false);
            } catch (final Exception e) {
                // TODO: Tratar exceção!
                // The b1 is a requeue parameter. Handle it better
                channel.basicNack(deliverTag, false, true);
            }
        };

        // Consumer handler to receive cancel receiving messages
        final CancelCallback cancelCallback = (consumerTag) -> {
            System.out.println("CANCEL Received! " + consumerTag);
        };

        try {
            channel.basicConsume(workerQueueName, WORKER_AUTO_ACK_FALSE_VALUE, deliverCallbackWithoutAck, cancelCallback);
        } catch (final IOException e) {
            // TODO: Tratar exceção!
            e.printStackTrace();
        }

    }

    private static Channel configureRabbit(final String brokerIP, final int brokerPort) {
        try {
            final ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(brokerIP);
            factory.setPort(brokerPort);

            final Connection connection = factory.newConnection();
            final Channel channel = connection.createChannel();

            final int prefetchCount = 1;
            channel.basicQos(prefetchCount);
            return channel;
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void workerWork(final String operation, final String directoryPath, final List<String> substringsList) {
        switch (operation) {
            case "localizar" -> {
                try {
                    final List<String> emailFileNames = FileReadLogic.searchInsideEmails(directoryPath, substringsList);

                    if (emailFileNames.isEmpty()) {
                        System.out.println("No file has given strings");
                    } else {
                        System.out.println("List of names with the given strings");
                        for (final String fileName : emailFileNames) {
                            System.out.print(fileName + " / ");
                        }
                        System.out.println();
                    }

                } catch (final Exception e) {
                    System.out.println("------ Exceção");
                    // TODO: Tratar exceção!
                    System.out.println(e.getCause());
                }
            }
            case "ler" -> {
                try {
                    final String emailContent = FileReadLogic.readEmail(Paths.get(directoryPath, substringsList.getFirst()).toString());
                    System.out.println("Content of the given email file name");
                    System.out.println(emailContent);
                } catch (final Exception e) {
                    System.out.println("------ Exceção");
                    // TODO: Tratar exceção!
                    System.out.println("Given file doesn't exist");
                }
            }
            case "estatistica" -> {
                // TODO: Fazer esta parte -> Método de eleição
            }
            case null, default -> {
                System.out.println("Invalid operation!");
                exit(-1);
            }
        }
        System.out.println("-------------");
    }

}
