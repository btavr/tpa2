package worker.spread;

import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class GroupMember {

    private SpreadConnection connection;
    private AdvancedMessageHandling advancedMsgHandling;

    private final Map<String, SpreadGroup> groupsBelonging = new HashMap<>();

    public GroupMember(String memberName, String daemonIP, int port) {
        try {
            this.connection = new SpreadConnection();
            connection.connect(InetAddress.getByName(daemonIP), port, memberName, false, true);
            advancedMsgHandling = new AdvancedMessageHandling(connection, this);
            connection.add(advancedMsgHandling);
        } catch (SpreadException e) {
            System.err.println("There was an error connecting to the daemon.");
            e.printStackTrace();
            System.exit(1);
        } catch (UnknownHostException e) {
            System.err.println("Can't find the daemon " + daemonIP);
            System.exit(1);
        }
    }

    public void addMemberToGroup(final String groupName) throws SpreadException {
        final SpreadGroup newGroup = new SpreadGroup();
        newGroup.join(connection, groupName);
        groupsBelonging.put(groupName, newGroup);
    }

    private boolean isLeader = false;

    public void setLeader(boolean leader) {
        isLeader = leader;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void sendMulticastMessage(final byte[] txtMessage) throws SpreadException {
        final SpreadMessage multicastMessage = new SpreadMessage();
        groupsBelonging.forEach((s, spreadGroup) -> multicastMessage.addGroup(s));

        multicastMessage.setSafe();
        multicastMessage.setData(txtMessage);
        connection.multicast(multicastMessage);
        System.out.println("Message sent to all the groups");
    }

    public void sendUnicastMessage(String targetMember, byte[] data) throws SpreadException {
        SpreadMessage unicastMessage = new SpreadMessage();
        unicastMessage.setSafe();
        unicastMessage.addGroup(targetMember);
        unicastMessage.setData(data);
        connection.multicast(unicastMessage);
        System.out.println("Unicast message sent to " + targetMember);
    }
}
