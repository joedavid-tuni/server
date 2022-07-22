package SocketMessage;

import com.google.gson.JsonObject;

public class GeneralMessage {

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonObject getValue() {
        return value;
    }

    public void setValue(JsonObject value) {
        this.value = value;
    }

    private String type;
    private JsonObject value;

    public GeneralMessage(String type, JsonObject value) {
        this.type = type;
        this.value = value;
    }
}
