package rmqconfigurator;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.simple.SimpleLoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class RabbitConfigurator {




    private static int Menu() {
        int op;
        Scanner scan = new Scanner(System.in);
        do {
            System.out.println();
            System.out.println("    MENU");
            System.out.println(" 0 - Create Exchange");
            System.out.println(" 1 - Create Exchange DIRECT with Alternate Exchange");
            System.out.println(" 2 - Create Queue");
            System.out.println(" 3 - Bind Queue to Exchange");
            System.out.println(" 4- Bind Exchange to Exchange");
            System.out.println(" 5 - Bind Queue to Exchange with Headers");
            System.out.println("99 - Exit");
            System.out.println();
            System.out.println("Choose an Option?");
            op = scan.nextInt();
        } while (!((op >= 0 && op <= 5) || op == 99));
        return op;
    }

    private static String readline(String msg) {
        Scanner scaninput = new Scanner(System.in);
        System.out.println(msg);
        return scaninput.nextLine();
    }

    static Connection connection = null;
    static Channel channel = null;
    static Logger logger=new SimpleLoggerFactory().getLogger("RabbitMQ-configurator");
    static String IP_BROKER="localhost";

    public static void main(String[] args) {

        try {
            if (args.length > 0) {
                IP_BROKER=args[0];
            }
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(IP_BROKER);
            factory.setPort(5672);

            connection = factory.newConnection();
            channel = connection.createChannel();

            boolean end = false;
            while (!end) {
                int option = Menu();
                switch (option) {
                    case 0:
                        CreateExchange();
                        break;
                    case 1:
                        CreateExchangeWithAlternateExchange();
                        break;
                    case 2:
                        CreateQueue();
                        break;
                    case 3:
                        BindQueue2Exchange();
                        break;
                    case 4:
                        BindExchange2Exchange();
                        break;
                    case 5:
                        BindQueuesToExchangeWithHeaders();
                        break;
                    case 99:
                        end = true;
                        break;
                }
            }

            channel.close();
            connection.close();
        } catch (Exception ex) {
            ex.printStackTrace();

        }
    }

    static BuiltinExchangeType readEXchangeType() {
        int op;
        Scanner scan = new Scanner(System.in);
        BuiltinExchangeType[] tipos = new BuiltinExchangeType[]{
                BuiltinExchangeType.DIRECT,
                BuiltinExchangeType.FANOUT,
                BuiltinExchangeType.TOPIC,
                BuiltinExchangeType.HEADERS
        };
        do {
            System.out.println();
            System.out.println("        EXCHANGE TYPE");
            System.out.println(" 0 - " + BuiltinExchangeType.DIRECT);
            System.out.println(" 1 - " + BuiltinExchangeType.FANOUT);
            System.out.println(" 2 - " + BuiltinExchangeType.TOPIC);
            System.out.println(" 3 - " + BuiltinExchangeType.HEADERS);
            System.out.println();
            System.out.println("Choose an Option?");
            op = scan.nextInt();
        } while (!(op >= 0 && op <= 3));
        return tipos[op];
    }

    static void CreateExchange() throws IOException {
        //durable true if we are declaring a durable exchange (the exchange will survive a server restart)
        //Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException;
        channel.exchangeDeclare(readline("Exchange Name?"), readEXchangeType(), true);
    }

    static void CreateExchangeWithAlternateExchange() throws IOException {
        //durable true if we are declaring a durable exchange (the exchange will survive a server restart)
        //Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException;
        // alternate exchange com binding para alternate-queue
        String alternateExchangeName=readline("Alternate Exchange Name?");
        channel.exchangeDeclare(alternateExchangeName, BuiltinExchangeType.FANOUT, true);
        channel.queueDeclare("alternate-queue", true, false, false, null);
        channel.queueBind("alternate-queue", alternateExchangeName, "");
        // exchange com alternate exchange
        String exchangeName=readline("Exchange Name?");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("alternate-exchange", alternateExchangeName);
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true, false, args);

    }

    static void CreateQueue() throws IOException {
//        @param durable true if we are declaring a durable queue (the queue will survive a server restart)
//     * @param exclusive true if we are declaring an exclusive queue (restricted to this connection)
//     * @param autoDelete true if we are declaring an autodelete queue (server will delete it when no longer in use)
        //Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete,
        //                                 Map<String, Object> arguments) throws IOException;
        channel.queueDeclare(readline("Queue Name?"), true, false, false, null);

    }

    static void BindQueue2Exchange() throws IOException {
        //Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException;
        channel.queueBind(readline(
                        "Queue name?"),
                readline("Exchange name?"),
                readline("Binding Key/Routing Key?")
        );
    }

    static void BindExchange2Exchange() throws IOException {
        channel.exchangeBind(readline(
                        "Destination Exchange name?"),
                readline("Source Exchange name?"),
                readline("Binding Key/Routing Key?")
        );
    }

    static void BindQueuesToExchangeWithHeaders() throws IOException {
        // https://jstobigdata.com/rabbitmq/headers-exchange-in-amqp-rabbitmq/
//        here are 2 types of headers matching allowed which are any (similar to logical OR) or all (similar to logical AND).
//        They are represented in the bindings as { "x-match", "any" ..} or { “x-match”, “all” ..}.
//        The x-match = any means, a message sent to the Exchange should contain at least one of the headers that Queue is linked with, then the message will be routed to the Queue.
//        On the other hand, if a queue is bound with headers has x-match = all, messages that have all of its listed headers will be forwarded to the Queue.
        String exchangeName = readline("Nome do Exchange?");
        String queueNameAny = readline("Nome do Queue para receber messages with <any> headers?");
        String queueNameAll = readline("Nome do Queue para receber messages with <all> headers?");
        Map<String, Object> HeadersBindingAny = new HashMap<>();
        Map<String, Object> HeadersBindingAll = new HashMap<>();

        String headersString = readline("Quais os valores de headers separados por espaço?");
        String[] listHeaders = headersString.split(" ");
        HeadersBindingAny.put("x-match", "any"); //Match any of the header
        HeadersBindingAll.put("x-match", "all"); //Match all of the headers

        for (int i = 0; i < listHeaders.length; i++) {
            HeadersBindingAny.put("h" + i, listHeaders[i]);
            HeadersBindingAll.put("h" + i, listHeaders[i]);
        }

        channel.queueBind(queueNameAny, exchangeName, "", HeadersBindingAny);
        channel.queueBind(queueNameAll, exchangeName, "", HeadersBindingAll);
        Map<String, Object> HeadersBindingSpec = new HashMap<>();
        HeadersBindingSpec.put("x-match", "all"); //Match all of the headers
        String queueNameSpec = readline("Nome do Queue para receber messages with specific header?");
        String hkey = readline("Qual a key header especifico");
        String hvalue = readline("Qual o valor do header especifico");
        HeadersBindingSpec.put(hkey, hvalue);
        channel.queueBind(queueNameSpec, exchangeName, "", HeadersBindingSpec);
    }

}
