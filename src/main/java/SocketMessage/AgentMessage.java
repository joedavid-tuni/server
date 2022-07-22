package SocketMessage;

public class AgentMessage {
    public String getType() {
        return type;
    }

    public String getSub_type() {
        return sub_type;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getActivity() {
        return activity;
    }

    private String type;
    private String sub_type;
    private String sender;
    private String receiver;
    private String activity;

    public AgentMessage(String type, String sub_type, String sender, String receiver, String activity) {
        this.type = type;
        this.sub_type = sub_type;
        this.sender = sender;
        this.receiver = receiver;
        this.activity = activity;
    }
}
