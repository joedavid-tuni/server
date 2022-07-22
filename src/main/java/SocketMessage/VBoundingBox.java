package SocketMessage;

import java.awt.*;

public class VBoundingBox {

    private String color;
    private String name;
    private String mode;
    private Point[] points;

    public VBoundingBox(String color, String name, String mode, Point[] points) {
        this.color = color;
        this.name = name;
        this.mode = mode;
        this.points = points;
    }

}
