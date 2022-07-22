package SocketMessage;

public class VA2ACommunication {

    private String sender;

    private String receiver;

    private String context;

    private String communicativeAct;

    private String payload;

    private String interactionProtocol;

    private String conversation_id;

    private String reply_with;

    public String getConversation_id() {
        return conversation_id;
    }

    public String getReply_with() {
        return reply_with;
    }

    public String getIn_reply_to() {
        return in_reply_to;
    }

    private String in_reply_to;

    // for communication objects that receive response from UI with additional 3 params
    public VA2ACommunication(String sender, String receiver, String context, String payload, String communicativeAct, String interactionProtocol, String conversation_id, String reply_with, String in_reply_to) {
        // TODO: Optionally delegate task to existing constructor
        this.sender = sender;
        this.receiver = receiver;
        this.context = context;
        this.communicativeAct = communicativeAct;
        this.payload = payload;
        this.interactionProtocol = interactionProtocol;
        this.conversation_id = conversation_id;
        this.reply_with = reply_with;
        this.in_reply_to = in_reply_to;
    }

    public String getPayload() {
        return payload;
    }

    public String getCommunicativeAct() {
        return communicativeAct;
    }

    public String getInteractionProtocol() {
        return interactionProtocol;
    }

    public VA2ACommunication(String sender, String receiver, String context, String payload, String communicativeAct, String interactionProtocol) {
        this.sender = sender;
        this.receiver = receiver;
        this.context = context;
        this.payload = payload;
        this.communicativeAct = communicativeAct;
        this.interactionProtocol = interactionProtocol;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContext() {
        return context;
    }

}
