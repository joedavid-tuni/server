import SocketMessage.*;
import Utils.ConsoleColors;
import Utils.KnowledgeBase;
import Utils.ProcessTaskValidation;
import Utils.SPARQLUtils;
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
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;

import jade.proto.ProposeInitiator;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.shacl.ValidationReport;
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
        System.out.println(ConsoleColors.operator_format() + "[" + this.name + "WS] new connection opened" + ConsoleColors.RESET);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("closed with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(String message) {
        System.out.println(ConsoleColors.operator_format() +"[" + this.name + "WS] received message: " + message + ConsoleColors.RESET);
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
    KnowledgeBase kb = new KnowledgeBase("/home/robolab/Downloads/productCapOv3.rdf","/home/robolab/Documents/DatasetOperator", 3001, ConsoleColors.operator_kb());

    Queue<String> checkedQueue = new LinkedList<String>();

    Queue<String> acceptedProposalsConvIds = new LinkedList<String>();
    public HumanAgent() throws IOException {
    }

    public void setup() {
        System.out.println(ConsoleColors.operator_format() + "Hello from Human Jade Agent" + ConsoleColors.RESET);
        System.out.println(ConsoleColors.operator_format() + "My local name is " + getAID().getLocalName() + ConsoleColors.RESET);
        System.out.println(ConsoleColors.operator_format() + "My GUID is " + getAID().getName() + ConsoleColors.RESET);

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
        addBehaviour(new HandleCollaborations());
//        addBehaviour(new HandleInform());
//        addBehaviour(new UpdatingIntentions()); // ?commented to debug
//        addBehaviour(new Desire()); //commented for debugging
//        addBehaviour(new CheckForO2A());
//        addBehaviour(new CheckForMessages()); //commented out as this was interfering with the interaction protocols

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

                System.out.println(ConsoleColors.operator_format() +"[Operator getUIWSObj Beh] Got UI WS Object" + ConsoleColors.RESET);
                UIconn.send("{\"message\" : \"[Operator Agent] UI Websocket connection established\"}");

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

                        Map<String, ProcessTaskValidation> inCapableTasks = kb.checkCapabilityOfProductionTask(currentTask);
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
                System.out.println(ConsoleColors.operator_format() + "[" + myAgent.getAID().getLocalName() + " Agent] " + "Received Proposal Message from " + msg.getSender().getLocalName() + ConsoleColors.RESET);
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

    class HandleCollaborations extends CyclicBehaviour {
        // To handle all messages with CFP IP to forward to UI

        @Override
        public void action() {

            MessageTemplate mt1 = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),MessageTemplate.MatchPerformative(ACLMessage.CFP));
            ACLMessage msg1 = myAgent.receive(mt1);

            ArrayList<String> processTasks = new ArrayList<>();

            MessageTemplate mt2 = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            ACLMessage msg2 = myAgent.receive(mt2);

            ArrayList<ProcessTaskValidation> processTaskValidations = new ArrayList<>();
            if(msg1!=null){

                kb.insertACLMessage(msg1);

                String content = msg1.getContent(); // does not work with many process tasks, convert to array?
                System.out.println("Content: " + content);

//                List<String> ptList = new ArrayList<String>(Arrays.asList(content.split(",")));

                CharSequence sequence = "}, {"; // this sequence indicates multiple process tasks (as joined while sending from robot)

                if(content.contains(sequence)){
                    //more than one process tasks have failed
                    System.out.println("Damn more than one process tasks have failed");

                }
                else{
                    // only one process task has failed. Note atleast one process task will have failed otherwise the
                    // agent wouldnt initiate a call for proposal to begin with.
                    ProcessTaskValidation processTaskValidation = gson.fromJson(content,  ProcessTaskValidation.class);
                    processTaskValidations.add(processTaskValidation);
                    String processTask =processTaskValidation.getProcessTask();
                    ArrayList<String> failedActionCaps = processTaskValidation.getFailedActionCapClasses();
                    processTasks.add(processTask);




                    // validate with the process task


                }

                // fetch production Task from conv id

                String[] taskStringArr = msg1.getConversationId().split("_");
                String prodTask = taskStringArr[taskStringArr.length-1];

                // check own capability to perform the communicated failed actions of the process task

                //  a. Describe all failedactioncaps instances as one model

                Model requestedCapDescriptions = kb.describeResquestedCaps(processTaskValidations);
                //  b. validate the process task
                Writer writer = new StringWriter();
                requestedCapDescriptions.write(writer, "turtle");

                try {

                    // check if the described instances of capabilities  pass the validation for all requested incapable processes
                    boolean conforms = kb.validateRequestedProcessTasks(requestedCapDescriptions.getGraph(), processTaskValidations);

                    if(conforms){
                        System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " Validated the all requested  incapabilities are possesed. Proceeding to propose.");

//                        ACLMessage prop = msg1.createReply();
//                        prop.setContent(requestedCapDescriptions.toString());
//                        send(prop);

                        // TODO: Send to the left drawer, the process and actions that's the operators responsibility to make necessary changes

                        String uiMsg = "Collaboration Needed for process "+ StringUtils.join(processTasks, ", ")+" for Production Task: " + prodTask +". ";

                        // send message to UI (and open left drawer)

                        System.out.println("Debug Sending data: " + requestedCapDescriptions.toString());
                        UIMessage uiMessage = new UIMessage(ACLMessage.getPerformative(msg1.getPerformative()), msg1.getSender().getLocalName(), uiMsg, msg1.getProtocol(), msg1.getConversationId(), msg1.getReplyWith(), msg1.getInReplyTo(), writer.toString());
                        GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                        UIconn.send(gson.toJson(gm));



                    }
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }finally {

                }
                //  c. if conforms send the description back as message to the requesting agent.




            }
            else if(msg2!=null){
                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + "Acknowledging acceptance of collaboration proposal");
                kb.insertACLMessage(msg2);

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
                System.out.println(ConsoleColors.operator_format() + "[" + myAgent.getAID().getLocalName() + " Agent] " + "Received Inform Message from " + msg.getSender().getLocalName() + ConsoleColors.RESET);
                String[] taskStringArr = msg.getConversationId().split("_");
                String task = taskStringArr[taskStringArr.length-1];
                System.out.println(ConsoleColors.operator_format() +"For Task: " + task + ConsoleColors.RESET);
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
            System.out.println(ConsoleColors.operator_format() + "[" + myAgent.getAID().getLocalName() + " Agent] " + "Checking for O2A Message" + ConsoleColors.RESET);
            GeneralMessage object = (GeneralMessage) myAgent.getO2AObject();

            Gson gson = new Gson();
            /// IMPLEMENT REQUEST INTERACTION PROTOCOL

            if (object != null) {

                System.out.println("");
                System.out.println(ConsoleColors.operator_format() + "[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message type: " + object.getType() + ConsoleColors.RESET);
                System.out.println(ConsoleColors.operator_format() + "[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message value: " + object.getValue().toString() + ConsoleColors.RESET);
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message receiver " + object.getReceiver());
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message context " + object.getContext());
//                System.out.println("[" + myAgent.getAID().getLocalName() + " Agent] " + " O2A Message payload " + object.getPayload());
//                System.out.println("");

                if (Objects.equals(object.getType(), "agent_communication")) {
                    VA2ACommunication a2aMsg = gson.fromJson(object.getValue().toString(), VA2ACommunication.class);
//                    System.out.println(ConsoleColors.operator_format() +"[" + myAgent.getAID().getLocalName() + " Agent] " + " agent_communication message's CA: " + a2aMsg.getCommunicativeAct() + ConsoleColors.RESET);

                    if(Objects.equals(a2aMsg.getInteractionProtocol(),"fipa-contract-net")){ // this is only used for collaborations
                        if(Objects.equals(a2aMsg.getCommunicativeAct(),"PROPOSE")){ // this is a response to a call for proposal

                            System.out.println("[Debug] checking for desc" + a2aMsg.getLoopbackcontent());

                            ACLMessage requestMsg = new ACLMessage(ACLMessage.PROPOSE);
                            requestMsg.setSender(myAgent.getAID());
                            requestMsg.addReceiver(new AID(a2aMsg.getReceiver(), AID.ISLOCALNAME));
                            requestMsg.setProtocol(a2aMsg.getInteractionProtocol());
                            requestMsg.setConversationId(a2aMsg.getConversation_id());
                            requestMsg.setInReplyTo(a2aMsg.getIn_reply_to());
                            requestMsg.setContent(a2aMsg.getLoopbackcontent());

                            kb.insertACLMessage(requestMsg);
                            send(requestMsg);
                        }

                    }

                    //  TODO: Refactor lot of redundant logic
                    else if(Objects.equals(a2aMsg.getCommunicativeAct(), "REQUEST")||Objects.equals(a2aMsg.getCommunicativeAct(), "PROPOSE")||Objects.equals(a2aMsg.getCommunicativeAct(), "INFORM")){
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

                                        UIMessage uiMessage = new UIMessage(ACLMessage.getPerformative(message.getPerformative()), message.getSender().getLocalName(), message.getContent(),message.getProtocol(), message.getConversationId(), message.getReplyWith(), message.getInReplyTo(),"");
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

                                Map<String, ProcessTaskValidation> inCapableProcessTasks = kb.checkCapabilityOfProductionTask(task);

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

                                            UIMessage uiMessage = new UIMessage(ACLMessage.getPerformative(message.getPerformative()), message.getSender().getLocalName(), message.getContent(),message.getProtocol(), message.getConversationId(), message.getReplyWith(), message.getInReplyTo(),"");
                                            GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                                            UIconn.send(gson.toJson(gm));

                                            kb.insertACLMessage(message);
                                            kb.addConversationToContract(message.getConversationId());
                                        }
                                    });
                                }
                                else{

                                    UIMessage uiMessage = new UIMessage("Notify", "Belief", "Capability to perform task " + task + "not found",
                                            "Notify", "None", "None", "None","");
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
                else if (Objects.equals(object.getType(), "process_description")) {

                    ProcessDescription val = gson.fromJson(object.getValue().toString(), ProcessDescription.class);

                    System.out.println("extracted " +  val.getProcessName());

                    ArrayList<PrimitveTask> desc = kb.getProcessDescription(val.getProcessName());

                    String desc_json = gson.toJson(desc);

                    System.out.println(desc_json);



                    String  uiMessage = "{\"type\":\"show-process-description\",\"values\":"+desc_json+"}";
                    UIconn.send(uiMessage);

                }
            }  else  {
                block();
            }
        }
    }
}