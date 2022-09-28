package Utils;

import SocketMessage.PrimitveTask;
import com.github.owlcs.ontapi.OntManagers;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.OntologyManager;
import com.google.gson.Gson;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.apache.commons.io.FileUtils;
//import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.commons.io.IOUtils;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.Graph;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
//import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.update.UpdateExecution;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.util.PrintUtil;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.util.InferredAxiomGenerator;
import org.semanticweb.owlapi.util.InferredClassAssertionAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;


import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


//todo: check for redundant code blocks throughout
public class KnowledgeBase {

    private Dataset knowledgeBase;
    private String consoleFontFormat = new String();

    private int i = 0;
    private final SPARQLUtils su = new SPARQLUtils();

    private Integer portFuseki;

    Gson gson = new Gson();

//    private Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
//    private Reasoner reasoner = PelletReasonerFactory.theInstance().create();
//    private Reasoner reasoner2 = PelletReasonerFactory.theInstance().create();

    FusekiServer fusekiServer;

    public KnowledgeBase(String gPath, String dBaseDir, Integer portFuseki, String consoleFontFormat) throws IOException {
//        String dBaseDir = "/home/robolab/Documents/Dataset";
        File dir = new File(dBaseDir);
        FileUtils.cleanDirectory(dir);

        this.knowledgeBase = TDBFactory.createDataset(dBaseDir);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        ontModel.read(gPath, "RDF/XML");


//        reasoner.bind(ontModel.getGraph());
//
        this.knowledgeBase.getDefaultModel().add(ontModel);
        this.knowledgeBase.addNamedModel("https://joedavid91.github.io/ontologies/camo-named-graph/intention", ontModel);
        this.portFuseki = portFuseki;
        this.consoleFontFormat = consoleFontFormat;

    }

    public Model getModel() {
        return this.knowledgeBase.getDefaultModel();
    }

    public void writeToFile(String fileName) throws IOException {
        // Mainly for Debugging purposes
        Model model = this.knowledgeBase.getDefaultModel();
//        Model model2 = this.knowledgeBase.getNamedModel("https://joedavid91.github.io/ontologies/camo-named-graph/intention");
        FileWriter out = new FileWriter(fileName);
        knowledgeBase.begin(TxnType.WRITE);

        try {
            model.write(out, "RDF/XML-ABBREV");
        } finally {
            try {
                out.close();
                knowledgeBase.commit();
                knowledgeBase.end();
            } catch (IOException closeException) {
                // ignore
            }
        }

    }

    public void initFuseki() {
        fusekiServer = FusekiServer.create()
                .enableCors(true)
                .port(portFuseki)
                .add("/ds", knowledgeBase, true)
                .build();
        System.out.println(consoleFontFormat + "Starting Fuseki on port " + portFuseki + ConsoleColors.RESET);
        fusekiServer.start();
    }

    private void stopFusek() {
        System.out.println("Stopping Fuseki");
        fusekiServer.stop();
    }

