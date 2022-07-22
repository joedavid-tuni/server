import SocketMessage.GeneralMessage;
import SocketMessage.VA2ACommunication;
import Utils.KnowledgeBase;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;



//}
class WSHumanClient extends WebSocketClient {
    private String name;
    public WSHumanClient(URI serverURI, String name) {
        super(serverURI);
        this.name =name;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        send("Hello, it is the Operator :)");
        System.out.println("[" + this.name + "WS] new connection opened");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("closed with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[" + this.name + "WS] received message: " + message);
    }

    @Override
    public void onMessage(ByteBuffer message) {
        System.out.println("received ByteBuffer");
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("an error occurred:" + ex);
    }
}

public class HumanAgent extends Agent {

    WebSocket UIconn;
    WebSocketClient WShuman;
    Gson gson = new Gson();
    KnowledgeBase kb = new KnowledgeBase("/home/robolab/Downloads/productCap.rdf","/home/robolab/Documents/DatasetOperator");

    public HumanAgent() throws IOException {
    }

    public void setup() {
        System.out.println("Hello from Human Jade Agent");
        System.out.println("My local name is " + getAID().getLocalName());
        System.out.println("My GUID is " + getAID().getName());
        System.out.println("");

        Object[] args = getArguments();
        UIconn = (WebSocket) args[0];  //if you need connection directly to the UI (untested)
        String url = (String) args[1];

        try {
            WShuman =  new WSHumanClient(new URI(url), getAID().getLocalName());
            WShuman.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


        setEnabledO2ACommunication(true, 0);

//        addBehaviour(new getUIWSObj()); //testing with robot currently

        addBehaviour(new CheckForProposals());
        addBehaviour(new getUIWSObj());
//        addBehaviour(new CheckForO2A());
//        addBehaviour(new CheckForMessages()); //commented out as this was interfereing with the interaction protocols


    }
    class getUIWSObj extends Behaviour {
        private boolean done = false;

        @Override
        public void action() {

            UIconn = (WebSocket) myAgent.getO2AObject();
            if (UIconn != null) {
//                UIconn = (WebSocket) myAgent.getO2AObject();

                System.out.println("[Operator getUIWSObj Beh] Got UI WS Object");
                UIconn.send("{\"message\" : \"[Robot Agent] UI Websocket connection established\"}");

                addBehaviour(new CheckForO2A());
                done = true;

            }
            block();
        }

        @Override
        public boolean done(){
            return done;
        }
    }


    class CheckForProposals extends CyclicBehaviour {
        // To handle all messages with request IP to forward to UI
        private MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if(msg!=null){
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + "Received Request Message from " + msg.getSender().getLocalName());
                VA2ACommunication _resMsg = new VA2ACommunication(msg.getSender().getLocalName(), myAgent.getAID().getLocalName() , //2nd Arg=> if the message is received by Operator, receiver = Operator
                        "Request from Robot", msg.getContent(),  ACLMessage.getPerformative(msg.getPerformative()), msg.getProtocol(),
                        msg.getConversationId(), msg.getReplyWith(), msg.getInReplyTo()); // AGAIN A CUSTOM MAP FOR CA?
                JsonObject obj = new JsonParser().parse(gson.toJson(_resMsg)).getAsJsonObject();
                GeneralMessage resMsg = new GeneralMessage("agent_communication",obj);
                String uiMsg= gson.toJson(resMsg);

                kb.insertACLMessage(msg);

                WShuman.send(uiMsg);
            }
            else {
                block();
            }

        }
    }

//    AchieveREInitiator WorkPlanRequestInitiator= new AchieveREInitiator(this, null){
//
////        @Override
////        protected Vector prepareRequests(ACLMessage request) {
////            request.
////            return super.prepareRequests(request);
////
////        }
//
//        protected void handleInform(ACLMessage message){
//            System.out.println("Workplan Information: ");
//            System.out.println(message.getContent());
//        }
//    };

