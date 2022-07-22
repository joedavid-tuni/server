public class O2A {
    String type;
    String sender;
    String receiver;
    String context;
    String payload;

    public O2A(String type, String sender, String receiver, String context, String payload) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.context = context;
        this.payload = payload;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public void setActivity(String activity) {
        this.context = context;
    }

    public void setPayload(String payload) { this.payload = payload;
    }


    public String getType() {
        return type;
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

    public String getPayload() { return payload;
    }




}
