package Utils;

import com.github.owlcs.ontapi.OntManagers;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.OntologyManager;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.iterator.Iter;
//import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
//import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.engine.Targets;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.update.UpdateExecution;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.util.PrintUtil;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;



public class KnowledgeBase {

    private Dataset knowledgeBase;

    private int i = 0;
    private final SPARQLUtils su = new SPARQLUtils();

//    private Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
//    private Reasoner reasoner = PelletReasonerFactory.theInstance().create();
//    private Reasoner reasoner2 = PelletReasonerFactory.theInstance().create();

//    FusekiServer fusekiServer;

    public KnowledgeBase(String gPath, String dBaseDir) throws IOException {
//        String dBaseDir = "/home/robolab/Documents/Dataset";
        File dir = new File(dBaseDir);
        FileUtils.cleanDirectory(dir);

        this.knowledgeBase = TDBFactory.createDataset(dBaseDir);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        System.out.println("Going to read");
        ontModel.read(gPath, "RDF/XML");
        System.out.println("Finished Reading");

//        reasoner.bind(ontModel.getGraph());
//
        this.knowledgeBase.getDefaultModel().add(ontModel);

    }

    public Model getModel(){
        return this.knowledgeBase.getDefaultModel();
    }

//    public void initFuseki(){
//        fusekiServer = FusekiServer.create()
//                .enableCors(true)
//                .port(3003)
//                .add("/ds", knowledgeBase, true)
//                .build();
//        System.out.println("Starting Robot KB");
//        fusekiServer.start();
//    }
//
//    private void stopFusek(){
//        System.out.println("Stopping Fuseki");
//        fusekiServer.stop();
//    }

   public ArrayList<String> getCurrentProductionTasks(){

        //returns all enabled transitions
        ArrayList<String> currentProductionTasks= new ArrayList<String>();
        knowledgeBase.begin(ReadWrite.READ);

        try {
            // get all transitions with atleast one marked place
            String qs1 = su.getPrefixes("camo","xsd") + " SELECT DISTINCT ?prodTasks WHERE {?prodState a camo:ProductState. " +
                    "?prodState camo:hasToken \"true\"^^xsd:boolean. ?prodState camo:includesActivities ?prodTasks. } LIMIT 10";
            try(QueryExecution qExec = QueryExecution.create(qs1,knowledgeBase)){

                ResultSet rs = qExec.execSelect();

                while(rs.hasNext()){
                    QuerySolution sol = rs.nextSolution();
                    String prodTask = sol.getResource("?prodTasks").getLocalName();

                    //Describe places of the transtiion
                    String descQuery = su.getPrefixes("camo") + "DESCRIBE ?prodState camo:hasToken camo:includesActivities WHERE {?prodState camo:includesActivities camo:"+prodTask+"}";
                    QueryExecution qExec2 = QueryExecution.create(descQuery, knowledgeBase);
                    Model stateModel = qExec2.execDescribe();

                    //load shape
                    Graph shapeGraph = RDFDataMgr.loadGraph("/home/robolab/Documents/shapes/isTransitionEnabled.ttl", Lang.TTL);
                    Shapes shape = Shapes.parse(shapeGraph);

                    ValidationReport report = ShaclValidator.get().validate(shape, stateModel.getGraph());
                    System.out.println("Checking Enabled Transitions");
                    ShLib.printReport(report);

                    if (report.conforms()){
                        currentProductionTasks.add(prodTask);
                    }


                    //validate

//                    System.out.println(currentTasks);
                }

//                System.out.println(currentTasks.size());
            }
        }
        finally {
            knowledgeBase.end();
        }

        return  currentProductionTasks;

    }

    public String getIDofTask(String task){


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
        }
            finally {
                knowledgeBase.end();
            }


