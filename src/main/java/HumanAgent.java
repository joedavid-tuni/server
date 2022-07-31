import SocketMessage.GeneralMessage;
import SocketMessage.UIMessage;
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
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

import jade.proto.ProposeInitiator;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


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
    KnowledgeBase kb = new KnowledgeBase("/home/robolab/Downloads/productCapO.rdf","/home/robolab/Documents/DatasetOperator", 3001);

    Queue<String> checkedQueue = new LinkedList<String>();

    Queue<String> acceptedProposalsConvIds = new LinkedList<String>();
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

        kb.initFuseki();

        try {
            WShuman =  new WSHumanClient(new URI(url), getAID().getLocalName());
            WShuman.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


        setEnabledO2ACommunication(true, 0);

//        addBehaviour(new getUIWSObj()); //testing with robot currently

        addBehaviour(new HandleProposals());
        addBehaviour(new getUIWSObj());  // adds CheckForO2A() afterwards
//        addBehaviour(new HandleInform());
//        addBehaviour(new UpdatingIntentions()); // ?commented to debug
        addBehaviour(new Desire());
//        addBehaviour(new CheckForO2A());
//        addBehaviour(new CheckForMessages()); //commented out as this was interfereing with the interaction protocols


    }
    private void sendWSMessage(String id, String status){
        String  uiMessage = "{\"type\":\"tree-status-change\",\"key\":\""+id+"\" , \"state\":\""+status+"\"}";
        UIconn.send(uiMessage);
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

    class Desire extends CyclicBehaviour{
        //TODO: check all enabled transitions and update for Capability IF NOT DONE BEFORE


        @Override
        public void action() {
            // get all enabled transitions
            ArrayList<String> currentTasks = kb.getCurrentProductionTasksBySparql(); // checks enabled transitions

            if (currentTasks.size() > 0) {
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] Current Tasks: " + currentTasks);

                // populate queue and check capability
                for (String currentTask : currentTasks) {
                    // check if checked before if not check for capability and insert isAchievableWithCap
                    if (!checkedQueue.contains(currentTask)) {

                        ArrayList<String> inCapableTasks = kb.checkCapabilityOfProductionTask(currentTask);
                        System.out.println("[Operator] " + currentTask + " isCapable? : " + inCapableTasks.size()); // true if size=0
                        checkedQueue.add(currentTask);
                    }
                }
                block();
//                        restart();
            } else {
                //handle no more tasks left
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] No production Tasks");
                block();
            }
        }

    }
    class UpdatingIntentions extends CyclicBehaviour {
        // Keep updating intentions on receipt on any message (behaviour automatically unblocked and executed)
        // TODO: maintain and update a separate named model in the dataset, (is this accessible via Fuseki?)
        @Override
        public void action() {
            System.out.println("[Operator] Updating intentions .. ");
            try {
                kb.updateIntention();
            } catch (OWLOntologyCreationException | IOException | OWLOntologyStorageException e) {
                throw new RuntimeException(e);
            }
            block(2000);
        }
    }

    class HandleProposals extends CyclicBehaviour {
        // To handle all messages with request IP to forward to UI
        private MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if(msg!=null){
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + "Received Proposal Message from " + msg.getSender().getLocalName());
                VA2ACommunication _resMsg = new VA2ACommunication(msg.getSender().getLocalName(), myAgent.getAID().getLocalName() , //2nd Arg=> if the message is received by Operator, receiver = Operator
                        "Proposal from Robot", msg.getContent(),  ACLMessage.getPerformative(msg.getPerformative()), msg.getProtocol(),
                        msg.getConversationId(), msg.getReplyWith(), msg.getInReplyTo()); // AGAIN A CUSTOM MAP FOR CA?
                JsonObject obj = new JsonParser().parse(gson.toJson(_resMsg)).getAsJsonObject();
                GeneralMessage resMsg = new GeneralMessage("agent_communication",obj);
                String uiMsg= gson.toJson(resMsg);

                addBehaviour(new HandleInform(MessageTemplate.and(MessageTemplate.MatchConversationId(msg.getConversationId()),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM))));

                kb.insertACLMessage(msg);

                WShuman.send(uiMsg);
            }
            else {
                block();
            }

        }
    }

    class HandleInform extends CyclicBehaviour {
        public HandleInform(MessageTemplate mt) {
            this.mt = mt;
        }

        // To handle all messages with request IP to forward to UI
        private MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if(msg!=null){
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + "Received Inform Message from " + msg.getSender().getLocalName());
                String[] taskStringArr = msg.getConversationId().split("_");
                String task = taskStringArr[taskStringArr.length-1];
                System.out.println("For Task: " + task);
                String infMsg = kb.insertACLMessage(msg);
                kb.addInformMessageToContract(infMsg);
                kb.updateTaskExecution(task);
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

                    //  TODO: Refactor lot of redundant logic
                    if(Objects.equals(a2aMsg.getCommunicativeAct(), "REQUEST")||Objects.equals(a2aMsg.getCommunicativeAct(), "PROPOSE")||Objects.equals(a2aMsg.getCommunicativeAct(), "INFORM")){
                        if(Objects.equals(a2aMsg.getContext(),"Request Collaborative Assembly")) {// currently only expecting to be trigerred the Request Collaborative Assembly button
                            ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
                            requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                            requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST); // USE A MAP THAT MAPS YOUR CA TO FIPA'S, THEN YOU CAN SET PROTOCOL AUTOMATICALLY
                            requestMsg.setContent(object.getValue().toString());
                            requestMsg.setConversationId(a2aMsg.getContext());

                            myAgent.addBehaviour(new AchieveREInitiator(myAgent, requestMsg) {
                                protected void handleAgree(ACLMessage message) {
                                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Request Collaboration Result: ");
                                    System.out.println(message.getContent()); // transform to appropriate JSON object as you have on receive message function for the robot and you need it for multiple scenarios
                                    System.out.println(" ");
                                }
                            });
                        }
                        else if(Objects.equals(a2aMsg.getContext(),"Achieve Rational Effect")){

                            if (Objects.equals(a2aMsg.getCommunicativeAct(), "REQUEST") ){
                                ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
                                requestMsg.setSender(myAgent.getAID());
                                requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                                requestMsg.setProtocol(a2aMsg.getInteractionProtocol()); // USE A MAP THAT MAPS YOUR CA TO FIPA'S, THEN YOU CAN SET PROTOCOL AUTOMATICALLY
                                requestMsg.setConversationId(a2aMsg.getConversation_id());
                                kb.insertACLMessage(requestMsg);

                                myAgent.addBehaviour(new AchieveREInitiator(myAgent, requestMsg) {
                                    protected void handleAgree(ACLMessage message) {
                                        System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Achieve RE Result: ");
                                        System.out.println(message.getContent()); // transform to appropriate JSON object as you have on receive message function for the robot and you need it for multiple scenarios
                                        System.out.println(" ");

                                        UIMessage uiMessage = new UIMessage(ACLMessage.getPerformative(message.getPerformative()), message.getSender().getLocalName(), message.getContent(),message.getProtocol(), message.getConversationId(), message.getReplyWith(), message.getInReplyTo());
                                        GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                                        UIconn.send(gson.toJson(gm));

                                        kb.insertACLMessage(message);
                                        kb.addConversationToContract(message.getConversationId());

//                                        WShuman.send(uiMsg);
                                    }
                                });
                            } else if (Objects.equals(a2aMsg.getCommunicativeAct(), "PROPOSE")) {

                                //TODO: Before proposing to do check if you have the capability to do and update the KB,
                                // if not do not proceed and send appropriate message back for visual confirmation
                                boolean isCapable = false;

                                String[] taskStringArr = a2aMsg.getConversation_id().split("_");
                                String task = taskStringArr[taskStringArr.length-1];
                                System.out.println("[Operator] Checking Capability for Task: "+ task);

                                ArrayList<String> inCapableProcessTasks = kb.checkCapabilityOfProductionTask(task);

                                if(inCapableProcessTasks.size()==0){
                                    ACLMessage requestMsg = new ACLMessage(ACLMessage.PROPOSE);
                                    requestMsg.setSender(myAgent.getAID());
                                    requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                                    requestMsg.setProtocol(a2aMsg.getInteractionProtocol()); // USE A MAP THAT MAPS YOUR CA TO FIPA'S, THEN YOU CAN SET PROTOCOL AUTOMATICALLY
                                    requestMsg.setConversationId(a2aMsg.getConversation_id());

                                    kb.insertACLMessage(requestMsg);

                                    myAgent.addBehaviour(new ProposeInitiator(myAgent, requestMsg) {
                                        protected void handleAcceptProposal(ACLMessage message) {
                                            System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Achieve RE Result: ");
                                            System.out.println(message.getContent()); // transform to appropriate JSON object as you have on receive message function for the robot and you need it for multiple scenarios
                                            System.out.println(" ");

                                            UIMessage uiMessage = new UIMessage(ACLMessage.getPerformative(message.getPerformative()), message.getSender().getLocalName(), message.getContent(),message.getProtocol(), message.getConversationId(), message.getReplyWith(), message.getInReplyTo());
                                            GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                                            UIconn.send(gson.toJson(gm));

                                            kb.insertACLMessage(message);
                                            kb.addConversationToContract(message.getConversationId());
                                        }
                                    });
                                }
                                else{

                                    UIMessage uiMessage = new UIMessage("Notify", "Belief", "Capability to perform task " + task + "not found",
                                            "Notify", "None", "None", "None");
                                    GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                                    UIconn.send(gson.toJson(gm));
                                }

                            }

                            else if (Objects.equals(a2aMsg.getCommunicativeAct(), "INFORM")) {
                                ACLMessage requestMsg = new ACLMessage(ACLMessage.INFORM);
                                requestMsg.setSender(myAgent.getAID());
                                requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                                requestMsg.setProtocol(a2aMsg.getInteractionProtocol()); // USE A MAP THAT MAPS YOUR CA TO FIPA'S, THEN YOU CAN SET PROTOCOL AUTOMATICALLY
                                requestMsg.setConversationId(a2aMsg.getConversation_id());
                                send(requestMsg);
                                String infMsg = kb.insertACLMessage(requestMsg);
                                String[] taskStringArr = requestMsg.getConversationId().split("_");
                                String task = taskStringArr[taskStringArr.length-1];
                                System.out.println("Inform Task: "+ task);
                                kb.updateTaskExecution(task);
                                kb.addInformMessageToContract(infMsg);
                            }
                        }
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