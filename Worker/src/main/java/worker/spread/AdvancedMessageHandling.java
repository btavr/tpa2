package worker.spread;

import spread.*;
import worker.Worker;
import worker.messages.ResponseUserApp;
import worker.messages.SpreadMessages;
import worker.util.MessageSpreadSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdvancedMessageHandling implements AdvancedMessageListener {
    private final Map<String, AggregatedStatistics> aggregations = new HashMap<>();
    private final SpreadConnection connection;

    private MembershipInfo currentMembership;
    private final GroupMember groupMember;

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
            final SpreadMessages request = MessageSpreadSerializer
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

    private void handleElectionMessage(String sender, SpreadMessages request) {
        long senderPid = extractPid(sender);
        long myPid = extractPid(connection.getPrivateGroup().toString());

        if (myPid > senderPid) {
            sendElectionOk(sender, request);
            startElection();
        }
    }

    private void sendElectionOk(String target, SpreadMessages req) {
        SpreadMessages okMsg = new SpreadMessages();
        okMsg.setType(SpreadMessages.RequestType.ELECTION_OK);
        okMsg.setRequestId(req.getRequestId());

        try {
            groupMember.sendUnicastMessage(target, MessageSpreadSerializer.toBytes(okMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private void startElection() {
        if (currentMembership == null)
            return;

        System.out.println("Starting Election!");
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
        SpreadMessages electMsg = new SpreadMessages();
        electMsg.setType(SpreadMessages.RequestType.ELECTION);
        electMsg.setRequestId("ELECTION-" + System.currentTimeMillis());

        try {
            groupMember.sendUnicastMessage(target, MessageSpreadSerializer.toBytes(electMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private void announceLeader() {
        groupMember.setLeader(true);
        System.out.println("I am declaring myself Leader.");

        SpreadMessages coordMsg = new SpreadMessages();
        coordMsg.setType(SpreadMessages.RequestType.COORDINATOR);
        coordMsg.setRequestId("COORDINATOR-" + System.currentTimeMillis());

        try {
            groupMember.sendMulticastMessage(MessageSpreadSerializer.toBytes(coordMsg));
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private long extractPid(final String memberName) {
        try {
            String clean = memberName.startsWith("#") ? memberName.substring(1) : memberName;

            String[] parts = clean.split("-");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse PID from: " + memberName);
        }
        return -1;
    }

    private void handleElectionOkMessage(final String sender) {
        System.out.println("Received OK from " + sender + ". Waiting for coordinator.");
    }

    private void handleCoordinatorMessage(final String sender) {
        System.out.println("COORDINATOR is " + sender);
        groupMember.setLeader(false);

        if (sender.equals(connection.getPrivateGroup().toString())) {
            groupMember.setLeader(true);
            System.out.println("I am the Leader!");
        }
    }

    private void handleWorkerResponse(final SpreadMessages request, final String sender, final int membershipGroupSize) throws IOException {
        if (groupMember.isLeader()) {
            handleLeaderLogic(request, sender, membershipGroupSize);
        }
    }

    private void handleNotLeaderMessage(final SpreadMessages request, final String sender, final int membershipGroupSize)
            throws SpreadException, IOException {
        if (groupMember.isLeader()) {
            handleLeaderLogic(request, sender, membershipGroupSize);
        } else {
            handleWorkerLogic(request);
        }
    }

    private void handleLeaderLogic(SpreadMessages request, String sender, int groupSize) throws IOException {
        final String requestId = request.getRequestId();
        aggregations.putIfAbsent(requestId, new AggregatedStatistics());
        final AggregatedStatistics stats = aggregations.get(requestId);

        // Valida para não voltar a processar o nó já processado
        if (stats.responders.contains(sender)) {
            return;
        }

        // Stats do pedido
        stats.totalRequests += request.getTotalRequests();
        stats.successfulRequests += request.getSuccessfulRequests();
        stats.failedRequests += request.getFailedRequests();
        stats.responders.add(sender);

        System.out.println("Total pedidos processados: " + request.getTotalRequests() +
                " Número de pedidos com sucesso: " + request.getSuccessfulRequests() +
                " Número de pedidos com erro: " + request.getFailedRequests());

        // Stats do próprio nó
        String myName = connection.getPrivateGroup().toString();
        if (!stats.responders.contains(myName)) {
            stats.totalRequests += Worker.getTotalRequests();
            stats.successfulRequests += Worker.getSuccessfulRequests();
            stats.failedRequests += Worker.getFailedRequests();
            stats.responders.add(myName);

            System.out.println("Stats do próprio nó");
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

    private void handleWorkerLogic(final SpreadMessages request) throws SpreadException {
        if (request.getType() == SpreadMessages.RequestType.NOT_LEADER) {
            final SpreadMessages myStats = new SpreadMessages();
            myStats.setType(SpreadMessages.RequestType.WORKER_RESPONSE);
            myStats.setRequestId(request.getRequestId());
            myStats.setReplyTo(request.getReplyTo());
            myStats.setReplyExchange(request.getReplyExchange());
            myStats.setTotalRequests(Worker.getTotalRequests());
            myStats.setSuccessfulRequests(Worker.getSuccessfulRequests());
            myStats.setFailedRequests(Worker.getFailedRequests());

            groupMember.sendMulticastMessage(MessageSpreadSerializer.toBytes(myStats));
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

                startElection();
            }
        }
    }
}
