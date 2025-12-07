package userapp.messages;

import java.util.List;

/**
 * Classe que representa uma resposta enviada pelo Worker para o UserApp
 */
public class Response {
    public enum ResponseType {
        SEARCH_RESULT,      // Resultado de pesquisa
        FILE_CONTENT,       // Conteúdo de ficheiro
        STATISTICS          // Estatísticas
    }

    private ResponseType type;
    private String requestId;  // ID do pedido original para correlação
    
    // Para SEARCH_RESULT
    private List<String> filenames;
    
    // Para FILE_CONTENT
    private String filename;
    private String content;
    
    // Para STATISTICS
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    
    private boolean success;
    private String errorMessage;

    // Construtor vazio para Gson
    public Response() {}

    // Construtor para SEARCH_RESULT
    public Response(ResponseType type, String requestId, List<String> filenames, boolean success) {
        this.type = type;
        this.requestId = requestId;
        this.filenames = filenames;
        this.success = success;
    }

    // Construtor para FILE_CONTENT
    public Response(ResponseType type, String requestId, String filename, String content, boolean success) {
        this.type = type;
        this.requestId = requestId;
        this.filename = filename;
        this.content = content;
        this.success = success;
    }

    // Construtor para STATISTICS
    public Response(ResponseType type, String requestId, int totalRequests, int successfulRequests, int failedRequests, boolean success) {
        this.type = type;
        this.requestId = requestId;
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.success = success;
    }

    // Construtor para erro
    public Response(ResponseType type, String requestId, String errorMessage) {
        this.type = type;
        this.requestId = requestId;
        this.success = false;
        this.errorMessage = errorMessage;
    }

    // Getters e Setters
    public ResponseType getType() {
        return type;
    }

    public void setType(ResponseType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(int successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(int failedRequests) {
        this.failedRequests = failedRequests;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

