package Utils;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ValidationReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TaskAndReport {
    private String task;


    private Model processDescriptionShapesModel;



    public String getTask() {
        return task;
    }

    public ValidationReport getReport() {
        return report;
    }

    public Map<String, ArrayList<String>> getFailedActionCapClassSHNameListMap(){
        Map<String, ArrayList<String>>  failedActionSHNameClassMap = new HashMap<>();
        // TODO: GET THE SOURCE SHAPE OF FAILED VALIDATION
        Model m = report.getModel();
        ArrayList<String> sourceShapes = new ArrayList<>();
        ArrayList<String> instShapeNames = new ArrayList<>();

        String queryString = new SPARQLUtils().getPrefixes("camo","sh") + "SELECT ?sourceShape WHERE { " +
                " ?report a sh:ValidationReport. " +
                " ?report sh:result/sh:sourceShape ?sourceShape. " +
                "}";
        Query query = QueryFactory.create(queryString);
        QueryExecution queryExecution = QueryExecutionFactory.create(query, m);
        ResultSet resultSet = queryExecution.execSelect();

        while(resultSet.hasNext()){
            QuerySolution soln = resultSet.nextSolution();
            Resource sourceShape = soln.getResource("sourceShape");
            System.out.println("Source Shape: " + sourceShape.getLocalName() + " appears to have failed.");
            sourceShapes.add(sourceShape.getLocalName());
        }

        for (String sourceShape: sourceShapes){
            //TODO: GET THE NAME OF THE INSTANCE PROPERTY SHAPE OF THE NODE SHAPE THAT CONTAINS THE SOURCESHAPE AS THE PROPERTY SHAPE
            queryString = new SPARQLUtils().getPrefixes("camo","sh") + "SELECT ?instanceShapeName ?instanceShapeValue WHERE { " +
                    "?nodeShape sh:property camo:"+sourceShape+ ". " +
                    "?nodeShape sh:targetClass ?targetClass. " +
                    "?nodeShape sh:property ?instancePropertyShape. " +
                    "?instancePropertyShape sh:value ?instanceShapeValue. " +
                    "?instancePropertyShape sh:name ?instanceShapeName. " +
                    " FILTER (?instanceShapeValue = ?targetClass)"+ // needed for checking only property instance shape
                    "}";

            query = QueryFactory.create(queryString);
            queryExecution = QueryExecutionFactory.create(query, processDescriptionShapesModel);
            resultSet = queryExecution.execSelect();

            Resource instanceShapeValue = null;

            while(resultSet.hasNext()){
                QuerySolution soln = resultSet.nextSolution();
                Literal instShapeName = soln.getLiteral("instanceShapeName");
                instanceShapeValue = soln.getResource("instanceShapeValue");
                System.out.println("Source Shape: " + sourceShape + " appears to be contained a nodeshape with instance propertyShape name " + instShapeName.toString());
                instShapeNames.add(instShapeName.toString());
            }
            assert instanceShapeValue != null;
            failedActionSHNameClassMap.put(instanceShapeValue.getLocalName(), instShapeNames);
        }

        return failedActionSHNameClassMap;

    }

    public ArrayList<String> getFailedActions() {
        Model m = report.getModel();
        ArrayList<String> failedCaps = new ArrayList<>();
        // you may have to group by focusNode  later if there are failures along multiple property paths
        String queryString = new SPARQLUtils().getPrefixes("sh") + """ 
                SELECT DISTINCT ?capInstance ?param WHERE{
                    ?report sh:result ?result.
                    ?result sh:focusNode ?capInstance.
                    ?result sh:resultPath ?param.
                }
                """;


        Query query = QueryFactory.create(queryString);
        QueryExecution queryExecution = QueryExecutionFactory.create(query, m);
        ResultSet resultSet = queryExecution.execSelect();


        while(resultSet.hasNext()){
            QuerySolution soln = resultSet.nextSolution();
            Resource capInstance = soln.getResource("capInstance");
            Resource param = soln.getResource("param");
            System.out.println("Capability Instance: " + capInstance.getLocalName() + ", along Parameter: " + param.getLocalName() + " failed.");
            failedCaps.add(capInstance.getLocalName());
        }

        return failedCaps;

    }

    private ValidationReport report;

    public TaskAndReport(String task, Model processDescriptionShapesModel, ValidationReport report) {
        this.processDescriptionShapesModel = processDescriptionShapesModel;
        this.task = task;
        this.report = report;
    }


}