    public ArrayList<String> getCurrentProductionTasksBySparql() {
        ArrayList<String> currentProductionTasks = new ArrayList<String>();
        knowledgeBase.begin(ReadWrite.READ);

        try {
            String qs1 = su.getPrefixes("camo") + """
                    SELECT ?task1 ?id
                        WHERE  {
                                    ?task1 a camo:ProductionTask.
                                    ?task1 camo:UID ?id.
                                    ?state camo:includesActivities ?task1.
                                    ?state camo:hasToken true.
                        MINUS {
                                    ?state1 camo:hasToken true.
                                    ?state2 camo:hasToken false.
                                    ?state1 camo:includesActivities ?task1.
                                    ?state2 camo:includesActivities ?task2.
                                    filter ((?task1=?task2))
                                }
                        }
                    """;

            try (QueryExecution qExec = QueryExecution.create(qs1, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution sol = rs.nextSolution();
                    String prodTask = sol.getResource("?task1").getLocalName();
                    String id = sol.getLiteral("?id").getString();
                    if (prodTask != null) currentProductionTasks.add(prodTask);
                }
            }
            knowledgeBase.end();
            return currentProductionTasks;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ArrayList<String> getCurrentProductionTasksByShapes() {

        //returns all enabled transitions
        ArrayList<String> currentProductionTasks = new ArrayList<String>();
        knowledgeBase.begin(ReadWrite.READ);

        try {
            // get all transitions with atleast one marked place
            //TODO: ALTERNATIVELY REPLACE WITH QUERY THAT RETURNS TRANSITIONS WITH ALL INPUT PLACES MARKED
            String qs1 = su.getPrefixes("camo", "xsd") + " SELECT DISTINCT ?prodTasks WHERE {?prodState a camo:ProductState. " +
                    "?prodState camo:hasToken \"true\"^^xsd:boolean. ?prodState camo:includesActivities ?prodTasks. } LIMIT 10";
            try (QueryExecution qExec = QueryExecution.create(qs1, knowledgeBase)) {

                ResultSet rs = qExec.execSelect();
                System.out.println(consoleFontFormat + "Checking Enabled Transitions (Production Task)" + ConsoleColors.RESET);
                while (rs.hasNext()) {
                    QuerySolution sol = rs.nextSolution();
                    String prodTask = sol.getResource("?prodTasks").getLocalName();
                    System.out.println(consoleFontFormat + " Checking production task" + prodTask + ConsoleColors.RESET);

                    //Describe places of the transtiion
                    String descQuery = su.getPrefixes("camo") + "DESCRIBE ?prodState camo:hasToken camo:includesActivities WHERE {?prodState camo:includesActivities camo:" + prodTask + "}";
                    QueryExecution qExec2 = QueryExecution.create(descQuery, knowledgeBase);
                    Model stateModel = qExec2.execDescribe();

                    //load shape
                    Graph shapeGraph = RDFDataMgr.loadGraph("/home/robolab/Documents/shapes/isTransitionEnabled.ttl", Lang.TTL);
                    Shapes shape = Shapes.parse(shapeGraph);

                    ValidationReport report = ShaclValidator.get().validate(shape, stateModel.getGraph());

                    ShLib.printReport(report);

                    if (report.conforms()) {
                        currentProductionTasks.add(prodTask);
                    }


                    //validate

//                    System.out.println(currentTasks);
                }

//                System.out.println(currentTasks.size());
            }
        } finally {
            knowledgeBase.end();
        }

        return currentProductionTasks;

    }


    public String getIDofTask(String task) {


        knowledgeBase.begin(ReadWrite.READ);
        String id;
        try {
            //1. Get the Shape defining the capability required.
            String queryString = su.getPrefixes("camo") + " SELECT ?id WHERE { camo:" +
                    task + " camo:UID ?id.}";
            try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                QuerySolution soln = rs.nextSolution();
                id = soln.getLiteral("id").getString();
//                System.out.println(shapeDir);
            }
        } finally {
            knowledgeBase.end();
        }


        return id;
    }

    public ArrayList<String> getMessagesWithConvID(String conversation_id) {
//        if (!knowledgeBase.isInTransaction())  knowledgeBase.begin(ReadWrite.READ);
        ArrayList<String> ids = new ArrayList<String>();

//        try {
        //1. Get the Shape defining the capability required.
        String query = su.getPrefixes("camo", "xsd") + " SELECT ?msgID WHERE { " +
                "?msgID a camo:ACLMessage. " +
                "?msgID camo:conversation_id \"" + conversation_id + "\"^^xsd:string.}";
        // assumes only one message exists
        Query _query = QueryFactory.create(query);
        String msg;
        try (QueryExecution qExec = QueryExecution.create(_query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                msg = soln.getResource("?msgID").getURI(); // for some reason you cant get the local name directly
                String[] msgs = msg.split("#");
                msg = msgs[msgs.length - 1];
                ids.add(msg);
            }
//                System.out.println(shapeDir);
        }
//        }
//        finally {
//            knowledgeBase.end();
//        }

        return ids;

    }

    public String insertACLMessage(ACLMessage msg) {


        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        String formattedDate = myDateObj.format(myFormatObj);


        String sender = msg.getSender().getLocalName();
        StringBuilder receiver = new StringBuilder("");
        jade.util.leap.Iterator it = msg.getAllReceiver();
        while (it.hasNext()) {
            AID r = (AID) it.next();
            receiver.append(r.getLocalName());
        }

        String content = msg.getContent();

        if (content != null) {
            content = content.replace("\n", " ");
        }

        String in_reply_to = msg.getInReplyTo();
        String reply_with = msg.getReplyWith();
        String protocol = msg.getProtocol();
        String performative = ACLMessage.getPerformative(msg.getPerformative()); //not verified
        String conversation_id = msg.getConversationId();

        String query = su.getPrefixes("camo", "xsd") + "INSERT DATA{camo:" + formattedDate + " a camo:ACLMessage. " +
                "camo:" + formattedDate + " camo:sender \"" + sender + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:receiver \"" + receiver + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:content \'" + content + "\'^^xsd:string. " + // needed as writer formats with newline characted
                "camo:" + formattedDate + " camo:in_reply_to \"" + in_reply_to + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:reply_with \"" + reply_with + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:protocol \"" + protocol + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:performative \"" + performative + "\"^^xsd:string. " +
                "camo:" + formattedDate + " camo:conversation_id \"" + conversation_id + "\"^^xsd:string. " +
                //add date time if necessary later
                "}";

        knowledgeBase.begin(TxnType.WRITE);

        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
//        UpdateProcessor proc = UpdateExecutionFactory.create(uReq,graphStore)

        knowledgeBase.commit();
        knowledgeBase.end();

        return formattedDate;

    }

    public String createContractForMessage(String msgName) {
        String name = String.valueOf((int) (Math.random() * 10000000));
        String query = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + name + " a DUL:Contract. " +
                "camo:" + name + " DUL:hasPart camo:" + msgName + "}";

        knowledgeBase.begin(TxnType.WRITE);

        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

        knowledgeBase.commit();
        knowledgeBase.end();
        return name;
    }

    //    public void addMessageToContract(String msgName, String contract) {
//        String query = su.getPrefixes("camo") + " INSERT DATA {camo:"+contract+" camo:hasPart camo:"+msgName+"}";
//
//        knowledgeBase.begin(TxnType.WRITE);
//
//        UpdateRequest uReq = UpdateFactory.create(query);
//        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
//
//        knowledgeBase.commit();
//        knowledgeBase.end();
//    }
    public void addInformMessageToContract(String infMsg) {

        knowledgeBase.begin(TxnType.WRITE);
        System.out.println(consoleFontFormat + "Adding Inform Message to existing contract: " + infMsg + ConsoleColors.RESET);

        // get the contract where the convid of msg belongs to and add the msg as part of the contract

        String query = su.getPrefixes("camo", "DUL") + " INSERT {?con DUL:hasPart camo:" + infMsg + "} WHERE {camo:" + infMsg + " camo:conversation_id ?convID. " +
                "?msg camo:conversation_id ?convID. ?con DUL:hasPart ?msg.}";
        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

        knowledgeBase.commit();
        knowledgeBase.end();
    }

    public void addMessagesToContract(ArrayList<String> msgNames) {

        knowledgeBase.begin(TxnType.WRITE);

        // Generate the Contract Name Randomly
        String conName = String.valueOf((int) (Math.random() * 10000000));

        // Get which task the message is about using the conversation ID

        String qs = su.getPrefixes("camo") + "SELECT ?convID WHERE {}";

        // Insert that the Task is defined in the contract (Can be combined with below)


        //  Add messages to Contract
        String query = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + conName + " a DUL:Contract.}";
        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();


        for (String msg : msgNames) {
            query = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + conName + " DUL:hasPart camo:" + msg + "}";
            uReq = UpdateFactory.create(query);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
        }

        knowledgeBase.commit();
        knowledgeBase.end();
    }

    public String addConversationToContract(String convID) {

        // Takes all messages with a conversation id and adds them to a contract

        knowledgeBase.begin(TxnType.WRITE);

        String conName = String.valueOf((int) (Math.random() * 10000000));

        // Get which task the message is about using the conversation ID

        String[] taskStringArr = convID.split("_");
        String task = taskStringArr[taskStringArr.length - 1];

        // get all messages with the convID
        ArrayList<String> msgs = getMessagesWithConvID(convID);

        // Make a Contract
        String query = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + conName + " a DUL:Contract.}";
        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

        //Add Messages to contract
        for (String msg : msgs) {
            query = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + conName + " DUL:hasPart camo:" + msg + "}";
            uReq = UpdateFactory.create(query);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
        }

        // Add task defined in contract
        String query1 = su.getPrefixes("camo", "DUL") + " INSERT DATA {camo:" + task + " DUL:isTaskDefinedIn camo:" + conName + ".}";
        UpdateRequest uReq1 = UpdateFactory.create(query1);
        UpdateExecution.dataset(knowledgeBase).update(uReq1).execute();


        knowledgeBase.commit();
        knowledgeBase.end();

        // Insert that the Task is defined in the contract (Can be combined with below)
        return convID;
    }


    public ArrayList<TaskAndReport> checkCapabilityOfProcess(String processTask) {
        System.out.println(consoleFontFormat + "\ta.Checking Capability of Process: " + processTask + ConsoleColors.RESET);
        String shapeDir = "";
        ArrayList<TaskAndReport> incapableTasks = new ArrayList<>();
        knowledgeBase.begin(TxnType.READ);
        try {
            //1. Get the Shape path defining the capability required.
            System.out.println(consoleFontFormat + "\ta. fetching description of: " + processTask + ConsoleColors.RESET);
            String queryString = su.getPrefixes("camo") + " SELECT ?shape WHERE { camo:" +
                    processTask + " camo:requiresCapability ?cap. " +
                    "?cap camo:hasShape ?shape.}";
            try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    shapeDir = soln.getLiteral("shape").getString();
                }
                System.out.println(shapeDir);
            }

            String _path = Paths.get(new URI(shapeDir)).toString();
            System.out.println(consoleFontFormat + "\t\t i. path found to be: " + _path + ConsoleColors.RESET);

            // 2. Check if agent has the capability required
            //    2a. Get what class of Capability the shape is targeting
            System.out.println(consoleFontFormat + "\t\t ii. parsing description" + ConsoleColors.RESET);
            Graph shapesGraph = RDFDataMgr.loadGraph(_path);
            Shapes shapes = Shapes.parse(shapesGraph);

            //todo: query the actionName here in the parsed graph

            Model shapesModel = RDFDataMgr.loadModel(_path);
            System.out.println(consoleFontFormat + "\tb. querying description to find target capabilities" + ConsoleColors.RESET);
            String getTargetClassQuery = su.getPrefixes("sh", "cm") + " SELECT ?targetClass WHERE " +
                    "{?shape a sh:NodeShape. ?shape sh:targetClass ?targetClass.}";
            Query query = QueryFactory.create(getTargetClassQuery);
            QueryExecution queryExecution = QueryExecutionFactory.create(query, shapesModel);
            ResultSet resultSet = queryExecution.execSelect();

            ArrayList<String> capClassList = new ArrayList<>();
            while (resultSet.hasNext()) {
                QuerySolution soln = resultSet.nextSolution();
                capClassList.add(soln.getResource("targetClass").getLocalName());
            }
            System.out.println(consoleFontFormat + "\t\ti. target capabilities found to be " + capClassList + ConsoleColors.RESET);

            //     2b. Describe the instances of the Target Capability (if any-important) to be run the validation against
            ValidationReport report = null;

            System.out.println(consoleFontFormat + "\tc. Describe own instance of target capabilites" + ConsoleColors.RESET);
            if (capClassList.size() > 0) {
                System.out.println(consoleFontFormat + "\t\t i. generating dynamic describe query" + ConsoleColors.RESET);
                String describeCapQuery = su.getPrefixes("camo", "cm") + su.describeCapablitiesQuery(capClassList);
                System.out.println(ConsoleColors.YELLOW + describeCapQuery);

                System.out.println(consoleFontFormat + "\t d. validate description of own capabilities with task reqruiements" + ConsoleColors.RESET);
                Query query1 = QueryFactory.create(describeCapQuery);
                queryExecution = QueryExecution.create(query1, knowledgeBase);

                Model agentCapabilityModel = queryExecution.execDescribe();
                Graph agentCapabilityGraph = agentCapabilityModel.getGraph();

                //3. Validate the Agent Capability Graph against the shapes the defines the capability required by the said task.
                report = ShaclValidator.get().validate(shapes, agentCapabilityGraph);
                RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);

                if (!report.conforms()) {
                    incapableTasks.add(new TaskAndReport(processTask, shapesModel, report));
                }
                System.out.println("\n");
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        knowledgeBase.end();
        return incapableTasks;
    }

