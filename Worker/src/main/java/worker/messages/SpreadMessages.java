package worker.messages;

/**
 * Classe que representa a mensagem de comunicação entre a rede de máquinas ligadas pelo spread.
 */
public class SpreadMessages {

    public enum RequestType {
        NOT_LEADER,
        WORKER_RESPONSE,
        ELECTION,
        ELECTION_OK,
        COORDINATOR,
    }

    private RequestType type;
    private String requestId;
    private String replyTo;
    private String replyExchange;
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;

    public SpreadMessages() {
    }

    public RequestType getType() {
        return type;
    }

    public void setType(final RequestType type) {
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

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(final int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(final int successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(final int failedRequests) {
        this.failedRequests = failedRequests;
    }
}