        return id;
    }

    public ArrayList<String> getMessageWithConvID(String conversation_id) {
//        if (!knowledgeBase.isInTransaction())  knowledgeBase.begin(ReadWrite.READ);
        ArrayList<String>  ids =  new ArrayList<String>();

//        try {
            //1. Get the Shape defining the capability required.
            String query = su.getPrefixes("camo","xsd") + " SELECT ?msgID WHERE { " +
                    "?msgID a camo:ACLMessage. " +
                    "?msgID camo:conversation_id \""+ conversation_id +"\"^^xsd:string.}";
            // assumes only one message exists
            Query _query = QueryFactory.create(query);
            String msg;
            try (QueryExecution qExec = QueryExecution.create(_query, knowledgeBase)) {
                ResultSet rs = qExec.execSelect();
                while(rs.hasNext()) {
                    QuerySolution soln = rs.nextSolution();
                    msg = soln.getResource("?msgID").getURI(); // for some reason you cant get the local name directly
                    String[] msgs = msg.split("#");
                    msg = msgs[msgs.length-1];
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

    public String insertACLMessage(ACLMessage msg){


        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String formattedDate = myDateObj.format(myFormatObj);


        String sender = msg.getSender().getLocalName();
        StringBuilder receiver = new StringBuilder("");
        jade.util.leap.Iterator it = msg.getAllReceiver();
        while(it.hasNext()){
            AID r = (AID) it.next();
            receiver.append(r.getLocalName());
        }

        String content = msg.getContent();
        String in_reply_to = msg.getInReplyTo();
        String reply_with = msg.getReplyWith();
        String protocol = msg.getProtocol();
        String performative = ACLMessage.getPerformative(msg.getPerformative()); //not verified
        String conversation_id = msg.getConversationId();

        String query = su.getPrefixes("camo","xsd") + "INSERT DATA{camo:"+formattedDate+" a camo:ACLMessage. " +
                "camo:"+formattedDate+" camo:sender \""+sender+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:receiver \""+receiver+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:content \""+content+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:in_reply_to \""+in_reply_to+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:reply_with \""+reply_with+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:protocol \""+protocol+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:performative \""+performative+"\"^^xsd:string. " +
                "camo:"+formattedDate+" camo:conversation_id \""+conversation_id+"\"^^xsd:string. " +
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
        String  name = String.valueOf((int)( Math.random() *10000000));
        String query = su.getPrefixes("camo","DUL") + " INSERT DATA {camo:"+name+ " a DUL:Contract. " +
                "camo:"+name+" DUL:hasPart camo:"+msgName+"}";

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

    public void addMessagesToContract(ArrayList<String> msgNames) {

        knowledgeBase.begin(TxnType.WRITE);

        // Generate the Contract Name Randomly
        String  conName = String.valueOf((int)( Math.random() *10000000));

        // Get which task the message is about using the conversation ID

        String qs = su.getPrefixes("camo") + "SELECT ?convID WHERE {}";

        // Insert that the Task is defined in the contract (Can be combined with below)


        //  Add messages to Contract
        String query = su.getPrefixes("camo","DUL") + " INSERT DATA {camo:"+conName+" a DUL:Contract.}";
        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();


        for (String msg: msgNames){
            query = su.getPrefixes("camo") + " INSERT DATA {camo:"+conName+" camo:hasPart camo:"+msg+"}";
            uReq = UpdateFactory.create(query);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
        }

        knowledgeBase.commit();
        knowledgeBase.end();
    }

    public String addConversationToContract(String convID){

        knowledgeBase.begin(TxnType.WRITE);

        String  conName = String.valueOf((int)( Math.random() *10000000));

        // Get which task the message is about using the conversation ID

        String[] taskStringArr = convID.split("_");
        String task = taskStringArr[taskStringArr.length-1];

        // get all messages with the convID
        ArrayList<String> msgs = getMessageWithConvID(convID);

        // Make a Contract
        String query = su.getPrefixes("camo","DUL") + " INSERT DATA {camo:"+conName+" a DUL:Contract.}";
        UpdateRequest uReq = UpdateFactory.create(query);
        UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

        //Add Messages to contract
        for (String msg: msgs){
            query = su.getPrefixes("camo") + " INSERT DATA {camo:"+conName+" camo:hasPart camo:"+msg+"}";
            uReq = UpdateFactory.create(query);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();
        }

        // Add task defined in contract
        String query1 = su.getPrefixes("camo","DUL") + " INSERT DATA {camo:"+task+" DUL:isTaskDefinedIn camo:"+conName+".}";
        UpdateRequest uReq1 = UpdateFactory.create(query1);
        UpdateExecution.dataset(knowledgeBase).update(uReq1).execute();


        knowledgeBase.commit();
        knowledgeBase.end();

        // Insert that the Task is defined in the contract (Can be combined with below)
      return convID;
    }

    public boolean checkCapabilityOfProductionTask(String productionTask){

        ArrayList<String> processTasks = getProcessTasksForProductionTask(productionTask);
        System.out.println("Process tasks for " + productionTask + ": " + processTasks);


        String shapeDir="";
        Resource capClass = null;
        knowledgeBase.begin(TxnType.READ_PROMOTE);
        boolean isCapable = true;
        for (String processTask : processTasks) {
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
                try(QueryExecution qExec = QueryExecution.create(queryString,knowledgeBase)){
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

                while (resultSet.hasNext()){
                    QuerySolution soln = resultSet.nextSolution();
                    capClass = soln.getResource("targetClass");
                    System.out.println("Required Capability: " + capClass.getLocalName());
                }

                //     2b. Describe the instances of the Target Capability (if any-important) to be run the validation against
                ValidationReport report = null;

                if(capClass != null) {
                    String describeCapQuery = su.getPrefixes("cm") + " DESCRIBE ?cap  WHERE {" + "?cap a cm:" + capClass.getLocalName() + ".}";
                    Query query1 = QueryFactory.create(describeCapQuery);
                    queryExecution = QueryExecution.create(query1, knowledgeBase);
                    Model agentCapabilityModel = queryExecution.execDescribe();
                    Graph agentCapabilityGraph = agentCapabilityModel.getGraph();

                    //3. Validate the Agent Capability Graph against the shapes the defines the capability required by the said task.
                    report = ShaclValidator.get().validate(shapes, agentCapabilityGraph);
                    ShLib.printReport(report);

//                    if(!report.conforms()){
                    isCapable = report.conforms() && isCapable; //not sure if this works but no errors are evident
//                    }

                }
                else {
                    //TODO: for now return false, later see what exactly does not conform
                    System.out.println("No Target Capabilities Found to validate");

                }


            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        if(isCapable){
            // update the specific task with that it isAchievableWithCap that cap
            //     get the instance
            System.out.println("Is Capable");
            assert capClass != null;
            String qs = su.getPrefixes("cm", "camo") + " SELECT ?capInst WHERE {" + "?capInst a cm:" + capClass.getLocalName() + ".}";

            Query _query = QueryFactory.create(qs);
            QueryExecution _queryExecution = QueryExecutionFactory.create(_query, knowledgeBase);
            ResultSet _resultSet = _queryExecution.execSelect();

            Resource capInst = null; //we assume that there is only one instance of each class of capability

            while (_resultSet.hasNext()){
                QuerySolution soln = _resultSet.nextSolution();
                capInst = soln.getResource("capInst");
                System.out.println("Required Capability Inst: " + capInst.getLocalName());
            }

            // update
            String _qs = su.getPrefixes("camo","cm") + " INSERT DATA {camo:"+ productionTask +" camo:isAchievableWithCap camo:"+capInst.getLocalName()+".}";

            UpdateRequest uReq = UpdateFactory.create(_qs);
            UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

            knowledgeBase.commit();


        }
        knowledgeBase.end();


        return isCapable;

    }

    public ArrayList<String> getProcessTasksForProductionTask(String productionTask){

        ArrayList<String> processTasks= new ArrayList<String>();
        knowledgeBase.begin(ReadWrite.READ);
        try {
            String qs1 = su.getPrefixes("camo","xsd","DUL") + " SELECT DISTINCT ?processTasks WHERE {camo:" + productionTask + " a camo:ProductionTask. " +
                    "camo:" + productionTask + " camo:hasProcessPlan ?plan. ?plan DUL:definesTask ?processTasks. }";
            try(QueryExecution qExec = QueryExecution.create(qs1,knowledgeBase)){

                ResultSet rs = qExec.execSelect();

                while(rs.hasNext()){
                    QuerySolution sol = rs.nextSolution();
                    processTasks.add(sol.getResource("?processTasks").getLocalName());
//                    System.out.println(currentTasks);
                }

//                System.out.println(currentTasks.size());
            }
        }
        finally {
            knowledgeBase.end();
        }

        return processTasks;
    }

    public boolean updateTaskExecution(String task){
        // updates the PN with that results a fired transition

        boolean isEnabled;

        //1. Check if enabled
        knowledgeBase.begin(TxnType.READ_PROMOTE);

        try {
            String query = su.getPrefixes("camo","xsd") + "ASK WHERE {?s camo:includesActivities camo:"+task+". " +
                    "?s camo:hasToken \"true\"^^xsd:boolean. }";

            try(QueryExecution qExec = QueryExecution.create(query, knowledgeBase)){
                isEnabled = qExec.execAsk();

                if(isEnabled){

                    //2. Get preceding and successive places as ArrayLists
                    System.out.println(task + "is enabled");
                    String sourceDestQuery= new SPARQLUtils().getPrefixes("camo") + " SELECT * WHERE {{?source camo:includesActivities camo:"+task+
                            ".} UNION {camo:"+task+" camo:leadsTo ?dest.}}";

                    ArrayList<String> sources = new ArrayList<String>();
                    ArrayList<String> dests = new ArrayList<String>();

                    try(QueryExecution qExec2 = QueryExecution.create(sourceDestQuery,knowledgeBase)){

                        ResultSet rs2 = qExec2.execSelect();

                        while(rs2.hasNext()){
                            QuerySolution sol = rs2.next();

                            Resource source = sol.getResource("source");
                            if(source !=null)  sources.add(source.getLocalName());

                            Resource dest = sol.getResource("dest");
                            if(dest!= null) dests.add(dest.getLocalName());
                        }

                        System.out.println(sources);
                        System.out.println(dests);
                    }

                    // 3. Build DELETE INSERT WHERE Query
                    String whereClause = " WHERE {?places camo:hasToken ?hasSourceToken.}";

                    String delInsWhereClause = su.getPrefixes("camo","xsd") + su.getDelInsClause(sources,dests) + whereClause;


                    //4. Perform update Query
                    UpdateRequest uReq = UpdateFactory.create(delInsWhereClause);
                    UpdateExecution.dataset(knowledgeBase).update(uReq).execute();

                    knowledgeBase.commit();

                }
                else{
                    System.out.println("Task to be executed not current task");
                    return false;
                }

            };
        }
        finally {
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
        String query = su.getPrefixes("camo") + " SELECT ?inst WHERE {?inst a camo:"+className+"}";
        knowledgeBase.begin(TxnType.READ);

        try(QueryExecution qExec = QueryExecution.create(query,knowledgeBase)){
            ResultSet rs = qExec.execSelect();

            while(rs.hasNext()) {
                QuerySolution sol = rs.next();
                String source = sol.getResource("inst").getLocalName();
                instances.add(source);
            }
        } finally {
            knowledgeBase.end();
        }

        return instances;
    }


    public OWLOntology getOWLOntology (OntModel ontmodel) {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        try (PipedInputStream is = new PipedInputStream(); PipedOutputStream os = new PipedOutputStream(is)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {knowledgeBase.begin(TxnType.WRITE);
                        // save jena model to os in RDF/XML format
                        ontmodel.write(os, "RDF/XML");
//                        ontology.getOWLOntologyManager().saveOntology(ontology, new TurtleDocumentFormat(), os);
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    finally {
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

    public void printStatements(Model m, Resource s, Property p, Resource o) {
        for (StmtIterator i = m.listStatements(s,p,o); i.hasNext(); ) {
            Statement stmt = i.nextStatement();
            System.out.println(" - " + PrintUtil.print(stmt));
        }
    }

    public ArrayList<String> getIntentions() throws IOException, OWLOntologyStorageException {
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

//        OntologyManager manager = OntManagers.createManager();
//        Ontology ontology = manager.addOntology(ontmodel.getGraph());
////        Ontology ontology = manager.addOntology(knowledgeBase.asDatasetGraph().getDefaultGraph());
////        OWLReasonerFactory reasonerFactory1 = new StructuralReasonerFactory();
        OWLReasonerFactory reasonerFactory2 = new org.semanticweb.HermiT.Reasoner.ReasonerFactory();

//        //DEBUG: INSPECT THE ONTOLOGY BY OUTPUTTING TO FILE (OWLAPI)
//        File file = new File("currModel"+String.valueOf(++i)+".owl");
//        System.out.println("Document IRI" + manager.getOntologyDocumentIRI(ontology));
//        manager.saveOntology(ontology, IRI.create(file.toURI()));
//
//        OWLReasoner reasoner1 = reasonerFactory2.createReasoner(ontology);
        OWLOntology ontology = getOWLOntology(ontModel); // using piped streams

        knowledgeBase.begin(TxnType.READ);

        OWLReasoner reasoner = reasonerFactory2.createReasoner(ontology);

        reasoner.precomputeInferences();


        boolean consistent = reasoner.isConsistent();
        System.out.println("Consistent: " + consistent);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLClass Intention  = factory.getOWLClass("https://joedavid91.github.io/ontologies/camo/product#Intention");
        NodeSet<OWLNamedIndividual> intentionNodeSet = reasoner.getInstances(Intention,true);
        Set<OWLNamedIndividual> intentions = intentionNodeSet.getFlattened();

        for (OWLNamedIndividual intention: intentions){
            instances.add(intention.toString());
        }

        knowledgeBase.end();
        return instances;
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