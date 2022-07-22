package SocketMessage;

public class CanvasMessage {
    public String getType() {
        return type;
    }

//    public double[] getValues() {
//        return values;
//    }

    private String type;

    public void setType(String type) {
        this.type = type;
    }

//    public void setValues(double[] values) {
//        this.values = values;
//    }

    public VBoundingBox getBbox() {
        return values;
    }

    public void setBbox(VBoundingBox values) {
        this.values = values;
    }

    //    private double[] values;
    private VBoundingBox values;

    public CanvasMessage(String type,  VBoundingBox values) {
        this.type = type;
        this.values = values;
    }
}



