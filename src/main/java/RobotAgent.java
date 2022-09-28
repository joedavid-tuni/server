import SocketMessage.GeneralMessage;
import SocketMessage.VA2ACommunication;
import Utils.*;
import com.github.andrewoma.dexx.collection.ArrayLists;
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
import jade.proto.ContractNetInitiator;
import jade.proto.ProposeInitiator;
import jdk.swing.interop.SwingInterOpUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


import javax.mail.Message;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

enum State{IDLE, PERFORMING_TASK, CHECKING_CAPABILITY, COMMUNICATION, INIT}
enum INTState{INIT, FETCH, CHECK_CAPABILITY, PERFORM_TASK, COLLABORATE, COMMUNICATE}

public class RobotAgent extends Agent {

    WebSocket UIconn = null;
    Gson gson = new Gson();

    KnowledgeBase kb = new KnowledgeBase("/home/robolab/Downloads/productCapRv5.rdf","/home/robolab/Documents/DatasetRobot", 3002, ConsoleColors.robot_kb());

    private final MessageUtils mu = new MessageUtils();

    public static State robotState = State.INIT;

    public static INTState intentionState = INTState.INIT;

//    Behaviour intention  = new Intention();

    Queue<String> productionTaskQueue = new LinkedList<String>();
    Queue<String> intentionsQueue = new LinkedList<String>();
    Queue<String> pursuedIntentions = new LinkedList<String>();
    Queue<String> processTaskQueue = new LinkedList<String>();
    Queue<String> desires = new LinkedList<String>();


    public RobotAgent() throws IOException {
    }


    public void setup() {
        // write your code here
        System.out.println(ConsoleColors.robot_format() + "Hello from Robot Jade Agent" + ConsoleColors.RESET);
        System.out.println(ConsoleColors.robot_format() + "My local name is " + getAID().getLocalName() + ConsoleColors.RESET);
        System.out.println(ConsoleColors.robot_format() + "My GUID is " + getAID().getName() + ConsoleColors.RESET);



        // Initialize Fuseki
        kb.initFuseki();

        setEnabledO2ACommunication(true, 0);

//        MessageTemplate mt = AchieveREResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_REQUEST); // changed as request can come also in the form of task request
        MessageTemplate mt = MessageTemplate.MatchConversationId("Request Collaborative Assembly");
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
        addBehaviour(new Desire()); //commented to debug
        addBehaviour(new CheckForRequestsAndProposals());
//        addBehaviour(intention);


    }

    class getUIWSObj extends Behaviour {
        private boolean done = false;

        @Override
        public void action() {

            UIconn = (WebSocket) myAgent.getO2AObject();
            if (UIconn != null) {
//                UIconn = (WebSocket) myAgent.getO2AObject();

                System.out.println(ConsoleColors.robot_format() +"[Robot getUIWSObj Beh] Got UI WS Object" + ConsoleColors.RESET);
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
    class HandleInform extends CyclicBehaviour {
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
//                kb.addInformMessageToContract(infMsg);
//                kb.updateTaskExecution(task);
            }
            else {
                block();
            }

        }
    }

    class Intention extends Behaviour{ // TODO: create a multi state behaviour(as before) that traverses through all the process tasks and initiates appropriate messages with the Operator if ncessary
        // just directly do the inference as the inference will automatically work only on the currently active tasks
        //  as they are the ones that are achievable (updated achievable with min 1 cap) and  (non-conflicting)
        //  defined in contract after needed inter-agent communication.
        private boolean done = false;
        public String productionTask;
        public Queue<String> processTaskQueue = new LinkedList<String>();

        MessageTemplate messageTemplate;

        private boolean joint;

        ArrayList<TaskAndReport> incapableTasks;
        ArrayList<String> noActionForTaskTasks;

        public Intention(String productionTask, boolean joint) {
            this.productionTask = productionTask;
            this.joint = joint;
        }


