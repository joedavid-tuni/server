package SocketMessage;

public class PrimitveTask {

    public PrimitveTask(String name, String operatorRole, String robotRole, String description, String modality, int order, String context) {
        this.name = name;
        this.operatorRole = operatorRole;
        this.robotRole = robotRole;
        this.description = description;
        this.modality = modality;
        this.order = order;
        this.context = context;
    }

    String name;
    String operatorRole;
    String robotRole;
    String description;

    public String getContext() {
        return context;
    }

    String modality;
    String context;

    public Integer getOrder() {
        return order;
    }

    Integer order;

    public String getName() {
        return name;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public String getRobotRole() {
        return robotRole;
    }

    public String getDescription() {
        return description;
    }

    public String getModality() {
        return modality;
    }




}
