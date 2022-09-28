package Utils;

import java.util.ArrayList;
import java.util.Map;

public class ProcessTaskValidation {
    public String getProcessTask() {
        return processTask;
    }

    public ArrayList<String> getFailedActionCapClasses() {
        return failedActionCapClasses;
    }

    private String processTask;
    private ArrayList<String> failedActionCapClasses;
    private Map<String, ArrayList<String>> failedActionCapClassSHNameListMap;

    public Map<String, ArrayList<String>> getFailedActionCapClassSHNameListMap() {
        return failedActionCapClassSHNameListMap;
    }

    public ProcessTaskValidation(String processTask, ArrayList<String> failedActions, Map<String, ArrayList<String>> failedActionCapClassSHNameListMap) {
        this.processTask = processTask;
        this.failedActionCapClasses = failedActions;
        this.failedActionCapClassSHNameListMap = failedActionCapClassSHNameListMap;
    }
}