    public void updateWithAchievableAxiom(String productionTask) {
        // TODO: checking capabilities again is redundant, just blindly update with the axiom for passed  production task

        String shapeDir = "";
        ArrayList<String> processTasks = getProcessTasksForProductionTask(productionTask);
        knowledgeBase.begin(TxnType.WRITE);

        for (String processTask : processTasks) {
//            System.out.println("[Update Achievable Axiom] for process task: " + processTask);
            try {
                //1. Get the Shape path defining the capability required.
                String queryString = su.getPrefixes("camo") + " SELECT ?shape WHERE { camo:" +
                        processTask + " camo:requiresCapability ?cap. " +
                        "?cap camo:hasShape ?shape.}";
                try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                    ResultSet rs = qExec.execSelect();
                    while (rs.hasNext()) {
                        QuerySolution soln = rs.nextSolution();
                        shapeDir = soln.getLiteral("shape").getString();
                    }
                    System.out.println(shapeDir);
                }
                String _path = Paths.get(new URI(shapeDir)).toString();

                //    2 Get what class of Capability the shape is targeting

                Graph shapesGraph = RDFDataMgr.loadGraph(_path);
                Shapes shapes = Shapes.parse(shapesGraph);

                Model shapesModel = RDFDataMgr.loadModel(_path);
                String getTargetClassQuery = su.getPrefixes("sh", "cm") + " SELECT ?targetClass WHERE " +
                        "{?shape a sh:NodeShape. ?shape sh:targetClass ?targetClass.}";
                Query query = QueryFactory.create(getTargetClassQuery);
                QueryExecution queryExecution = QueryExecutionFactory.create(query, shapesModel);
                ResultSet resultSet = queryExecution.execSelect();

                ArrayList<String> capClassList = new ArrayList<>();
                while (resultSet.hasNext()) {
                    QuerySolution soln = resultSet.nextSolution();
                    capClassList.add(soln.getResource("targetClass").getLocalName());
//                    System.out.println("Required Capabilities: " + capClassList);
                }

//                System.out.println("CapClassList: " + capClassList);

                for (String capClass : capClassList) {
//                    System.out.println("CapClas: " + capClass);
                    assert capClass != null;
                    String qs = su.getPrefixes("cm", "camo") + " SELECT ?capInst WHERE {?capInst a cm:" + capClass + ".}";

                    Query _query = QueryFactory.create(qs);
                    QueryExecution _queryExecution = QueryExecutionFactory.create(_query, knowledgeBase);
                    ResultSet _resultSet = _queryExecution.execSelect();

                    Resource capInst = null; //we assume that there is only one instance of each class of capability

                    ArrayList<String> capInstList = new ArrayList<>();

                    while (_resultSet.hasNext()) {
                        QuerySolution soln = _resultSet.nextSolution();
                        capInst = soln.getResource("capInst");
                        capInstList.add(capInst.getLocalName());
//                        System.out.println("Required Capability Inst: " + capInst.getLocalName());


                    }

                    for (String cinst : capInstList) {

                        // update
                        String _qs = su.getPrefixes("camo", "cm") + " INSERT DATA {camo:" + productionTask + " camo:isAchievableWithCap camo:" + cinst + ".}";
                        UpdateRequest uReq = UpdateFactory.create(_qs);
                        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

                    }

                }


            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        knowledgeBase.commit();

        knowledgeBase.end();

    }

    public Map<String, ProcessTaskValidation> checkCapabilityOfProductionTask(String productionTask) {
        Gson gson = new Gson();
        Map<String, ProcessTaskValidation> map = new HashMap<>();

        System.out.println(consoleFontFormat + "[Checking Capability of Production Task:" + productionTask + " (X Steps)]");
        ArrayList<String> processTasks = getProcessTasksForProductionTask(productionTask);
        System.out.println(consoleFontFormat + "[Step 1: Identifying Process Tasks] Process tasks for " + productionTask + ": " + processTasks + ConsoleColors.RESET);

        ArrayList<String> inCapableProcessTasks = new ArrayList<>();

//        System.out.println(consoleFontFormat + "[Step 2: Form] 2a. Incapable Tasks for Process Task " + processTask + " " + incTasks + ConsoleColors.RESET);
        for (String processTask : processTasks) {
            System.out.println(consoleFontFormat + "[Step 2a] Validate capability of each constituent process task description with possessed capabilities] Process tasks for " + productionTask + ": " + processTasks + ConsoleColors.RESET);
            ArrayList<TaskAndReport> incTasks = checkCapabilityOfProcess(processTask);
            // todo: you want to use the new class TaskAndReport method in this function checkCapabilityOfProductionTask()
            System.out.println(consoleFontFormat + "[Step 2b] Find class of capability instances that failed the validation " + ConsoleColors.RESET);
            ArrayList<String> failedActionCaps = new ArrayList<>(); // todo: this should be a Map<String, ArrayList<String>> {"Moving": ["Extract Moving","Approach Moving"]}

            for (TaskAndReport t : incTasks) {
                String procTask = t.getTask();
                ArrayList<String> failedActions = t.getFailedActions();  //RETURNS CAP_MOVING
//                ArrayList<String> failedActionCaps = t.getFailedActions();

                // for each failed action get the parent capability class
                knowledgeBase.begin(TxnType.READ);
                for (String failedAction : failedActions) {
                    String queryString = new SPARQLUtils().getPrefixes("camo", "owl") + """
                            SELECT ?capClass WHERE {
                                camo:""" + failedAction + """
                                            a ?capClass.
                                FILTER (!sameTerm(?capClass, owl:NamedIndividual))     
                            }
                            """;


                    Query query = QueryFactory.create(queryString);
                    QueryExecution queryExecution = QueryExecutionFactory.create(query, knowledgeBase);
                    ResultSet resultSet = queryExecution.execSelect();
                    Map<String,String> tempMap = new HashMap<>();
                    while (resultSet.hasNext()) {
                        QuerySolution solution = resultSet.nextSolution();
                        Resource capClass = solution.getResource("capClass");

                        failedActionCaps.add(capClass.getLocalName());
                    }
                    System.out.println(consoleFontFormat + failedActionCaps + ConsoleColors.RESET);
                }


                ProcessTaskValidation processTaskValidation = new ProcessTaskValidation(procTask, failedActionCaps, t.getFailedActionCapClassSHNameListMap());
//                inCapableProcessTasks.add(gson.toJson(processTaskValidation));
                map.put(processTask, processTaskValidation);
            }
            System.out.println(consoleFontFormat + "[Step 3] Merge information about process tasks that failed with their respective incapabilities to a suitable data structure]" + ConsoleColors.RESET);
            knowledgeBase.end();
        }

        System.out.println(consoleFontFormat + "[Step 4] If all process tasks are capable, update belief that the respeective production task is capable (isAchievableWithCap)" + ConsoleColors.RESET);
        if (map.size() == 0) {


            updateWithAchievableAxiom(productionTask);

        }


        return map;
    }

    public ArrayList<String> checkCapabilityOfProductionTask2(String productionTask) {

        ArrayList<String> processTasks = getProcessTasksForProductionTask(productionTask);
        System.out.println("Process tasks for " + productionTask + ": " + processTasks);

        ArrayList<String> inCapableProcessTasks = new ArrayList<>();

        String shapeDir = "";
        Resource capClass = null;
        knowledgeBase.begin(TxnType.READ_PROMOTE);
        boolean isCapable = true;
        for (String processTask : processTasks) {
            System.out.println("For process " + processTask);
//            isCapable = kb.getCapabilityRequiredOfTask(processTask);  // do this better currently it returns the last validation
//            if (!isCapable) {
//                System.out.println("Incapable");
//                robotState = State.IDLE;
//                break;
//            } else {
//                System.out.println("Capable");
//                isCapable = true;
//            }

            try {
                //1. Get the Shape defining the capability required.
                String queryString = su.getPrefixes("camo") + " SELECT ?shape WHERE { camo:" +
                        processTask + " camo:requiresCapability ?cap. " +
                        "?cap camo:hasShape ?shape.}";
                try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                    ResultSet rs = qExec.execSelect();
                    QuerySolution soln = rs.nextSolution();
                    shapeDir = soln.getLiteral("shape").getString();
//                System.out.println(shapeDir);
                }
                String _path = Paths.get(new URI(shapeDir)).toString();
//            String path = _path.replace("\\","\\\\");
//            System.out.println("Path: " + _path);

                // 2. Check if agent has the capability required
                //    2a. Get what class of Capability the shape is targeting

                Graph shapesGraph = RDFDataMgr.loadGraph(_path);
                Shapes shapes = Shapes.parse(shapesGraph);

                Model shapesModel = RDFDataMgr.loadModel(_path);
                String getTargetClassQuery = su.getPrefixes("sh") + " SELECT ?targetClass WHERE " +
                        "{?shape a sh:NodeShape. ?shape sh:targetClass ?targetClass.}";
                Query query = QueryFactory.create(getTargetClassQuery);
                QueryExecution queryExecution = QueryExecutionFactory.create(query, shapesModel);
                ResultSet resultSet = queryExecution.execSelect();

                //we assume that there is only one instance of each class of capability

                while (resultSet.hasNext()) {
                    QuerySolution soln = resultSet.nextSolution();
                    capClass = soln.getResource("targetClass");
                    System.out.println("Required Capability: " + capClass.getLocalName());
                }

                //     2b. Describe the instances of the Target Capability (if any-important) to be run the validation against
                ValidationReport report = null;

                if (capClass != null) {
                    String describeCapQuery = su.getPrefixes("cm") + " DESCRIBE ?cap  WHERE {" + "?cap a cm:" + capClass.getLocalName() + ".}";
                    Query query1 = QueryFactory.create(describeCapQuery);
                    queryExecution = QueryExecution.create(query1, knowledgeBase);
                    Model agentCapabilityModel = queryExecution.execDescribe();
                    Graph agentCapabilityGraph = agentCapabilityModel.getGraph();

                    System.out.println("\nDescribed model:");
                    agentCapabilityModel.write(System.out, "Turtle");
                    System.out.println("\n\n");

                    //3. Validate the Agent Capability Graph against the shapes the defines the capability required by the said task.
                    report = ShaclValidator.get().validate(shapes, agentCapabilityGraph);
                    System.out.println("Validation Report: ");
                    ShLib.printReport(report);


//                    if(!report.conforms()){
                    isCapable = report.conforms() && isCapable; //not sure if this works but no errors are evident
//                    }

                    if (!report.conforms()) {
                        inCapableProcessTasks.add(processTask);
                    }

                } else {
                    //TODO: for now return false, later see what exactly does not conform
                    System.out.println("No Target Capabilities Found to validate");

                }


            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        if (isCapable) {
            // update the specific task with that it isAchievableWithCap that cap
            //     get the instance
            System.out.println("Is Capable");
            assert capClass != null;
            String qs = su.getPrefixes("cm", "camo") + " SELECT ?capInst WHERE {" + "?capInst a cm:" + capClass.getLocalName() + ".}";

            Query _query = QueryFactory.create(qs);
            QueryExecution _queryExecution = QueryExecutionFactory.create(_query, knowledgeBase);
            ResultSet _resultSet = _queryExecution.execSelect();

            Resource capInst = null; //we assume that there is only one instance of each class of capability

            while (_resultSet.hasNext()) {
                QuerySolution soln = _resultSet.nextSolution();
                capInst = soln.getResource("capInst");
                System.out.println("Required Capability Inst: " + capInst.getLocalName());
            }

            // update
            String _qs = su.getPrefixes("camo", "cm") + " INSERT DATA {camo:" + productionTask + " camo:isAchievableWithCap camo:" + capInst.getLocalName() + ".}";

            UpdateRequest uReq = UpdateFactory.create(_qs);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

            knowledgeBase.commit();


        }
        knowledgeBase.end();


        return inCapableProcessTasks;

    }

    public ArrayList<String> getProcessTasksForProductionTask(String productionTask) {

        ArrayList<String> processTasks = new ArrayList<String>();
        knowledgeBase.begin(ReadWrite.READ);
        try {
            String qs1 = su.getPrefixes("camo", "xsd", "DUL") + " SELECT DISTINCT ?processTasks WHERE {camo:" + productionTask + " a camo:ProductionTask. " +
                    "camo:" + productionTask + " camo:hasProcessPlan ?plan. ?plan DUL:definesTask ?processTasks. }";
            try (QueryExecution qExec = QueryExecution.create(qs1, knowledgeBase)) {

                ResultSet rs = qExec.execSelect();

                while (rs.hasNext()) {
                    QuerySolution sol = rs.nextSolution();
                    processTasks.add(sol.getResource("?processTasks").getLocalName());
//                    System.out.println(currentTasks);
                }

//                System.out.println(currentTasks.size());
            }
        } finally {
            knowledgeBase.end();
        }

        return processTasks;
    }

    public boolean updateTaskExecution(String task) {
        // updates the PN with that results a fired transition

        boolean isEnabled;

        //1. Check if enabled
        knowledgeBase.begin(TxnType.READ_PROMOTE);

        try {
            String query = su.getPrefixes("camo", "xsd") + "ASK WHERE {?s camo:includesActivities camo:" + task + ". " +
                    "?s camo:hasToken \"true\"^^xsd:boolean. }";

            // TODO: CHANGE TO CORRECT DEFINITION USING  THE BELOW (UNTESTED)
            String qs1 = su.getPrefixes("camo") + "ASK WHERE " +
                    " WHERE  {" + task + " a camo:ProductionTask." +
                    """             
                                            ?state camo:includesActivities ?task1.
                                            ?state camo:hasToken true.
                                MINUS {     ?state1 a camo:ProductState.
                                            ?state1 camo:hasToken true.
                                            ?state2 a camo:ProductState.
                                            ?state2 camo:hasToken false.
                                            ?state1 camo:includesActivities ?task1.
                                            ?state2 camo:includesActivities ?task2.
                                            filter ((?task1=?task2))
                                        }
                                }
                            """;

            try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
                isEnabled = qExec.execAsk();

                if (isEnabled) {

                    //2. Get preceding and successive places as ArrayLists
                    System.out.println(consoleFontFormat + task + "is enabled" + ConsoleColors.RESET);
                    String sourceDestQuery = new SPARQLUtils().getPrefixes("camo") + " SELECT * WHERE {{?source camo:includesActivities camo:" + task +
                            ".} UNION {camo:" + task + " camo:leadsTo ?dest.}}";

                    ArrayList<String> sources = new ArrayList<String>();
                    ArrayList<String> dests = new ArrayList<String>();

                    try (QueryExecution qExec2 = QueryExecution.create(sourceDestQuery, knowledgeBase)) {

                        ResultSet rs2 = qExec2.execSelect();

                        while (rs2.hasNext()) {
                            QuerySolution sol = rs2.next();

                            Resource source = sol.getResource("source");
                            if (source != null) sources.add(source.getLocalName());

                            Resource dest = sol.getResource("dest");
                            if (dest != null) dests.add(dest.getLocalName());
                        }

                        System.out.println(consoleFontFormat + sources + ConsoleColors.RESET);
                        System.out.println(consoleFontFormat + dests + ConsoleColors.RESET);
                    }

                    // 3. Build DELETE INSERT WHERE Query
                    String whereClause = " WHERE {?places camo:hasToken ?hasSourceToken.}";

                    String delInsWhereClause = su.getPrefixes("camo", "xsd") + su.getDelInsClause(sources, dests) + whereClause;


                    //4. Perform update Query
                    UpdateRequest uReq = UpdateFactory.create(delInsWhereClause);
                    UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

                    knowledgeBase.commit();

                } else {
                    System.out.println("Task to be executed not current task");
                    return false;
                }

            }
            ;
        } finally {
            knowledgeBase.end();
        }

        return true;
    }


//    public InfModel getInferredModel() {
//        Model model = knowledgeBase.getDefaultModel();
//
//        InfModel infModel = ModelFactory.createInfModel(reasoner, model);
//        return infModel;
//    }

    public ArrayList<String> getInstancesOfClass(String className) {
        ArrayList<String> instances = new ArrayList<>();
        String query = su.getPrefixes("camo") + " SELECT ?inst WHERE {?inst a camo:" + className + "}";
        knowledgeBase.begin(TxnType.READ);

        try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();

            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String source = sol.getResource("inst").getLocalName();
                instances.add(source);
            }
        } finally {
            knowledgeBase.end();
        }