        @Override
        public void action(){
            System.out.println();
            System.out.println(ConsoleColors.robot_heading_whtbck() + "~~~~~~~[" + myAgent.getAID().getLocalName() + " Agent Behaviour Intention ("+productionTask+")]  "+intentionState +"~~~~~~~" + ConsoleColors.RESET);
            // get inferred model
            // Check for instances of Intentions to perform them
            switch (intentionState){
                case INIT:

                    //Check if valid intention here.

                    ArrayList<String> intentions = null;
                    try {
                        intentions = kb.getIntentions();
                        System.out.println(ConsoleColors.robot_format() + "Computed Intentions: " + intentions + ConsoleColors.RESET);
                        if(intentions.contains(this.productionTask)){
                            intentionState = INTState.FETCH;
//                            done=true;
                        }
                        else{
                            System.out.println("Not an Intention");
                        }
                    } catch (IOException | OWLOntologyStorageException | OWLOntologyCreationException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case FETCH:
                    // fetch current process task

                    System.out.println(ConsoleColors.robot_format() + "Fetching Process Tasks " + ConsoleColors.RESET);
                    ArrayList<String> processTasks = kb.getActiveProcesses(productionTask);
                    System.out.println(ConsoleColors.robot_format() + "Process Tasks: " + processTasks +ConsoleColors.RESET);

                    if(processTasks.size() > 0){

                        for(String processTask : processTasks){
                            if (!processTaskQueue.contains(processTask)) {
                                processTaskQueue.add(processTask);
                            }

                        }
                        intentionState = INTState.CHECK_CAPABILITY;
                    } else{
                        System.out.println("No active processes");
                        done=true;
                    }


                    break;

                case CHECK_CAPABILITY:

                    // get the process

                    String process = processTaskQueue.peek();
                    // an ArrayList even though one element to be compatible to check length in the case there are none
                    System.out.println(ConsoleColors.robot_format() + "Checking Capability to carry out process" + ConsoleColors.RESET);
                    incapableTasks = kb.checkCapabilityOfProcess(process);
                    try {
                        System.out.println(ConsoleColors.robot_format() + "Checking Actions of Process: " + process + ConsoleColors.RESET);
                        noActionForTaskTasks = kb.checkForActions(process);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }



                    System.out.println(ConsoleColors.robot_format() + "Deciding between performing task alone or collaborate" + ConsoleColors.RESET);
                    if(incapableTasks.size()==0){
                        System.out.println(ConsoleColors.robot_format() + "Decided Perform" + ConsoleColors.RESET);
                        intentionState = INTState.PERFORM_TASK;
                    }
                    else {
                        System.out.println(ConsoleColors.robot_format() + "Decided Collaborate" + ConsoleColors.RESET);
                        intentionState = INTState.COLLABORATE;
                    }
                    break;

                case PERFORM_TASK:

                    //temp


                    try {
                        kb.writeToFile("planDebug_"+processTaskQueue.peek() +".rdf");
                        kb.updateNamedGraph();
//                        kb.debug();

                    } catch (OWLOntologyCreationException | IOException e) {
                        throw new RuntimeException(e);
                    }

                    System.out.println(ConsoleColors.robot_format() + "Performing task: " + processTaskQueue.peek() + ConsoleColors.RESET);

                    // todo: if all actions of the plan are executed

                    boolean allActionsExecuted = false;
                    try {
                        allActionsExecuted = kb.areActionsExecuted(processTaskQueue.peek());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }


                    if(allActionsExecuted){
                        kb.updateTaskExecution(processTaskQueue.peek());
                        processTaskQueue.remove();
                        intentionState = INTState.FETCH;
                    }

                    // get the ActivePlan (the right one)

                    // check if all actions in the plan exists for all tasks requirement in shapes

                    //execute them in the right order and modality

                    break;
                case COLLABORATE:
                    // get the caps that failed the validation

                    System.out.println("Incapable Tasks " + incapableTasks);
                    System.out.println("No Action Tasks " + noActionForTaskTasks);

                    Model validationReportModel = incapableTasks.get(0).getReport().getModel();

                    RDFDataMgr.write(System.out,validationReportModel, Lang.TTL);

                    // get the primitive tasks to be executed in order and store them in array list.
                        // get the SHACL graph
                        // query the graph

                    // pop each one and check

                    // Modality, if independent and capable and has action > PERFORM

                    // Modality, if synchronous and all actions capable and has action >PERFORM
                    // Modality, if synchronous and not all actions capable and has action > COMMUNICATE

                    // Modality, if sequential and all actions capable and has action > PERFORM
                    // Modality, if sequential and not all actions capable and has action > COMMUNICATE


                    // if it is either incapable or noaction task and inform operator to be done if so based on the modality
                        // wait for inform messages from the operator that indicates operator has done his/her part

                    ACLMessage propMsg = new ACLMessage(ACLMessage.PROPOSE);
                    propMsg.addReceiver(new AID("Operator", AID.ISLOCALNAME));
                    propMsg.setSender(myAgent.getAID());
                    propMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
                    propMsg.setReplyWith("Collab_Process_" + processTaskQueue.peek() + "_Reply");
                    propMsg.setConversationId("Collab_Process_" + processTaskQueue.peek());
                    propMsg.setContent("Its time to do an agreed Collaboration, You should see the instructions on you left. Hit accept proposal when ready");

                    send(propMsg);

                    //TODO: throw open left drawer with required information  (type b left drawer)

                    messageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("Collab_Process_" + processTaskQueue.peek()),
                            MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));

                    intentionState = INTState.COMMUNICATE;

                    break;

                case COMMUNICATE:


                    ACLMessage comm = myAgent.receive(messageTemplate);

                    if(comm !=null){
                        System.out.println("Received accept proposal to do collaboration process immediately");

                        intentionState = INTState.PERFORM_TASK;
                    }




            }

