package worker.messages;

import java.util.List;

/**
 * Classe que representa um pedido recebido pelo Worker do UserApp
 * (mesma estrutura que em UserApp para compatibilidade)
 */
public class RequestUserApp {
    public enum RequestType {
        SEARCH,      // Pesquisa de substrings
        GET_FILE,    // Obter conteúdo de ficheiro
        GET_STATISTICS  // Obter estatísticas
    }

    private RequestType type;
    private String requestId;
    private String replyTo;
    private String replyExchange;
    
    // Para SEARCH
    private List<String> substrings;
    
    // Para GET_FILE
    private String filename;

    public RequestUserApp() {}

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

