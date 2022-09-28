package SocketMessage;

public class UIMessage {


    private String communicativeAct;
    private String sender;

    private String interactionProtocol;

    private String conversation_id;

    private String reply_with;
    private String in_reply_to;
    private String message;

    private String loopbackcontent;

    public UIMessage(String communicativeAct, String sender, String message, String interactionProtocol, String conversation_id, String reply_with, String in_reply_to, String loopbackcontent) {
        this.communicativeAct = communicativeAct;
        this.sender = sender;
        this.interactionProtocol = interactionProtocol;
        this.conversation_id = conversation_id;
        this.reply_with = reply_with;
        this.in_reply_to = in_reply_to;
        this.message = message;
        this.loopbackcontent = loopbackcontent;

    }


    public UIMessage(String communicativeAct, String sender, String message, String interactionProtocol) {
        this.sender = sender;
        this.communicativeAct = communicativeAct;
        this.message = message;
        this.interactionProtocol = interactionProtocol;
    }


}