            block(500);
        }

        @Override
        public boolean done() {
            return done;
        }
    }


    class Desire extends Behaviour {
        private MessageTemplate messageTemplate;
        private String last = null;
        Map<String, ProcessTaskValidation> inCapableProcessTasks;

        private Intention currentIntention;

        private void sendWSMessage(String id, String status){
            String  uiMessage = "{\"type\":\"tree-status-change\",\"key\":\""+id+"\" , \"state\":\""+status+"\"}";
            UIconn.send(uiMessage);

        }

        @Override
        public void action() {
            System.out.println("");
            System.out.println(ConsoleColors.robot_heading() + "~~~~~~~[" + myAgent.getAID().getLocalName() + " Agent Behaviour Desire ("+productionTaskQueue.peek()+")] "+robotState+"~~~~~~~" + ConsoleColors.RESET);
            switch (robotState) {
                case INIT:
                    robotState = State.IDLE;
                    block();
                    break;

                case IDLE:

                    // Check if any task
                    if(last!=null) {
//                        System.out.println("Last: " + last);
                        String id2 = kb.getIDofTask(last);
                        sendWSMessage(id2, "completed");
                    }
                    ArrayList<String> currentTasks = kb.getCurrentProductionTasksByShapes(); // checks enabled transitions
                    if (currentTasks.size() > 0) {
                        System.out.println(ConsoleColors.robot_format() + "[" + myAgent.getAID().getLocalName() + " Agent] Current Tasks: " + currentTasks + ConsoleColors.RESET);

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
                        try {
                            kb.writeToFile("planDebug.rdf");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        block();
                    }
                    break;

                case PERFORMING_TASK:

                    System.out.println(ConsoleColors.robot_format() + "[" + myAgent.getAID().getLocalName() + " Agent] Checking if current intention is complete.." +ConsoleColors.RESET);

                    if(currentIntention.done()){ // 31.07 You have decided to stick with the solution of waiting until each intention is
                        // done and to process productionTasks one-by-one because of the following reasons. (1) It can get overwhelming for
                        // the operator to answer 'proposals' all the time and not allow him to carry out tasks. (2) It can be distracting
                        // to answer questions while a task is being done by the robot. (3) Conversations for a collab task may be going on
                        // in parallel with the robot asking about new desires/proposals. It can be confusiong for the operator to maintain
                        // multiple parallel conversations about different things.
//                   get ID of object from knowledge base
                        System.out.println(ConsoleColors.robot_format()  + "Yes, intention is executed and complete, proceeding to inform operator" + ConsoleColors.RESET);
                        String id = kb.getIDofTask(currentIntention.productionTask);

                        // Prepare and send JSON object
                        sendWSMessage(id,"performing");
                        last = productionTaskQueue.remove();
                        kb.updateTaskExecution(last); //fires a transition

                        ACLMessage requestMsg = new ACLMessage(ACLMessage.INFORM);
                        requestMsg.addReceiver(new AID("Operator", AID.ISLOCALNAME));
                        requestMsg.setSender(myAgent.getAID());
                        requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
                        requestMsg.setContent("Task "  + currentIntention.productionTask + " performed successfully");
                        requestMsg.setConversationId("Propose_" + currentIntention.productionTask);
                        String infMsg = kb.insertACLMessage(requestMsg);
                        send(requestMsg);
                        kb.addInformMessageToContract(infMsg);

                        robotState = State.IDLE;

                    }

                    else{
                        System.out.println(ConsoleColors.robot_format() + "Not Done" + ConsoleColors.RESET);
                    }

                    // get ID of object from knowledge base
//                    String id = kb.getIDofTask(targetProdTask2);
//
//                    // Prepare and send JSON object
//                    sendWSMessage(id,"performing");
//
//                    last = productionTaskQueue.remove();
//
//                    // Succesfully Complete
//
//                    if(true) {
//                        // Informing task is done, end of protocol
//                        ACLMessage requestMsg = new ACLMessage(ACLMessage.INFORM);
//                        requestMsg.addReceiver(new AID("Operator", AID.ISLOCALNAME));
//                        requestMsg.setSender(myAgent.getAID());
//                        requestMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
//                        requestMsg.setContent("Task "  + targetProdTask2 + " performed successfully");
//                        requestMsg.setConversationId("Propose_" + targetProdTask2);
//                        String infMsg = kb.insertACLMessage(requestMsg);
//                        send(requestMsg);
//                        kb.addInformMessageToContract(infMsg);
//                    }
//                    // Failed to perform
//
//
//
//                    kb.updateTaskExecution(last); //fires a transition
//                    robotState = State.IDLE;
                    block(500);
//                    restart();
                    break;

                case CHECKING_CAPABILITY:
                    String targetProdTask1 = productionTaskQueue.peek();

                    //get Process Tasks for the target production Task

                    inCapableProcessTasks = kb.checkCapabilityOfProductionTask(targetProdTask1);
                    System.out.println(ConsoleColors.robot_format() + "Number of Incapable Process Tasks: " + inCapableProcessTasks.size() + ConsoleColors.RESET);
                    ArrayList<String> temp = new ArrayList<>();

                    for (ProcessTaskValidation ptv:inCapableProcessTasks.values()){
                        temp.add(gson.toJson(ptv));
                    }

                    //request permission from the operator
                    if(inCapableProcessTasks.size() == 0) {

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
                        String id = kb.getIDofTask(productionTaskQueue.peek());
                        String message = "{\"type\":\"show-process-plans\",\"values\":{\"id\":\""+id+"\",\"task\":\""+productionTaskQueue.peek()+"\"}}";
                        UIconn.send(message);

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

                    else {

                        String id = kb.getIDofTask(productionTaskQueue.peek());
                        String message = "{\"type\":\"show-process-plans\",\"values\":{\"id\":\""+id+"\",\"task\":\""+productionTaskQueue.peek()+"\"}}";
                        UIconn.send(message);

                        // initiate collaborative activities and set different message template for communication block
                        System.out.println("InCapable Tasks are : " + inCapableProcessTasks);

                        // CFP
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        cfp.addReceiver(new AID("Operator", AID.ISLOCALNAME));
                        cfp.setSender(myAgent.getAID());
                        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                        cfp.setReplyWith("Collab " + targetProdTask1 + " Reply");
                        cfp.setContent(StringUtils.join(temp, ", "));
//                        cfp.setContent(new Gson().toJson(inCapableProcessTasks));
                        cfp.setConversationId("Collab_" + targetProdTask1);

                        // TODO: throw the type a leftdrawer open to indicate which proces tasks of the prodution tasks

                        ArrayList<String> idsOfIncProcesses = new ArrayList<>();
                        for(ProcessTaskValidation ptv:inCapableProcessTasks.values()){
//                            ProcessTaskValidation ptv= gson.fromJson(task, ProcessTaskValidation.class);
                            String incProcess = ptv.getProcessTask();
                            String idOfIncProcess = kb.getIDofTask(incProcess);
                            idsOfIncProcesses.add(idOfIncProcess);
                        }



//                        String message2 = "{\"type\":\"highlight-incapable-tasks\",\"values\":{\"id\":\""+id+"\",\"task\":\""+productionTaskQueue.peek()+"\", \"incProcesses\":"+gson.toJson(idsOfIncProcesses)+"}}";
//                        UIconn.send(message2);

                        myAgent.addBehaviour(new ContractNetInitiator(myAgent, cfp));
                        String msgName = kb.insertACLMessage(cfp);

                        String id1 = kb.getIDofTask(targetProdTask1);
                        sendWSMessage(id1,"in focus");

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

                    // CASE 1 : For non-collaborative task, expecting accept-proposal
                    ArrayList<String> intentions = new ArrayList<>();
                    ACLMessage comm = myAgent.receive(messageTemplate);

                    // CASE 2: For collaborative task, CFP (RECEIVING PROPOSE OF OPERATOR/OTHER AGENT)

                    MessageTemplate mt2 =  MessageTemplate.and(MessageTemplate.MatchConversationId("Collab_" + productionTaskQueue.peek()),
                            MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                    ACLMessage comm2 = myAgent.receive(mt2);

                    // CASE 3 : For non-collaborative task, expecting reject-proposal
                    MessageTemplate newMessageT = MessageTemplate.and(MessageTemplate.MatchConversationId("Propose " + productionTaskQueue.peek()),
                            MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));
                    ACLMessage comm3 = myAgent.receive(newMessageT);


                    if(comm!=null){
                        String msgName = kb.insertACLMessage(comm);

//                        ArrayList<String> msgs = kb.getMessageWithConvID(comm.getConversationId());
//                        kb.addMessagesToContract(msgs);
                        kb.addConversationToContract(comm.getConversationId());

                        try {
                            System.out.println(ConsoleColors.robot_format() + "Step 1: Fetch Robot Intentions " + ConsoleColors.RESET);
                            intentions = kb.getIntentions();
                        } catch (IOException | OWLOntologyStorageException | OWLOntologyCreationException e) {
                            System.out.println(ConsoleColors.robot_format() +"Error computing intentions" + ConsoleColors.RESET);
                            throw new RuntimeException(e);
                        }finally {
                            System.out.println(ConsoleColors.robot_format() + "Check inferred beliefs " + ConsoleColors.RESET);
                            if(intentions.contains(productionTaskQueue.peek())){
                                try {
                                    kb.generatePlans(productionTaskQueue.peek());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                String id1 = kb.getIDofTask(productionTaskQueue.peek());
                                sendWSMessage(id1,"planned");

                                System.out.println(ConsoleColors.robot_format() + "New Intention found: " + productionTaskQueue.peek() + ConsoleColors.RESET);
                                System.out.println(ConsoleColors.robot_format() + "Spawning Jade Intention Behaviour for " + productionTaskQueue.peek() + ConsoleColors.RESET);

                                currentIntention = new Intention(productionTaskQueue.peek(), false);
                                currentIntention.setBehaviourName(productionTaskQueue.peek() + " Behaviour");

                                myAgent.addBehaviour(currentIntention);

                                robotState = State.PERFORMING_TASK;
                            }
                            else{
                                System.out.println("The production task in question is not an intention");
                            }
                        }
//                        addBehaviour( new Intention()); //commented to debug
                    }
                    else if(comm2!=null){
                        System.out.println("Acknowledging Receipt of Collaboration Proposal");
                        System.out.println(comm2.getContent());
                        String msgName = kb.insertACLMessage(comm2);
                        ArrayList<String> operatorCapableClasses = new ArrayList<>();

                        // check if the proposed is acceptable, and if yes then..

                        // TODO: (LATER) check if the capabilities description sent passes successful validation

                        kb.updateWithAchievableAxiom(productionTaskQueue.peek()); //todo(later): also update with Capabilities provided by human

                        // TODO: Generate Plans

                        // 1: Extract the classes of actions by the operator
                        String desc = comm2.getContent();

                        try {
                            operatorCapableClasses = kb.extractCapabilityClassOfOperator(desc);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            kb.generateCollaborativePlans(productionTaskQueue.peek(), operatorCapableClasses, inCapableProcessTasks);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        ACLMessage reply = comm2.createReply();
                        reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        String[] taskStringArr = comm2.getConversationId().split("_");
                        String task = taskStringArr[taskStringArr.length-1];
                        reply.setContent("Acknowledging Proposal to collaborate on " + task + ". Commencing Collaboration. Pay attention to your tasks, role and corresponding collaborative modalities ..");
                        send(reply);

                        kb.insertACLMessage(reply);

                        kb.addConversationToContract(comm2.getConversationId());

                        currentIntention = new Intention(productionTaskQueue.peek(), true);
                        currentIntention.setBehaviourName(productionTaskQueue.peek() + " Behaviour");

                        myAgent.addBehaviour(currentIntention);

                        robotState = State.PERFORMING_TASK;

                    }

                    else if(comm3!=null){
                            System.out.println("Acknowledging Reject");
                            System.out.println(comm2);
                            kb.updateTaskExecution(productionTaskQueue.remove());
                            robotState = State.IDLE;
                        }

                    block(500);
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    ;

    class CheckForRequestsAndProposals extends CyclicBehaviour {
        private MessageTemplate messageTemplate;
        @Override
        public void action() {

            messageTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE),MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
            ACLMessage msg = myAgent.receive(messageTemplate);
            if (msg != null) {
                System.out.println("[" + myAgent.getAID().getLocalName() + "] Message Received from " + msg.getSender().getLocalName() + ":  " + msg.getContent());

//                // TODO: Check for capability to do task
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                reply.setContent("Proposal Accepted");
                send(reply);
//                agent.send("[" + myAgent.getAID().getLocalName() + " ]  Sending that I received a message via Websockets to the webserver " + msg.getSender().getLocalName() + ":  " + msg.getContent());
            }
            messageTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            msg = myAgent.receive(messageTemplate);
            if (msg != null) {
                System.out.println("[" + myAgent.getAID().getLocalName() + "] Message Received from " + msg.getSender().getLocalName() + ":  " + msg.getContent());
                // TODO: Check for capability to do task AND ALSO IF PREREQUISITES ARE SATISFIED
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.AGREE);
                reply.setContent("Agreed to Request");
                send(reply);

                reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("Agreed to Request");
                send(reply);
//
//                agent.send("[" + myAgent.getAID().getLocalName() + " ]  Sending that I received a message via Websockets to the webserver " + msg.getSender().getLocalName() + ":  " + msg.getContent());
            }
            else {
                block();
            }
        }
    }
    class CheckForO2A extends CyclicBehaviour {

        @Override
        public void action() {
            System.out.println(ConsoleColors.robot_format() +"\n\n[" + myAgent.getAID().getLocalName() + " Agent] " + "Checking for O2A Message" + ConsoleColors.RESET);
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