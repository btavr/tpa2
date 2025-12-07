package userapp.messages;

import java.util.List;

/**
 * Classe que representa um pedido enviado pelo UserApp para o Worker
 */
public class Request {
    public enum RequestType {
        SEARCH,      // Pesquisa de substrings
        GET_FILE,    // Obter conteúdo de ficheiro
        GET_STATISTICS  // Obter estatísticas
    }

    private RequestType type;
    private String requestId;  // ID único para correlacionar request/response
    private String replyTo;    // Nome da queue onde enviar a resposta
    private String replyExchange; // Exchange onde publicar a resposta
    
    // Para SEARCH
    private List<String> substrings;
    
    // Para GET_FILE
    private String filename;
    
    // Construtor vazio para Gson
    public Request() {}

    // Construtor para SEARCH
    public Request(RequestType type, String requestId, String replyTo, String replyExchange, List<String> substrings) {
        this.type = type;
        this.requestId = requestId;
        this.replyTo = replyTo;
        this.replyExchange = replyExchange;
        this.substrings = substrings;
    }

    // Construtor para GET_FILE
    public Request(RequestType type, String requestId, String replyTo, String replyExchange, String filename) {
        this.type = type;
        this.requestId = requestId;
        this.replyTo = replyTo;
        this.replyExchange = replyExchange;
        this.filename = filename;
    }

    // Construtor para GET_STATISTICS
    public Request(RequestType type, String requestId, String replyTo, String replyExchange) {
        this.type = type;
        this.requestId = requestId;
        this.replyTo = replyTo;
        this.replyExchange = replyExchange;
    }

    // Getters e Setters
    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getReplyExchange() {
        return replyExchange;
    }

    public void setReplyExchange(String replyExchange) {
        this.replyExchange = replyExchange;
    }

    public List<String> getSubstrings() {
        return substrings;
    }

    public void setSubstrings(List<String> substrings) {
        this.substrings = substrings;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}

