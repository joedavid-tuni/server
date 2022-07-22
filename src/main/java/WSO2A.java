import org.java_websocket.WebSocket;

public class WSO2A {

    private String type;

    private WebSocket webSocket;

    public String getType() {
        return type;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public WSO2A(String type, WebSocket webSocket) {
        this.type = type;
        this.webSocket = webSocket;
    }
}