        return instances;
    }


    public OWLOntology getOWLOntology(OntModel ontmodel) {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        try (PipedInputStream is = new PipedInputStream(); PipedOutputStream os = new PipedOutputStream(is)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        knowledgeBase.begin(TxnType.WRITE);
                        // save jena model to os in RDF/XML format
                        ontmodel.write(os, "RDF/XML");
//                        ontology.getOWLOntologyManager().saveOntology(ontology, new TurtleDocumentFormat(), os);
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        knowledgeBase.commit();
                        knowledgeBase.end();
                    }
                }
            }).start();
            // read OWLOntology using is in RDF/XML
//            model.read(is, null, "TURTLE");
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(is);
            RDFXMLOntologyFormat rdfxmlOntologyFormat = new RDFXMLOntologyFormat();
            manager.setOntologyFormat(ontology, rdfxmlOntologyFormat);

            return ontology;
        } catch (Exception e) {
            throw new RuntimeException("Could not convert OWL API ontology to JENA API model.", e);
        }

    }

    public OntModel getOntModel(OWLOntology ontology) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

        try (PipedInputStream is = new PipedInputStream(); PipedOutputStream os = new PipedOutputStream(is)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ontology.getOWLOntologyManager().saveOntology(ontology, new RDFXMLOntologyFormat(), os);
                        os.close();
                    } catch (OWLOntologyStorageException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            ontModel.read(is, null, "RDF/XML");
            return ontModel;
        } catch (Exception e) {
            throw new RuntimeException("Could not convert OWL API ontology to JENA API model.", e);
        }
    }

    public void printStatements(Model m, Resource s, Property p, Resource o) {
        for (StmtIterator i = m.listStatements(s, p, o); i.hasNext(); ) {
            Statement stmt = i.nextStatement();
            System.out.println(" - " + PrintUtil.print(stmt));
        }
    }

    public void updateIntention() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        knowledgeBase.begin(TxnType.READ_PROMOTE);

        // get the intention named model
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, knowledgeBase.getDefaultModel());
        knowledgeBase.commit();
        knowledgeBase.end();


        // convert to OWLOntology and run inference

        OWLOntology ontology = getOWLOntology(ontModel);
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        //DEBUG: INSPECT THE ONTOLOGY BY OUTPUTTING TO FILE (OWLAPI)
//        File file = new File("currModel"+String.valueOf(++i)+".rdf");
//        RDFXMLOntologyFormat rdfxmlOntologyFormat = new RDFXMLOntologyFormat();
//        manager.setOntologyFormat(ontology,rdfxmlOntologyFormat);
//        manager.saveOntology(ontology, IRI.create(file.toURI()));

        knowledgeBase.begin(TxnType.READ_PROMOTE);


        OpenlletReasoner reasoner_openllet = OpenlletReasonerFactory.getInstance().createReasoner(ontology);

        long startTime = System.currentTimeMillis();
        reasoner_openllet.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("[Operator]  Inference Time: " + elapsedTime / 1000.0 + "s.");

        List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens.add(new InferredClassAssertionAxiomGenerator());

        OWLOntology infOnt = manager.createOntology(); // temporary fresh ontology to store inferred axioms alone

        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner_openllet, gens); //choose the right reasoner
        startTime = System.currentTimeMillis();
        iog.fillOntology(manager.getOWLDataFactory(), infOnt);
        elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("[Operator] Time to create ontology: " + elapsedTime / 1000.0 + "s.");

        // Convert back to Jena ONTModel and add to intention named model

        OntModel ontModel1 = getOntModel(infOnt);

//        //DEBUG: INSPECT THE ONTOLOGY BY OUTPUTTING TO FILE (JENA)
//        String defaultModelFileName = "currModel"+String.valueOf(++i)+".ttl";
//        FileWriter currOut = new FileWriter(defaultModelFileName);
//        ontModel1.writeAll(currOut, "TTL");
//        currOut.close();

        // get the intention named model
        knowledgeBase.replaceNamedModel("https://joedavid91.github.io/ontologies/camo-named-graph/operator-intention", ontModel1);
        knowledgeBase.commit();
        knowledgeBase.end();

    }

    public void updateNamedGraph() throws OWLOntologyCreationException {

        // Just updates a named graph with all inferred axioms of the current "state"
        ArrayList<String> instances = new ArrayList<>();

        knowledgeBase.begin(TxnType.WRITE);

        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, knowledgeBase.getDefaultModel());
        knowledgeBase.commit();
        knowledgeBase.end();

        OWLOntology ontology = getOWLOntology(ontModel); // using piped streams
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        knowledgeBase.begin(TxnType.WRITE);
        OpenlletReasoner reasoner_openllet = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        reasoner_openllet.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

        List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens.add(new InferredClassAssertionAxiomGenerator());
        OWLOntology infOnt = manager.createOntology(); // temporary fresh ontology to store inferred axioms alone

        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner_openllet, gens);

        iog.fillOntology(manager.getOWLDataFactory(), infOnt);

        OntologyManager _manager = OntManagers.createManager();
        Ontology ontOntology = _manager.copyOntology(infOnt, OntologyCopy.DEEP); //make sure its the ontology containing the inferred axioms
        knowledgeBase.replaceNamedModel("https://joedavid91.github.io/ontologies/camo-named-graph/intention", ontOntology.asGraphModel());

        knowledgeBase.commit();
        knowledgeBase.end();
    }

    public ArrayList<String> getIntentions() throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        ArrayList<String> instances = new ArrayList<>();

        knowledgeBase.begin(TxnType.READ_PROMOTE);

        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, knowledgeBase.getDefaultModel());
        knowledgeBase.commit();
        knowledgeBase.end();
        //DEBUG: INSPECT THE ONTOLOGY BY OUTPUTTING TO FILE (JENA)
