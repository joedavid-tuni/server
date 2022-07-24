package Utils;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
//import org.apache.jena.update.UpdateExecution;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;


import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SPARQLUtils {
    String pfx_camo = "https://joedavid91.github.io/ontologies/camo/product";
    String pfx_xsd =  "http://www.w3.org/2001/XMLSchema";

    String pfx_dul =  "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl";
    String pfx_sh = "http://www.w3.org/ns/shacl";
    String pfx_cm = "http://resourcedescription.tut.fi/ontology/capabilityModel";

    String pfx_rdfs = "http://www.w3.org/2000/01/rdf-schema";
    String separator = "#";

    Map<String, String> prefixMap = new HashMap<>();

    public SPARQLUtils() {
        // there is Jena utility functions for this
        prefixMap.put("camo", pfx_camo);
        prefixMap.put("xsd", pfx_xsd);
        prefixMap.put("DUL", pfx_dul);
        prefixMap.put("sh", pfx_sh);
        prefixMap.put("cm", pfx_cm);
        prefixMap.put("rdfs", pfx_rdfs);
    }

    String getPrefixes(String... prefixes){
        StringBuilder str = new StringBuilder("");


        for (var prefix: prefixes){
            if (prefixMap.containsKey(prefix)){
                str.append("PREFIX ").append(prefix).append(":<").append(prefixMap.get(prefix)).append(separator).append("> ");
            }
        }
        return str.toString();
    }

    String getDelInsClause(ArrayList<String> sources, ArrayList<String> dests){

        StringBuilder delClause = new StringBuilder("");
        for(String source: sources){
            delClause.append("camo:").append(source).append(" camo:hasToken \"true\"^^xsd:boolean. " );
        }

        for(String dest: dests){
            delClause.append("camo:").append(dest).append(" camo:hasToken \"false\"^^xsd:boolean. " );
        }

        StringBuilder insClause = new StringBuilder("");
        for(String dest: dests){
            insClause.append("camo:").append(dest).append(" camo:hasToken \"true\"^^xsd:boolean. " );
        }

        for(String source: sources){
            insClause.append("camo:").append(source).append(" camo:hasToken \"false\"^^xsd:boolean. " );
        }

        StringBuilder delins = new StringBuilder("");

        delins.append("DELETE {").append(delClause).append("} INSERT {").append(insClause).append("}");

        return delins.toString();
    }

    String stripNamespace(String prefix, String resource){
        String[] temp = resource.split(separator);
        return temp[temp.length-1];

    }

//    public static void main(String[] args){
//        ArrayList<String> listS = new ArrayList<String>();
////        listS.add("A");
////        listS.add("B");
////
////
//        ArrayList<String> listD = new ArrayList<String>();
////        listD.add("C");
////        listD.add("D");
//
//
//        System.out.println(getDelInsTokenString(listS, listD));
//
//    }

}