//    class CheckForMessages extends CyclicBehaviour {
//        @Override
//        public void action() {
//
//            ACLMessage msg = myAgent.receive();
//            if (msg != null) {
//                System.out.println("[" + myAgent.getAID().getLocalName() + "] Message Received from " + msg.getSender().getLocalName() + ":  " + msg.getContent());
//
////                // TODO: Later transform the message into appropriate json objects
////                agent.send("[" + myAgent.getAID().getLocalName() + " ]  Sending that I received a message via Websockets to the webserver " + msg.getSender().getLocalName() + ":  " + msg.getContent());
//            } else {
//                block();
//            }
//        }
//    }

    // interaction protocol example: http://www.iro.umontreal.ca/~dift6802/jade/src/examples/protocols/FIPARequestInitiatorAgent.java
    // other examples: https://www.javatips.net/api/jade.proto.achievereinitiator
    class CheckForO2A extends CyclicBehaviour {
        // O2A is only put by UI, so this behaviour may thought to be dedicated to handle UI Requests
        @Override
        public void action() {
            System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + "Checking for O2A Message");
            GeneralMessage object = (GeneralMessage) myAgent.getO2AObject();

            Gson gson = new Gson();
            /// IMPLEMENT REQUEST INTERACTION PROTOCOL

            if (object != null) {

                System.out.println("");
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message type: " + object.getType());
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message value: " + object.getValue().toString());
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message receiver " + object.getReceiver());
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message context " + object.getContext());
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message payload " + object.getPayload());
//                System.out.println("");

                if (Objects.equals(object.getType(), "agent_communication")) {
                    VA2ACommunication a2aMsg = gson.fromJson(object.getValue().toString(), VA2ACommunication.class);
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " agent_communication message's CA: " + a2aMsg.getCommunicativeAct());
                    ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
                    requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));

                    if(Objects.equals(a2aMsg.getCommunicativeAct(), "REQUEST")){
                        System.out.println("Entered final block before sending agent message");
                        requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST); // USE A MAP THAT MAPS YOUR CA TO FIPA'S, THEN YOU CAN SET PROTOCOL AUTOMATICALLY
                        requestMsg.setContent(object.getValue().toString());

                        myAgent.addBehaviour(new AchieveREInitiator(myAgent, requestMsg) {
                            protected void handleAgree(ACLMessage message) {
                                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Request Collaboration Result: ");
                                System.out.println(message.getContent()); // transform to appropriate JSON object as you have on receive message function for the robot and you need it for multiple scenarios
                                System.out.println(" ");
                                WShuman.send(message.getContent());
                            }
                        });
                    }
                    else if(Objects.equals(a2aMsg.getCommunicativeAct(), "ACCEPT-PROPOSAL")){

                        ACLMessage requestMsg2 = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        requestMsg2.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                        requestMsg2.setProtocol(a2aMsg.getInteractionProtocol());
                        requestMsg2.setConversationId(a2aMsg.getConversation_id());
                        requestMsg2.setInReplyTo(a2aMsg.getIn_reply_to());
                        send(requestMsg2);

                        // Record the message
                        String msg = kb.insertACLMessage(requestMsg2);
                        kb.addConversationToContract(a2aMsg.getConversation_id());
                    }
                    else if(Objects.equals(a2aMsg.getCommunicativeAct(), "REJECT-PROPOSAL")){

                        ACLMessage requestMsg2 = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                        requestMsg2.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                        requestMsg2.setProtocol(a2aMsg.getInteractionProtocol());
                        requestMsg2.setConversationId(a2aMsg.getConversation_id());
                        requestMsg2.setInReplyTo(a2aMsg.getIn_reply_to());
                        send(requestMsg2);
                    }
                    else if (Objects.equals(a2aMsg.getContext(), "Something else")) {
                        System.out.println("Do Something");
                    }
                }
            } else {
                block();
            }
        }
    }
}