//        String defaultModelFileName = "currModel"+String.valueOf(++i)+".ttl";
//        FileWriter currOut = new FileWriter(defaultModelFileName);
//        ontmodel.write(currOut,"TTL");
//        currOut.close();


        // Convert to OntAPI for inferencing (21.07 ONTAPI was causing all Production tasks to be inferred as Intentions
        // right from the start. The cause remains unknown even after several days of investigation. This is strange as
        // ONTAPI worked in isolated project (see testOWLAPI>testONTAPI.java). The alternate, using streams to convert
        // jena OntModel to OWLAPI Ontology works.)

        // CONSIDERING WORK WITH JENA ITSELF VIA OPENLLET https://github.com/Galigator/openllet, IT COULD BE FASTER AND
        // OF COURSE DOESNT REQUIRE CONVERTING TO OWLAPI

//        OntologyManager manager = OntManagers.createManager();
//        Ontology ontology = manager.addOntology(ontmodel.getGraph());
////        Ontology ontology = manager.addOntology(knowledgeBase.asDatasetGraph().getDefaultGraph());
////        OWLReasonerFactory reasonerFactory1 = new StructuralReasonerFactory();


//        OWLReasoner reasoner1 = reasonerFactory2.createReasoner(ontology);
        OWLOntology ontology = getOWLOntology(ontModel); // using piped streams

//DEBUG: INSPECT THE ONTOLOGY BY OUTPUTTING TO FILE (OWLAPI)
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
//        File file = new File("currModel"+String.valueOf(++i)+".owl");
//        RDFXMLOntologyFormat rdfxmlOntologyFormat = new RDFXMLOntologyFormat();
//        manager.setOntologyFormat(ontology,rdfxmlOntologyFormat);
////        System.out.println("Document IRI" + manager.getOntologyDocumentIRI(ontology));
//        manager.saveOntology(ontology, IRI.create(file.toURI()));
//

        knowledgeBase.begin(TxnType.READ_PROMOTE);


        OpenlletReasoner reasoner_openllet = OpenlletReasonerFactory.getInstance().createReasoner(ontology);

//        OWLReasonerFactory reasonerFactory2 = new org.semanticweb.HermiT.Reasoner.ReasonerFactory();
//        OWLReasoner reasoner_hermit = reasonerFactory2.createReasoner(ontology);
        System.out.println(consoleFontFormat + "Reasoning on Robot Beliefs .." + ConsoleColors.RESET);
        long startTime = System.currentTimeMillis();
        reasoner_openllet.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
//        reasoner_hermit.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println(consoleFontFormat + "Inference Time: " + elapsedTime / 1000.0 + "s." + ConsoleColors.RESET);

        List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<InferredAxiomGenerator<? extends OWLAxiom>>();
        gens.add(new InferredClassAssertionAxiomGenerator());

        OWLOntology infOnt = manager.createOntology(); // temporary fresh ontology to store inferred axioms alone

        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner_openllet, gens); //choose the right reasoner
        startTime = System.currentTimeMillis();
        iog.fillOntology(manager.getOWLDataFactory(), infOnt);
        elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println(consoleFontFormat + "Generating inferred beliefs .. " + ConsoleColors.RESET);
        System.out.println(consoleFontFormat + "Time to create ontology: " + elapsedTime / 1000.0 + "s." + ConsoleColors.RESET);


//        OWLDataFactory factory = manager.getOWLDataFactory();
////        IRI iri = IRI.create("https://joedavid91.github.io/ontologies/camo/product");
//        OWLOntology infOWLOnt= manager.createOntology();
//        System.out.println("Before Filling Ontology");
//        gen.fillOntology(factory,infOWLOnt);
//        System.out.println("After Filling Ontology");

        boolean consistent = reasoner_openllet.isConsistent();
        System.out.println(consoleFontFormat + "Consistent: " + consistent + ConsoleColors.RESET);


        //        OLDWAY to query owlapi ontology programmatically replaced with a sparql solution (ONTAPI-SPARQL)
        //        because inferring intentions is not possible with reaosner alone due to OWA and so you opted
        //         for a solution with inference accompanied by a simple sparql query  (do you want to relpace Intention with goals)
//        OWLDataFactory factory = manager.getOWLDataFactory();
//        OWLClass Intention  = factory.getOWLClass("https://joedavid91.github.io/ontologies/camo/product#Intention");
//        NodeSet<OWLNamedIndividual> intentionNodeSet = reasoner.getInstances(Intention,true);
//        Set<OWLNamedIndividual> intentions = intentionNodeSet.getFlattened();
        System.out.println(consoleFontFormat + "Querying inferred beliefs .. " + ConsoleColors.RESET);
        String queryString = su.getPrefixes("camo") + " SELECT ?int WHERE { " +
                " ?int a camo:Intention." +
                " FILTER NOT EXISTS { " +
                "?int a camo:NonAchievableDesire. " + //todo: what about NonConflicting?
                "} " +
                "}";

        Query query = QueryFactory.create(queryString);

//        converting OWLAPI ontology to ONT-API Ontology to run sparql
        OntologyManager _manager = OntManagers.createManager();
        Ontology ontOntology = _manager.copyOntology(infOnt, OntologyCopy.DEEP); //make sure its the ontology containing the inferred axioms
