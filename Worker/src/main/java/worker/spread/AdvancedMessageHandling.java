package worker.spread;

import spread.*;
import worker.Worker;
import worker.messages.RequestStatistics;
import worker.messages.ResponseUserApp;
import worker.util.MessageStatisticsSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdvancedMessageHandling implements AdvancedMessageListener {
    // Map to store aggregations: RequestId -> Statistics Data
    private final Map<String, AggregatedStatistics> aggregations = new HashMap<>();
    private final SpreadConnection connection;

    private MembershipInfo currentMembership;
    private final GroupMember groupMember;

    // Helper class to hold aggregation state
    private static class AggregatedStatistics {
        int totalRequests = 0;
        int successfulRequests = 0;
        int failedRequests = 0;
        Set<String> responders = new HashSet<>();
    }

    public AdvancedMessageHandling(SpreadConnection connection, GroupMember groupMember) {
        this.connection = connection;
        this.groupMember = groupMember;
    }

    @Override
    public void regularMessageReceived(final SpreadMessage spreadMessage) {
        try {
            final RequestStatistics request = MessageStatisticsSerializer
                    .requestStatisticsFromBytes(spreadMessage.getData());

            if (currentMembership == null || request == null)
                return;

            final int membershipGroupSize = currentMembership.getMembers() != null ?
                    currentMembership.getMembers().length : 0;
            final String sender = spreadMessage.getSender().toString();
            switch (request.getType()) {
                case ELECTION:
                    handleElectionMessage(sender, request);
                    break;
                case ELECTION_OK:
                    handleElectionOkMessage(sender);
                    break;
                case COORDINATOR:
                    handleCoordinatorMessage(sender);
                    break;
                case WORKER_RESPONSE:
                    handleWorkerResponse(request, sender, membershipGroupSize);
                    break;
                case NOT_LEADER:
                    handleNotLeaderMessage(request, sender, membershipGroupSize);
                    break;
                default:
                    System.out.println("Unknown message type: " + request.getType());
            }

        } catch (Exception e) {
            System.err.println("Error handling spread message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleElectionMessage(String sender, RequestStatistics request) {
        long senderPid = extractPid(sender);
        long myPid = extractPid(connection.getPrivateGroup().toString());

        if (myPid > senderPid) {
            // I am bigger, send OK and start my own election if not active
            sendElectionOk(sender, request);
            startElection();
        }
    }

    private void sendElectionOk(String target, RequestStatistics req) {
        RequestStatistics okMsg = new RequestStatistics();
        okMsg.setType(RequestStatistics.RequestType.ELECTION_OK);
        okMsg.setRequestId(req.getRequestId()); // Keep context if needed

        try {
            groupMember.sendUnicastMessage(target, MessageStatisticsSerializer.toBytes(okMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private void startElection() {
        if (currentMembership == null)
            return;

        System.out.println("Starting Election...");
        SpreadGroup[] members = currentMembership.getMembers();
        long myPid = extractPid(connection.getPrivateGroup().toString());
        boolean isBiggest = true;

        for (SpreadGroup member : members) {
            long memberPid = extractPid(member.toString());
            if (memberPid > myPid) {
                isBiggest = false;
                sendElectionAndWait(member.toString());
            }
        }

        if (isBiggest) {
            announceLeader();
        }
    }

    private void sendElectionAndWait(String target) {
        RequestStatistics electMsg = new RequestStatistics();
        electMsg.setType(RequestStatistics.RequestType.ELECTION);
        electMsg.setRequestId("ELECTION-" + System.currentTimeMillis());

        try {
            groupMember.sendUnicastMessage(target, MessageStatisticsSerializer.toBytes(electMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private void announceLeader() {
        groupMember.setLeader(true);
        System.out.println("I am declaring myself Leader.");

        RequestStatistics coordMsg = new RequestStatistics();
        coordMsg.setType(RequestStatistics.RequestType.COORDINATOR);
        coordMsg.setRequestId("COORD-" + System.currentTimeMillis());

        try {
            groupMember.sendMulticastMessage(MessageStatisticsSerializer.toBytes(coordMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private long extractPid(String memberName) {
        try {
            // Format: #Worker-<PID>-<UUID>#hostname
            // Split by '-'
            // Worker name set as: Check Worker.java: WORKER_STRING.concat(pid + "-" + ...);
            // Example: Worker-12345-uuid
            // Spread might decorate it: #Worker-12345-uuid#localhost

            // Remove Spread's # if present at start
            String clean = memberName.startsWith("#") ? memberName.substring(1) : memberName;

            String[] parts = clean.split("-");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse PID from: " + memberName);
        }
        return -1; // Fallback
    }

    private void handleElectionOkMessage(String sender) {
        System.out.println("Received OK from " + sender + ". Waiting for coordinator.");
        // We defer to the bigger node.
    }

    private void handleCoordinatorMessage(String sender) {
        System.out.println("COORDINATOR is " + sender);
        groupMember.setLeader(false);
        // If I was the one announcing, I am the leader, but the message comes back to
        // me too usually.
        if (sender.equals(connection.getPrivateGroup().toString())) {
            groupMember.setLeader(true);
            System.out.println("I am the Leader!");
        }
    }

    private void handleWorkerResponse(final RequestStatistics request, final String sender, final int membershipGroupSize) throws IOException {
        if (groupMember.isLeader()) {
            handleLeaderLogic(request, sender, membershipGroupSize);
        }
    }

    // Adjusted regularMessageReceived structure implies identifying sender earlier.
    // Let's refactor slightly to keep access to 'sender' in helper methods or pass
    // it.
    private void handleNotLeaderMessage(final RequestStatistics request, final String sender, final int membershipGroupSize)
            throws SpreadException, IOException {
        // Initial request from a worker (or external)

        // If I am leader, start aggregation
        if (groupMember.isLeader()) {
            handleLeaderLogic(request, sender, membershipGroupSize);
        } else {
            handleWorkerLogic(request);
        }
    }

    private void handleLeaderLogic(RequestStatistics request, String sender, int groupSize) throws IOException {
        final String requestId = request.getRequestId();
        aggregations.putIfAbsent(requestId, new AggregatedStatistics());
        final AggregatedStatistics stats = aggregations.get(requestId);

        // Avoid double counting same sender
        if (stats.responders.contains(sender)) {
            return;
        }

        // Add stats
        stats.totalRequests += request.getTotalRequests();
        stats.successfulRequests += request.getSuccessfulRequests();
        stats.failedRequests += request.getFailedRequests();
        stats.responders.add(sender);

        System.out.println("Total pedidos processados: " + request.getTotalRequests() +
                " Número de pedidos com sucesso: " + request.getSuccessfulRequests() +
                " Número de pedidos com erro: " + request.getFailedRequests());

        // Add own stats
        String myName = connection.getPrivateGroup().toString();
        if (!stats.responders.contains(myName)) {
            stats.totalRequests += Worker.getTotalRequests();
            stats.successfulRequests += Worker.getSuccessfulRequests();
            stats.failedRequests += Worker.getFailedRequests();
            stats.responders.add(myName);

            System.out.println("Stats do próprio pedido");
            System.out.println("Total pedidos processados: " + Worker.getTotalRequests() +
                    " Número de pedidos com sucesso: " + Worker.getSuccessfulRequests() +
                    " Número de pedidos com erro: " + Worker.getFailedRequests());
        }


        System.out.println("Leader Aggregation: " + stats.responders.size() + "/" + groupSize);

        // Validar se todos os nós responderam
        if (stats.responders.size() >= groupSize) {
            System.out.println("Processo de recolha de estatísticas terminado.");
            ResponseUserApp response = new ResponseUserApp();
            response.setType(ResponseUserApp.ResponseType.STATISTICS);
            response.setRequestId(requestId);
            response.setSuccess(true);
            response.setTotalRequests(stats.totalRequests);
            response.setSuccessfulRequests(stats.successfulRequests);
            response.setFailedRequests(stats.failedRequests);

            Worker.sendLeaderResponse(response, request.getReplyTo(), request.getReplyExchange());
            aggregations.remove(requestId);
        }
    }

    private void handleWorkerLogic(final RequestStatistics request) throws SpreadException {
        if (request.getType() == RequestStatistics.RequestType.NOT_LEADER) {
            // Send my stats
            final RequestStatistics myStats = new RequestStatistics();
            myStats.setType(RequestStatistics.RequestType.WORKER_RESPONSE);
            myStats.setRequestId(request.getRequestId());
            myStats.setReplyTo(request.getReplyTo());
            myStats.setReplyExchange(request.getReplyExchange());
            myStats.setTotalRequests(Worker.getTotalRequests());
            myStats.setSuccessfulRequests(Worker.getSuccessfulRequests());
            myStats.setFailedRequests(Worker.getFailedRequests());

            groupMember.sendMulticastMessage(MessageStatisticsSerializer.toBytes(myStats));
        }
    }

    @Override
    public void membershipMessageReceived(final SpreadMessage spreadMessage) {
        final MembershipInfo info = spreadMessage.getMembershipInfo();
        this.currentMembership = info;

        System.out.println("MemberShip ThreadID:" + Thread.currentThread().getId());
        if (info.isSelfLeave()) {
            System.out.println("Left group:" + info.getGroup().toString());
        } else {
            if (info.getMembers() != null) {
                SpreadGroup[] members = info.getMembers();
                System.out.println("members of belonging group:" + info.getGroup().toString());
                for (int i = 0; i < members.length; ++i) {
                    System.out.print(members[i] + ":");
                }
                System.out.println();

                // Start Election on Membership Change
                startElection();
            }
        }
    }
}
