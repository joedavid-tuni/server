import SocketMessage.GeneralMessage;
import SocketMessage.VA2ACommunication;
import Utils.KnowledgeBase;
import Utils.MessageUtils;
import Utils.SPARQLUtils;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;
import jade.proto.ProposeInitiator;
import jdk.swing.interop.SwingInterOpUtils;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

enum State{IDLE, PERFORMING_TASK, CHECKING_CAPABILITY, COMMUNICATION, INIT}

public class RobotAgent extends Agent {

    WebSocket UIconn = null;
    Gson gson = new Gson();

    KnowledgeBase kb = new KnowledgeBase("/home/robolab/Downloads/productCap.rdf","/home/robolab/Documents/DatasetRobot");

    private final MessageUtils mu = new MessageUtils();

    public static State robotState = State.INIT;

    Behaviour intention  = new Intention();

    Queue<String> productionTaskQueue = new LinkedList<String>();
    Queue<String> processTaskQueue = new LinkedList<String>();
    Queue<String> desires = new LinkedList<String>();
    Queue<String> intentions = new LinkedList<String>();

    public RobotAgent() throws IOException {
    }


    public void setup() {
        // write your code here
        System.out.println("Hello from Robot Jade Agent");
        System.out.println("My local name is " + getAID().getLocalName());
        System.out.println("My GUID is " + getAID().getName());



        // Initialize Fuseki
//        kb.initFuseki();

        setEnabledO2ACommunication(true, 0);

        MessageTemplate mt = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST);
        this.addBehaviour( new AchieveREResponder(this, mt){
            protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {

                System.out.println("[ " + getAID().getLocalName() + " ] Received message belonging to Interaction Protocol"  );
                System.out.println("[ " + getAID().getLocalName() + " ] Content: " + request.getContent()  );
                VA2ACommunication reqMsg = gson.fromJson(request.getContent(), VA2ACommunication.class);
                System.out.println("[ " + getAID().getLocalName() + " ] Context: " + reqMsg.getContext() );
                if (Objects.equals(reqMsg.getContext(), "Request Collaborative Assembly")) {


                    if(true){ // TODO: check if the robot is not busy

                        // Prepare message to send
                        ACLMessage affirmative = request.createReply();
                        affirmative.setPerformative(ACLMessage.AGREE);
                        String payload = String.format("Request for collaboration for product workplan %s Received. Request Accepted", reqMsg.getPayload());
                        VA2ACommunication _resMsg = new VA2ACommunication(reqMsg.getReceiver(), reqMsg.getSender(), "Response Collaborative Assembly ", payload, "AGREE", request.getProtocol()); // AGAIN A CUSTOM MAP FOR CA?
                        JsonObject obj = new JsonParser().parse(gson.toJson(_resMsg)).getAsJsonObject();
                        GeneralMessage resMsg = new GeneralMessage("agent_communication",obj);
                        affirmative.setContent(gson.toJson(resMsg));

                        //Add ticker behaviour to start PN checking and traversal



//                        addBehaviour(traversePN);

                        return affirmative;
                    }
                    else{
                        ACLMessage negative = request.createReply();
                        negative.setPerformative(ACLMessage.REFUSE);
                        negative.setContent("Request for collaboration for product workplan "+ reqMsg.getPayload() +" Received. Request Refused As I am busy");
                        return negative;
                    }

                }
                else{
                    ACLMessage notUnderstood = request.createReply();
                    notUnderstood.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    notUnderstood.setContent("I have not understood your message");
                    return notUnderstood;
                }


            }
        });

//        addBehaviour(new CheckForMessages());
        addBehaviour(new getUIWSObj());
        addBehaviour(new Desire());
        addBehaviour(intention);


    }

    class getUIWSObj extends Behaviour {
        private boolean done = false;

        @Override
        public void action() {

            UIconn = (WebSocket) myAgent.getO2AObject();
            if (UIconn != null) {
//                UIconn = (WebSocket) myAgent.getO2AObject();

                System.out.println("[Robot getUIWSObj Beh] Got UI WS Object");
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

    class Intention extends Behaviour{
        // just directly do the inference as the inference will automatically work only on the currently active tasks
        //  as they are the ones that are achievable (updated achievable with min 1 cap) and  (non-conflicting)
        //  defined in contract.
        private boolean done = false;
        @Override
        public void action(){

            // get inferred model
            // Check for instances of Intentions to perform them

            ArrayList<String> intentions = null;
            try {
                intentions = kb.getIntentions();
            } catch (IOException | OWLOntologyStorageException e) {
                throw new RuntimeException(e);
            }

            System.out.println("My intentions are: " + intentions);
            block();
        }

        @Override
        public boolean done() {
            return done;
        }
    }


    class Desire extends Behaviour {
        private MessageTemplate messageTemplate;
        private String last = null;

        private void sendWSMessage(String id, String status){
            String  uiMessage = "{\"type\":\"tree-status-change\",\"key\":\""+id+"\" , \"state\":\""+status+"\"}";
            UIconn.send(uiMessage);

        }

        @Override
        public void action() {
            System.out.println("\n~~~~~~~[" + myAgent.getAID().getLocalName() + " Agent] "+robotState+"~~~~~~~~~~~~~~~~");
            switch (robotState) {
                case INIT:
                    robotState = State.IDLE;
                    block();
                    break;

                case IDLE:

                    // Check if any task
                    System.out.println("\n\nRobot in IDLE State");
                    if(last!=null) {
//                        System.out.println("Last: " + last);
                        String id2 = kb.getIDofTask(last);
                        sendWSMessage(id2, "completed");
                    }
                    ArrayList<String> currentTasks = kb.getCurrentProductionTasks(); // checks enabled transitions
                    if (currentTasks.size() > 0) {
                        System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] Current Tasks: " + currentTasks);

                        // populate queue
                        for (String currentTask : currentTasks) {
                            if (!productionTaskQueue.contains(currentTask)) {
                                productionTaskQueue.add(currentTask);
                            }
                        }

                        robotState = State.CHECKING_CAPABILITY;
                        block(8000);
//                        restart();
                    } else {
                        //handle no more tasks left
                        System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] No production Tasks");
                        block();
                    }
                    break;

                case PERFORMING_TASK:
                    String targetProdTask2 = productionTaskQueue.peek();
                    System.out.println("\n\nPerforming Task " + targetProdTask2);

                    // get ID of object from knowledge base
                    String id = kb.getIDofTask(targetProdTask2);

                    // Prepare and send JSON object
                    sendWSMessage(id,"performing");

                    last = productionTaskQueue.remove();
                    kb.updateTaskExecution(last); //fires a transition
                    robotState = State.IDLE;
                    block(3000);
//                    restart();
                    break;

                case CHECKING_CAPABILITY:
                    System.out.println("\n\nChecking Capability");
                    String targetProdTask1 = productionTaskQueue.peek();

                    //get Process Tasks for the target production Task

                    boolean isCapable = kb.checkCapabilityOfProductionTask(targetProdTask1);


                    //request permission from the operator
                    if(isCapable) {

                        ACLMessage requestMsg = new ACLMessage(ACLMessage.PROPOSE);
                        requestMsg.addReceiver(new AID("Operator", AID.ISLOCALNAME));
                        requestMsg.setSender(myAgent.getAID());
                        requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
                        requestMsg.setReplyWith("Propose " + targetProdTask1 + " Reply");
                        requestMsg.setContent(mu.getRequestTaskMessageAsString(targetProdTask1));
                        requestMsg.setConversationId("Propose_" + targetProdTask1);
                        myAgent.addBehaviour(new ProposeInitiator(myAgent, requestMsg) {
                            // handling it separately in the communication switch case block
//                                protected void handleAcceptProposal(ACLMessage message) {
//                                    System.out.println("HAHA GOTCHAAA!!");
//                                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Acceptance acknowledge for task" + targetProdTask1);
//                                    System.out.println(message.getContent()); // transform to appropriate JSON object as you have on receive message function for the robot and you need it for multiple scenarios
//                                    System.out.println(" ");
//                                    robotState = State.PERFORMING_TASK;
//                                    restart();
//                                }
                            //handle reject (remove the task from queue)
                        });

                        String msgName = kb.insertACLMessage(requestMsg);
//                        contract = kb.createContractForMessage(msgName);

                        String id1 = kb.getIDofTask(targetProdTask1);
                        sendWSMessage(id1,"in focus");
                        messageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("Propose_" + productionTaskQueue.peek()),
                                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
//                            messageTemplate = MessageTemplate.MatchConversationId("Propose " + targetProdTask1);
                        robotState = State.COMMUNICATION;
                        System.out.println("\n\n");

                    }
                    block();
//                    restart();
                    break;

                case COMMUNICATION:
                    // TODO: add custom mt here based on the message that you are sending in the checking capability
                    // TODO: GIT THIS, OTHER OPTION (1) INDIVIDUAL BEHAVIOURS FOR ALL TASKS?
                    // try to receive only those kinds of messages you are awaiting a reply for from the last communication with the operator

                    ACLMessage comm = myAgent.receive(messageTemplate);

                    if(comm!=null){
                        String msgName = kb.insertACLMessage(comm);

//                        ArrayList<String> msgs = kb.getMessageWithConvID(comm.getConversationId());
//                        kb.addMessagesToContract(msgs);
                        kb.addConversationToContract(comm.getConversationId());

                        robotState = State.PERFORMING_TASK;
                        String id1 = kb.getIDofTask(productionTaskQueue.peek());
                        System.out.println("Planned: " + id1);
                        sendWSMessage(id1,"planned");
                        intention.restart();
                    }

                    else {
                        MessageTemplate newMessageT = MessageTemplate.and(MessageTemplate.MatchConversationId("Propose " + productionTaskQueue.peek()),
                                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));
                        ACLMessage comm2 = myAgent.receive(newMessageT);

                        if(comm2!=null){
                            System.out.println("Acknowledging Reject");
                            System.out.println(comm2);
                            kb.updateTaskExecution(productionTaskQueue.remove());
                            robotState = State.IDLE;
                        }
                    }
                    block(3000);
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    };



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
    class CheckForO2A extends CyclicBehaviour {

        @Override
        public void action() {
            System.out.println("\n\n[" + myAgent.getAID().getLocalName() + " Agent] " + "Checking for O2A Message");
            O2A object = (O2A) myAgent.getO2AObject();


            /// IMPLEMENT REQUEST INTERACTION PROTOCOL

            if (object != null) {
                if (object.getType().equals("agent_communication")) {

                    ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
                    requestMsg.addReceiver(new AID(object.getReceiver(), AID.ISLOCALNAME));
                    requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    requestMsg.setContent(object.getContext());
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message type " + object.getType());
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message sender " + object.getSender());
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message receiver " + object.getReceiver());
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message context " + object.getContext());
                    System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message payload " + object.getPayload());


//                    addBehaviour(new AchieveREInitiator(myAgent, requestMsg) {
//                        protected void handleInform(ACLMessage message) {
//                            System.out.println("Workplan Information: ");
//                            System.out.println(message.getContent());
//                        }
//
//                        ;
//                    });
                }
            } else {
                block();
            }
        }
    }
}