//        knowledgeBase.addNamedModel("https://joedavid91.github.io/ontologies/camo-named-graph/intention", ontOntology.asGraphModel());


        try (QueryExecution queryExecution = QueryExecutionFactory.create(query, ontOntology.asGraphModel())) {
            ResultSet res = queryExecution.execSelect();
            while (res.hasNext()) {
//                System.out.println(res.next());
                QuerySolution sol = res.next();

                Resource intention = sol.getResource("int");
                if (intention != null) instances.add(intention.getLocalName());
            }

//        }
//        for (OWLNamedIndividual intention: intentions){
//            instances.add(intention.toString());
//        }
        }
        knowledgeBase.commit();
        knowledgeBase.end();
        return instances;
    }


    public ArrayList<String> getActiveProcesses(String productionTask) {

        ArrayList<String> activeProcesses = new ArrayList<>();
        String msg;
        String queryString = su.getPrefixes("camo", "xsd", "DUL") + "SELECT ?processTask WHERE {" +
                "camo:" + productionTask + " camo:hasProcessPlan ?processPlan." +
                """ 
                                ?processPlan DUL:definesTask ?processTask.
                                ?processState camo:includesActivities ?processTask.
                                ?processState camo:hasToken "true"^^xsd:boolean.
                                MINUS {
                                 ?state1 camo:belongsToProcessPlan ?processPlan.
                                    ?state2 camo:belongsToProcessPlan ?processPlan.
                                    ?state1 camo:hasToken true.
                                    ?state2 camo:hasToken false.
                                    ?state1 camo:includesActivities ?process1.
                                    ?state2 camo:includesActivities ?process2.
                                    filter((?process1=?process2))
                                }
                        }""";

        Query query = QueryFactory.create(queryString);
        knowledgeBase.begin(TxnType.READ);


        try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                msg = soln.getResource("?processTask").getURI(); // for some reason you cant get the local name directly
                String[] msgs = msg.split("#");
                msg = msgs[msgs.length - 1];
                activeProcesses.add(msg);
            }
            knowledgeBase.end();
            return activeProcesses;
        }


    }


    public ArrayList<String> checkForActions(String processTask) throws URISyntaxException {
        // TODO: incomplete function
        ArrayList<String> noActionForTasks = new ArrayList<>();
        String plan = null;

        // get the different primitive tasks in the shape (same as before)

        System.out.println(consoleFontFormat + "[Step 1] Check the description of the process" + ConsoleColors.RESET);
        String shapeDir = "";
        knowledgeBase.begin(TxnType.READ);
        try {
            //1. Get the Shape path defining the capability required.
            String queryString = su.getPrefixes("camo") + " SELECT ?shape WHERE { camo:" +
                    processTask + " camo:requiresCapability ?cap. " +
                    "?cap camo:hasShape ?shape.}";
            try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    shapeDir = soln.getLiteral("shape").getString();
                }
                System.out.println(shapeDir);
            }
            String _path = Paths.get(new URI(shapeDir)).toString();

            // 2. Check if agent has the action for the primitive task
            //    2a. Get what class of Capability the shape is targeting

            Graph shapesGraph = RDFDataMgr.loadGraph(_path);
            Shapes shapes = Shapes.parse(shapesGraph);

            System.out.println(consoleFontFormat + "[Step 2] Get what capability is each primitive task requiring" + ConsoleColors.RESET);

            Model shapesModel = RDFDataMgr.loadModel(_path);
            String getTargetClassQuery = su.getPrefixes("sh", "cm") + " SELECT ?targetClass WHERE " +
                    "{?shape a sh:NodeShape. ?shape sh:targetClass ?targetClass.}";
            Query query = QueryFactory.create(getTargetClassQuery);
            QueryExecution queryExecution = QueryExecutionFactory.create(query, shapesModel);
            ResultSet resultSet = queryExecution.execSelect();

            ArrayList<String> capClassList = new ArrayList<>();
            while (resultSet.hasNext()) {
                QuerySolution soln = resultSet.nextSolution();
                capClassList.add(soln.getResource("targetClass").getLocalName());
                System.out.println(consoleFontFormat + "Required Capabilities: " + capClassList + ConsoleColors.RESET);
            }


            //get the plan active for the process task
            System.out.println(consoleFontFormat + "[Step 3] Get the active plan for the process task" + ConsoleColors.RESET);

            String qs = su.getPrefixes("camo", "DUL") + " SELECT ?agentPlan WHERE { " +
                    "?processState camo:includesActivities camo:" + processTask + ". " +
                    "?processState DUL:satisfies ?agentPlan.}";
            try (QueryExecution qExec = QueryExecution.create(qs, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    plan = soln.getResource("agentPlan").getLocalName();
                }
            }

            System.out.println(consoleFontFormat + "Plan " + plan + ConsoleColors.RESET);

            // for every primitive task there is an action
            // get the task

            System.out.println(consoleFontFormat + "[Step 4] Check that for each primitive task there is an action in the plan based on the capability required by both" + ConsoleColors.RESET);

            String qs2 = su.getPrefixes("camo") + " SELECT ?action WHERE { " +
                    "?action camo:isPartOfPlan camo:" + plan + ".}";

            query = QueryFactory.create(qs2);
            queryExecution = QueryExecutionFactory.create(query, knowledgeBase);
            resultSet = queryExecution.execSelect();

            ArrayList<String> actions = new ArrayList<>();

            while (resultSet.hasNext()) {
                QuerySolution soln = resultSet.nextSolution();
                actions.add(soln.getResource("action").getLocalName());

            }

            System.out.println(consoleFontFormat + "Actions " + actions + ConsoleColors.RESET);

            // Step 5 will be for each of these actions, check its capability class and compare with required capabilities of the tasks to see what
            // actions are missing in the plan

            knowledgeBase.end();

            return noActionForTasks;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void debug() {
        knowledgeBase.begin(TxnType.READ);
        System.out.println("================================DEBUG====================================");
        String dQuery = "PREFIX camo:<https://joedavid91.github.io/ontologies/camo/product#> " +
                "SELECT ?activePlan WHERE {" +
                " GRAPH <https://joedavid91.github.io/ontologies/camo-named-graph/intention> " +
                "{ ?activePlan a camo:ActivePlan. } " +
                "}";
        try (QueryExecution queryExecution = QueryExecutionFactory.create(dQuery, knowledgeBase)) {
            ResultSet res = queryExecution.execSelect();
            while (res.hasNext()) {
//                System.out.println(res.next());
                QuerySolution sol = res.next();

                Resource activePlan = sol.getResource("activePlan");
                if (activePlan != null) System.out.println(activePlan);
            }

//        }
//        for (OWLNamedIndividual intention: intentions){
//            instances.add(intention.toString());
//        }
        }

        System.out.println("================================DEBUG====================================");
        knowledgeBase.end();
    }

    public Model describeResquestedCaps(ArrayList<ProcessTaskValidation> processTaskValidations) {
        // send back union of all models that describe all requested caps (if available)
        knowledgeBase.begin(TxnType.READ);
        Model agentCapabilityModel = null;
        for (ProcessTaskValidation processTaskValidation : processTaskValidations) {
            String processTask = processTaskValidation.getProcessTask();
            ArrayList<String> failedActionCapClasses = processTaskValidation.getFailedActionCapClasses();

            if (failedActionCapClasses.size() > 0) {
                // describing works for any number of classes present in the list as a dyanmic query is generated
                String describeCapQuery = su.getPrefixes("camo", "cm") + su.describeCapablitiesQuery(failedActionCapClasses);

//                System.out.println("DESCRIBE CAP QUERY: " + describeCapQuery);
                Query query = QueryFactory.create(describeCapQuery);
                QueryExecution queryExecution = QueryExecution.create(query, knowledgeBase);

                agentCapabilityModel = queryExecution.execDescribe();

            }

            knowledgeBase.end();
        }
        return agentCapabilityModel;
    }

    public boolean validateRequestedProcessTasks(Graph requestedCapDescriptions, ArrayList<ProcessTaskValidation> processTaskValidations) throws URISyntaxException {
        knowledgeBase.begin(TxnType.READ);
        String shapeDir = new String();
        boolean conforms = true;

        //      1. For each process task get the process description (shape)

        for (ProcessTaskValidation processTaskValidation : processTaskValidations) {
            String processTask = processTaskValidation.getProcessTask();
            System.out.println("Debug: " + processTask);
            String queryString = new SPARQLUtils().getPrefixes("camo") + " SELECT ?shape WHERE { camo:" +
                    processTask + " camo:requiresCapability ?cap. " +
                    "?cap camo:hasShape ?shape.}";
            try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    shapeDir = soln.getLiteral("shape").getString();
                    System.out.println("Debug: " + shapeDir);
                }
            }
            String _path = Paths.get(new URI(shapeDir)).toString();


            //      2. Validate each process task description with the combined capability model

            Graph shapesGraph = RDFDataMgr.loadGraph(_path);
            Shapes shapes = Shapes.parse(shapesGraph);

            ValidationReport report = ShaclValidator.get().validate(shapes, requestedCapDescriptions);

            if (!report.conforms()) {
                conforms = false;
            } else {
                System.out.println("Incapable conforms");
            }

        }
        knowledgeBase.end();
        return conforms;
    }

    public ArrayList<PrimitveTask> getProcessDescription(String processName) {

        String shapeDir = "";
        String processParentClass = "";
        ArrayList<PrimitveTask> pts = new ArrayList<PrimitveTask>();
        knowledgeBase.begin(TxnType.READ);
        try {
            //1. Get the Shape path defining the capability required.
            System.out.println(consoleFontFormat + "\ta. Getting process description of: " + processName + ConsoleColors.RESET);
            String queryString = su.getPrefixes("camo","owl") + " SELECT ?shape  WHERE { " +
                    "camo:" + processName + " camo:requiresCapability ?cap. " +
                    "?cap camo:hasShape ?shape." +
                    "}";
            try (QueryExecution qExec = QueryExecution.create(queryString, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while (rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    shapeDir = soln.getLiteral("shape").getString();
                }
                System.out.println(shapeDir);
            }

            String _path = Paths.get(new URI(shapeDir)).toString();
            System.out.println(consoleFontFormat + "\t\t i. path found to be: " + _path + ConsoleColors.RESET);

            // todo: get class of process for context

            // 2. Check if agent has the capability required
            //    2a. Get what class of Capability the shape is targeting
            System.out.println(consoleFontFormat + "\t\t ii. parsing description" + ConsoleColors.RESET);
            Graph shapesGraph = RDFDataMgr.loadGraph(_path);
            Shapes shapes = Shapes.parse(shapesGraph);

            Model shapesModel = RDFDataMgr.loadModel(_path);
            System.out.println(consoleFontFormat + "\tb. querying description.." + ConsoleColors.RESET);

            Resource action = null;
            ;
            Resource group = null;
            Literal order = null;
            Literal desc = null;
            Literal ctx = null;


            queryString = new SPARQLUtils().getPrefixes("sh") + " SELECT ?actionClass ?group ?order ?desc ?name ?context WHERE { " +
//                    "SELECT ?order  WHERE{"+
                    " ?nodeShape a sh:NodeShape." +
                    " ?nodeShape sh:targetClass ?actionClass. " +
                    " ?nodeShape sh:property ?propertyShape. " +
                    " ?propertyShape sh:value ?actionClass. " +
                    " ?propertyShape sh:order ?order. " +
                    " ?propertyShape sh:group ?group. " +
                    " ?propertyShape sh:description ?desc. " +
                    " ?propertyShape sh:name ?context. " +
//                    "} GROUP BY ?order" +
                    "} ORDER BY asc(?order)";

            Query _query = QueryFactory.create(queryString);

            QueryExecution queryExecution1 = QueryExecutionFactory.create(_query, shapesModel);
            ResultSet _resultSet1 = queryExecution1.execSelect();


            QueryExecution queryExecution2 = QueryExecutionFactory.create(_query, shapesModel);
            ResultSet _resultSet2 = queryExecution2.execSelect();

            while (_resultSet1.hasNext()) {
                QuerySolution soln = _resultSet1.nextSolution();
                action = soln.getResource("actionClass");
                group = soln.getResource("group");
                order = soln.getLiteral("order");
                desc = soln.getLiteral("desc");
                ctx = soln.getLiteral("context");
                System.out.println("Action: " + action);
                System.out.println("Group: " + group);
                System.out.println("Order: " + order);

                PrimitveTask pt = new PrimitveTask(action.getLocalName(), "", "", desc.toString(), group.getLocalName(), order.getInt(), ctx.toString());
                pts.add(pt);
            }

//            ResultSetFormatter.out(System.out, _resultSet2) ;

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        knowledgeBase.end();
        return pts;

    }

    public String getPlaceForTransition(String task) {
        knowledgeBase.begin(TxnType.READ);
        String place = new String();
        String query = su.getPrefixes("camo") + " SELECT ?place WHERE {" +
                "?place camo:includesActivities camo:" + task + ". " +
                "}";
        try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                place = soln.getResource("place").getLocalName();
            }
        }
        knowledgeBase.end();
        return place;

    }

    public void generatePlans(String productionTask) throws IOException {

        ArrayList<String> allProcessTasks = getProcessTasksForProductionTask(productionTask);

//        for(ProcessTaskValidation item: robotInCapableProcessTasks.values()) {
//            ProcessTaskValidation ptv = gson.fromJson(item, ProcessTaskValidation.class);
//            robotIncapableProcessTaskValidation.add(ptv);
//            incapableProcessTaskNames.add(ptv.getProcessTask());
//        }

        for (String processTask : allProcessTasks) {  // for all process tasks of the production task

            ArrayList<PrimitveTask> description = getProcessDescription(processTask);// get the process description

            ArrayList<String> queries = new ArrayList<>();
            ArrayList<String> actionNames = new ArrayList<>();
            String uid = String.valueOf((int) (Math.random() * 100000));
            String processClass = getParentClassOfInst(processTask);

            for (PrimitveTask pt : description) {
                String uid2 = String.valueOf((int) (Math.random() * 100000));
                String actionName = "ACT_" + pt.getName().substring(0, Math.min(pt.getName().length(), 3)) + "_" + uid + "_" + uid2;
                // generate robot actions for all primitive tasks except those that are not failed (ptv)
                String query = su.getPrefixes("camo", "xsd") + "INSERT DATA{camo:" + actionName + " a camo:Action. " +
                        "camo:" + actionName + " camo:hasCapabilityClass \"http://resourcedescription.tut.fi/ontology/capabilityModel#" + pt.getName() + "\"^^xsd:anyURI. " +
                        "camo:" + actionName + " camo:order \"" + pt.getOrder() + "\"^^xsd:int. " +
                        "camo:" + actionName + " camo:hasStatus \"initial\"^^xsd:string. " +
                        "camo:" + actionName + " camo:isPerformedBy \"robot\"^^xsd:string. " +
                        "camo:" + actionName + " camo:UID \"" + (int) (Math.random() * 100000) + "\"^^xsd:string. " +
                        "camo:" + actionName + " camo:hasExecutionIdentifier \""+processClass+"\"^^xsd:string. " +  // todo: do you want to follow the same pattern as collaborative plans as you dont use this?
                        //add date time if necessary later
                        "}";
                queries.add(query);
                actionNames.add(actionName);
            }

            String situation = getPlaceForTransition(processTask);

            String planName = "AP_" + processTask;
            StringBuilder query = new StringBuilder(su.getPrefixes("camo", "xsd") + " INSERT DATA { camo:" + planName + " a camo:AgentPlan. ");
            query.append(" camo:").append(planName).append(" camo:hasType \"individual\"^^xsd:String. ");
            query.append(" camo:").append(planName).append(" camo:hasHead camo:").append(situation).append(". ");
            for (String action : actionNames) {
                query.append("camo:").append(action).append(" camo:isPartOfPlan camo:").append(planName).append(". ");
            }

            query.append("}");

            queries.add(query.toString());

            System.out.println("[debug] Queries: " + queries);

            knowledgeBase.begin(TxnType.WRITE);

            for (String q : queries) {
                UpdateRequest uReq = UpdateFactory.create(q);
                UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

            }
            knowledgeBase.commit();
            knowledgeBase.end();
        }

//        writeToFile("planDebug.rdf");


    }

    public void generateCollaborativePlans(String productionTask, ArrayList<String> operatorCapableClasses, Map<String, ProcessTaskValidation> robotInCapableProcessTasks) throws IOException {

        ArrayList<String> allProcessTasks = getProcessTasksForProductionTask(productionTask);
        ArrayList<ProcessTaskValidation> robotIncapableProcessTaskValidation = new ArrayList<>();
        ArrayList<String> incapableProcessTaskNames = new ArrayList<>();

//        for(ProcessTaskValidation item: robotInCapableProcessTasks.values()) {
//            ProcessTaskValidation ptv = gson.fromJson(item, ProcessTaskValidation.class);
//            robotIncapableProcessTaskValidation.add(ptv);
//            incapableProcessTaskNames.add(ptv.getProcessTask());
//        }


        for (String processTask : allProcessTasks) {  // for all process tasks of the production task
            String processClass = getParentClassOfInst(processTask);
            ArrayList<PrimitveTask> description = getProcessDescription(processTask);// get the process description

            ArrayList<String> queries = new ArrayList<>();
            ArrayList<String> actionNames = new ArrayList<>();
            String uid = String.valueOf((int) (Math.random() * 100000));
            String planType = "";

            if (robotInCapableProcessTasks.containsKey(processTask)) {  // if not completely capable by robot, i.e. collaborative process
                // TODO
                planType= "collaborative";
//                System.out.println("===================DEBUG======================");
//                System.out.println(Arrays.asList(robotInCapableProcessTasks.get(processTask).getFailedActionCapClassSHNameListMap()));
//

                for (PrimitveTask pt : description) {

                    // generate robot actions for all primitive tasks except those that are not failed (ptv)
                    ProcessTaskValidation ptv = robotInCapableProcessTasks.get(processTask);
                    ArrayList<String> failedActionCapClasses = ptv.getFailedActionCapClasses();
                    Map<String, ArrayList<String>> map = ptv.getFailedActionCapClassSHNameListMap();

                    String actionName = "ACT_" + pt.getName().substring(0, Math.min(pt.getName().length(), 3)) + "_" + uid;
                    System.out.println("[Debug2]" + failedActionCapClasses);
                    System.out.println("[Debug3]" + pt.getName());
                    System.out.println("[Debug4]" + operatorCapableClasses);
                    if (map.containsKey(pt.getName()) && operatorCapableClasses.contains(pt.getName())) {

                        if(map.get(pt.getName()).contains(pt.getContext())){
                            String uid2 = String.valueOf((int) (Math.random() * 100000));
                            actionName  = actionName  + "_" + uid2;
                            String query = su.getPrefixes("camo", "xsd") + "INSERT DATA{camo:" + actionName+" a camo:Action. " +
                                    "camo:" + actionName + " camo:hasCapabilityClass \"http://resourcedescription.tut.fi/ontology/capabilityModel#" + pt.getName() + "\"^^xsd:anyURI. " +
                                    "camo:" + actionName + " camo:order \"" + pt.getOrder() + "\"^^xsd:int. " +
                                    "camo:" + actionName + " camo:hasStatus \"initial\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:isPerformedBy \"operator\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:UID \"" + (int) (Math.random() * 100000) + "\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:hasExecutionIdentifier \""+processClass+"_"+pt.getName()+"_"+pt.getContext()+"\"^^xsd:string. " +

                                    //add date time if necessary later
                                    "}";
                            queries.add(query);
                        }
                        else{
                            String uid2 = String.valueOf((int) (Math.random() * 100000));
                            actionName  = actionName  + "_" + uid2;
                            String query = su.getPrefixes("camo", "xsd") + "INSERT DATA{camo:" + actionName+" a camo:Action. " +
                                    "camo:" + actionName + " camo:hasCapabilityClass \"http://resourcedescription.tut.fi/ontology/capabilityModel#" + pt.getName() + "\"^^xsd:anyURI. " +
                                    "camo:" + actionName + " camo:order \"" + pt.getOrder() + "\"^^xsd:int. " +
                                    "camo:" + actionName + " camo:hasStatus \"initial\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:isPerformedBy \"robot\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:UID \"" + (int) (Math.random() * 100000) + "\"^^xsd:string. " +
                                    "camo:" + actionName + " camo:hasExecutionIdentifier \""+processClass+"_"+pt.getName()+"_"+pt.getContext()+"\"^^xsd:string. " +

                                    //add date time if necessary later
                                    "}";
                            queries.add(query);
                        }

                    } else {
                        String uid2 = String.valueOf((int) (Math.random() * 100000));
                        actionName  = actionName  + "_" + uid2;
                        String query = su.getPrefixes("camo", "xsd") + "INSERT DATA {camo:" + actionName + " a camo:Action. " +
                                "camo:" + actionName + " camo:hasCapabilityClass \"http://resourcedescription.tut.fi/ontology/capabilityModel#" + pt.getName() + "\"^^xsd:anyURI. " +
                                "camo:" + actionName + " camo:order \"" + pt.getOrder() + "\"^^xsd:int. " +
                                "camo:" + actionName + " camo:hasStatus \"initial\"^^xsd:string. " +
                                "camo:" + actionName + " camo:isPerformedBy \"robot\"^^xsd:string. " +
                                "camo:" + actionName + " camo:UID \"" + (int) (Math.random() * 100000) + "\"^^xsd:string. " +
                                "camo:" + actionName + " camo:hasExecutionIdentifier \""+processClass+"_"+pt.getName()+"_"+pt.getContext()+"\"^^xsd:string. " +

                                //add date time if necessary later
                                "}";

                        queries.add(query);

                    }
                    actionNames.add(actionName);
                }


                // TODO: add actions to AGENT_plan and add head to corresponding place

            } else { // if this is not a robot incapable task then make actions for every primitive task doable by the robot itself, i.e. individual process
                planType= "individual";
                for (PrimitveTask pt : description) {
                    String uid2 = String.valueOf((int) (Math.random() * 100000));
                    String actionName = "ACT_" + pt.getName().substring(0, Math.min(pt.getName().length(), 3)) + "_" + uid+ "_" + uid2;
                    String query = su.getPrefixes("camo", "xsd") + "INSERT DATA {camo:" + actionName + " a camo:Action. " +
                            "camo:" + actionName + " camo:hasCapabilityClass \"http://resourcedescription.tut.fi/ontology/capabilityModel#" + pt.getName() + "\"^^xsd:anyURI. " +
                            "camo:" + actionName + " camo:order \"" + pt.getOrder() + "\"^^xsd:int. " +
                            "camo:" + actionName + " camo:hasStatus \"initial\"^^xsd:string. " +
                            "camo:" + actionName + " camo:isPerformedBy \"robot\"^^xsd:string. " +
                            "camo:" + actionName + " camo:UID \"" + (int) (Math.random() * 100000) + "\"^^xsd:string. " +
                            "camo:" + actionName + " camo:hasExecutionIdentifier \""+processClass+"_"+pt.getName()+"_"+pt.getContext()+"\"^^xsd:string. " +

                            //add date time if necessary later
                            "}";

                    queries.add(query);
                    actionNames.add(actionName);
                }

            }

            String situation = getPlaceForTransition(processTask);

            String planName = "AP_" + processTask;
            StringBuilder query = new StringBuilder(su.getPrefixes("camo", "xsd") + " INSERT DATA { camo:" + planName + " a camo:AgentPlan. ");
            query.append(" camo:").append(planName).append(" camo:hasType \"").append(planType).append("\"^^xsd:String. "); //todo: not all plans that reach here are collabprative
            query.append(" camo:").append(planName).append(" camo:hasHead camo:").append(situation).append(". ");
            for (String action : actionNames) {
                query.append("camo:").append(action).append(" camo:isPartOfPlan camo:").append(planName).append(". ");
            }

            query.append("}");

            queries.add(query.toString());

            System.out.println("[debug] Queries: " + queries);

            knowledgeBase.begin(TxnType.WRITE);

            for (String q : queries) {
                UpdateRequest uReq = UpdateFactory.create(q);
                UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

            }
            knowledgeBase.commit();
            knowledgeBase.end();
        }


    }

    private String getParentClassOfInst(String instance) {

        String parentClass = "";
        String query = su.getPrefixes("camo","owl") + " SELECT ?parentClass WHERE { " +
                "camo:"+instance+ " a ?parentClass. " +
                " FILTER (!sameTerm(?parentClass, owl:NamedIndividual))"+
                "}";

        knowledgeBase.begin(TxnType.READ);

        try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                parentClass = soln.getResource("parentClass").getLocalName();
            }
        }
        knowledgeBase.end();
        return parentClass;


    }

    public ArrayList<String> extractCapabilityClassOfOperator(String desc) throws IOException {
        // todo: redundant with above getParentClassOfInst()?
        ArrayList<String> classList = new ArrayList<>();
        InputStream stream = IOUtils.toInputStream(desc, "UTF-8");
        Model model = ModelFactory.createDefaultModel();
        model.read(stream, null, "TURTLE");

        String query = su.getPrefixes("camo", "cm", "owl") + "SELECT ?class WHERE {" +
                "?inst a ?class." +
                "}";

        knowledgeBase.begin(TxnType.READ);

        try (QueryExecution qExec = QueryExecution.create(query, model)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                String className = soln.getResource("class").getLocalName();
                if (!Objects.equals(className, "NamedIndividual")) {
                    classList.add(className);
                }
            }
        }
        knowledgeBase.end();
        return classList;


    }

    public boolean areActionsExecuted(String processTask) throws IOException {

        writeToFile("Completion_check_" + processTask+ ".rdf");
        System.out.println("Checking if all actions are executed for processTask: " + processTask);
        ArrayList<String> statuses = new ArrayList<>();
        String query = su.getPrefixes("camo") + "SELECT ?action ?status WHERE {" +
                "?processState camo:includesActivities camo:" + processTask + "." +
                "?agentPlan camo:hasHead ?processState." +
                "?action camo:isPartOfPlan ?agentPlan." +
                "?action camo:hasStatus ?status.}";

        knowledgeBase.begin(TxnType.WRITE);

        try (QueryExecution qExec = QueryExecution.create(query, knowledgeBase)) {
            ResultSet rs = qExec.execSelect();
            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                String action = soln.getResource("action").getLocalName();
                String status = soln.getLiteral("status").getString();
                System.out.println("Action: " + action + "  Status: " + status);
                statuses.add(status);
            }
        }
        knowledgeBase.commit();
        knowledgeBase.end();
        System.out.println("statuses: " + statuses);
        if ((statuses.contains("initial") || statuses.contains("performing"))) {
            return false;
        } else {
            return true;
        }

    }
}


// Code to print Jena Models to file
//        String defaultModelFileName = "defModel.ttl";
//        FileWriter defOut = new FileWriter(defaultModelFileName);
//        System.out.println("Here 1");
//        String inferredModelFileName = "infModel.ttl";
//        FileWriter infOut = new FileWriter(inferredModelFileName);

//        InfModel infModel = ModelFactory.createInfModel(reasoner, ontmodel);
//        System.out.println("Here 1");
//        try{
////            System.out.println("Here 2");
////            ontmodel.write(defOut,"TTL");
////            System.out.println("Here 3");
//            ontmodel.writeAll(infOut,"TTL");
//////            infModel.write(infOut,"TTL");
////            System.out.println("Here 4");
//        }
//        finally {
//            try {
////                defOut.close();
//                infOut.close();
//            }
//            catch (IOException closeExcpetion){
//
//            }
//        }

// Old Intentiion Attempt by running reasoning on a subset of the jena model
//    public ArrayList<String> getIntentions2() throws IOException {
//        ArrayList<String> intentions = new ArrayList<>();
//
//        knowledgeBase.begin(TxnType.READ);
//
//        // Describe subset model
//        String queryString = su.getPrefixes("camo", "rdfs", "DUL") + """
//                DESCRIBE ?task ?con camo:Intention camo:AchievableDesire camo:NonConflictingDesire camo:Desire camo:MentalAttitude camo:isAchievableWithCap DUL:isTaskDefinedIn ?cap ?capClass ?capClass2 WHERE {
//                ?task a camo:ProductionTask.
//                ?task camo:isAchievableWithCap ?cap.
//                ?task DUL:isTaskDefinedIn ?con.
//                ?cap a ?capClass.
//                ?capClass rdfs:subClassOf ?capClass2}
//                """;
//
//        Query query = QueryFactory.create(queryString);
//        FileWriter infOut = null;
//        try (QueryExecution queryExecution = QueryExecution.create(query, knowledgeBase)) {
//            Model modelToInferFrom = queryExecution.execDescribe();
//
//            // Write subset model to file for debugging
//            String modelToInferFromFileName = "infModel2.ttl";
//            infOut = new FileWriter(modelToInferFromFileName);
//            modelToInferFrom.write(infOut, "TTL");
//
//            InfModel infModel = ModelFactory.createInfModel(reasoner, modelToInferFrom);
//
//            Resource Intention = infModel.getResource("https://joedavid91.github.io/ontologies/camo/product#Intention");
//            printStatements(infModel, null, null, Intention);
//
////            Resource EngineBlock = modelToInferFrom.getResource("https://joedavid91.github.io/ontologies/camo/product#EngineBlock");
////            printStatements(modelToInferFrom, null,null,null);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        } finally {
//            infOut.close();
//            knowledgeBase.end();
//        }
//
//        // perform inference
//
//
//        return intentions;
//    }

//    public ArrayList<String> getIntentions3() throws IOException {
//        ArrayList<String> intentions = new ArrayList<>();
//
//        knowledgeBase.begin(TxnType.READ);
//
//        // Describe subset model
//        String queryString = su.getPrefixes("camo", "rdfs", "DUL") + """
//                DESCRIBE ?task ?con camo:Intention camo:AchievableDesire camo:NonConflictingDesire camo:Desire camo:MentalAttitude camo:isAchievableWithCap DUL:isTaskDefinedIn ?cap ?capClass ?capClass2 WHERE {
//                ?task a camo:ProductionTask.
//                ?task camo:isAchievableWithCap ?cap.
//                ?task DUL:isTaskDefinedIn ?con.
//                ?cap a ?capClass.
//                ?capClass rdfs:subClassOf ?capClass2}
//                """;
//
//        Query query = QueryFactory.create(queryString);
////        FileWriter infOut = null;
//        try (QueryExecution queryExecution = QueryExecution.create(query, knowledgeBase)) {
//            Model modelToInferFrom = queryExecution.execDescribe();
//
//            // Write subset model to file for debugging
////            String modelToInferFromFileName = "infModel2.ttl";
////            infOut = new FileWriter(modelToInferFromFileName);
////            modelToInferFrom.write(infOut, "TTL");
//
//            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RULE_INF, modelToInferFrom);;
////            InfModel infModel = ModelFactory.createInfModel(reasoner, modelToInferFrom);
//
//            Resource EngineBlock = ontModel.getResource("https://joedavid91.github.io/ontologies/camo/product#EngineBlock");
//            printStatements(ontModel, EngineBlock, null, null);
//
////          Resource EngineBlock = modelToInferFrom.getResource("https://joedavid91.github.io/ontologies/camo/product#EngineBlock");
////          printStatements(modelToInferFrom, null,null,null);
//        } finally {
////            infOut.close();
//            knowledgeBase.end();
//        }
//
//        // perform inference
//
//
//        return intentions;
//
//}