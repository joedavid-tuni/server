import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


import SocketMessage.*;

import com.google.gson.JsonParser;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.tools.sniffer.Sniffer;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jdk.swing.interop.SwingInterOpUtils;
import org.ejml.simple.SimpleMatrix;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;



import com.google.gson.Gson;



public class SimpleServer extends WebSocketServer {

    private double windowWidth = -99.9;
    private double windowHeight= -99.9;
    private double pageWidth= -99.9;
    private double pageHeight= -99.9;
    private double screenWidth= -99.9;
    private double screenHeight= -99.9;
    private double pageX= -99.9;
    private double pageY= -99.9;
    private double screenX= -99.9;
    private double screenY= -99.9;
    private double viewportWidth= -99.9;
    private double viewportHeight= -99.9;
    private double cssDensity = -99.9;
    private static WebSocket UIconn;
    private static Map<String, WebSocket> sessionMap = new HashMap<>();

    private static final int PORT = 8887;
    private static final String HOST = "localhost";

    private static AgentController RobotAgent;

    private static AgentController IntrospectorAgent;
    private static AgentController HumanAgent;
    private static AgentController rma;

    private static AgentController snifferAgent;

    // Projector Intrinsic Matrix obtained from camera calibration (KinectCalibration => ProCalibration.cpp)
    private SimpleMatrix K = new SimpleMatrix(
            new double[][]{
                    new double[] {1533.7076, 0 , 918.1004},
                    new double[] {0, 1584.4318, 367.31174},
                    new double[] {0, 0 ,1}
            }
    );

    // Revised Projector Intrinsic Matrix obtained from camera calibration (KinectCalibration => ProCalibration.cpp)
    // after reordering image points for increasing y values (checking possibility for a mistake)
    private SimpleMatrix K_rev = new SimpleMatrix(
            new double[][]{
                    new double[] {1804.52020, 0 , 966.56671},
                    new double[] {0, 1896.64901, 415.12267},
                    new double[] {0, 0 ,1}
            }
    );

    // Rigid Body Transformation  (KinectCalibration => PnP.cpp)
    private SimpleMatrix RT = new SimpleMatrix(
            new double[][]{
                    new double[] {-0.99973911, 0.0023694111 , 0.022717459, 55.749138},
                    new double[] {0.0073618814, 0.97495246, 0.22229147, 11.060492},
                    new double[] {-0.021621743, 0.22240072, -0.97471553, 116.04071},
            }
    );

    // Revised Rigid Body Transformation  (KinectCalibration => PnP.cpp)
    // after reordering image points for increasing y values (checking possibility for a mistake)
    private SimpleMatrix RT_rev = new SimpleMatrix(
            new double[][]{
                    new double[] {-0.99944794, -0.0016805725 , 0.033181749, 51.920116},
                    new double[] {0.0069724466, 0.96587044, 0.25893188, 4.2781181},
                    new double[] {-0.032484423, 0.25902027, -0.96532547, 135.90219},
            }
    );

    // Rigid Body Transformation  ( without inverting the solvePnP resultKinectCalibration => PnP.cpp)
    private SimpleMatrix _RT = new SimpleMatrix(
            new double[][]{
                    new double[] {-0.99944794, 0.0069724466 , -0.032484423, 56.276325},
                    new double[] {-0.0016805725, 0.96587044, 0.25902027, -39.246273},
                    new double[] {0.033181749, 0.25893188, -0.96532547, 128.3593},
            }
    );

    // Approximate transformation of the corner of the table (origin) to the bottom right corner of the engine block
    private SimpleMatrix RT2 = new SimpleMatrix(
            new double[][]{
                    new double[] {1, 0, 0, 31.02},
                    new double[] {0, 1, 0, 39.78},
                    new double[] {0, 0, 1, 0},
                    new double[] {0, 0, 0, 1},
            }
    );


    // Rigid Body Transformation  (KinectCalibration => PnP.cpp) (after rotating X and Z by 180deg)
//    private SimpleMatrix _RT = new SimpleMatrix(
//            new double[][]{
//                    new double[] {0.99973911, 0.0023694111 , -0.022717459, 55.749138},
//                    new double[] {-0.0073618814, 0.97495246, -0.22229147, 11.060492},
//                    new double[] {0.021621743, 0.22240072, 0.97471553, 116.04071},
//            }
//    );

    // Rigid Body Transformation  (KinectCalibration => PnP.cpp) (after rotating  Z by 90deg)
//    private SimpleMatrix _RT = new SimpleMatrix(
//            new double[][]{
//                    new double[] {0.0023694111 ,0.99973911,  0.022717459, 55.749138},
//                    new double[] {0.97495246, -0.0073618814, 0.22229147, 11.060492},
//                    new double[] {0.22240072, 0.021621743,  -0.97471553, 116.04071},
//            }
//    );
////     with R not transposed in PnP.cpp (R=R.t())
//    private SimpleMatrix _RT = new SimpleMatrix(
//            new double[][]{
//                    new double[] {-0.99973911, 0.0073618814 , -0.021621743, 60.782368},
//                    new double[] {0.0023694111, 0.97495246, 0.22240072, 11.338917},
//                    new double[] {0.022717459, 0.22229147, -0.97471553, 113.4578},
//            }
//    );

    Gson gson = new Gson();


    public SimpleServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        //todo: STORE THE CONN OBJECT OF THE FRONT END as many agents also end up communicating, you will need to communicate to the browser
        conn.send("Welcome to the server!"); //This method sends a message to the new client
//        broadcast( "new connection: " + handshake.getResourceDescriptor() ); //This method sends a message to all clients connected
        System.out.println("[Server] New Connection" +  conn.getRemoteSocketAddress() );
        System.out.println("");
        UIconn = conn;


//        SimpleMatrix RTT = RT.transpose();
        SimpleMatrix P = K_rev.mult(_RT);
//        System.out.println("P: ");
//        P.print();

        // corners from nx of ROCKER ARM 8

        SimpleMatrix C1 = createMatrixFromCoords(new double[] {3.0841125573636937, 11.97898405362822, 14.702226388166895});
        SimpleMatrix C2 = createMatrixFromCoords(new double[] {7.845773134452702, 8.459848835947388, 14.702226388166895});
        SimpleMatrix C3 = createMatrixFromCoords(new double[] {7.845773134452702, 8.459848835947388, 16.548481872774278});
        SimpleMatrix C4 = createMatrixFromCoords(new double[] {3.0841125573636937, 11.97898405362822, 16.548481872774278});
        SimpleMatrix C5 = createMatrixFromCoords(new double[] {3.982214390964783, 13.194184900070238, 14.702226388166895});
        SimpleMatrix C6 = createMatrixFromCoords(new double[] {8.743874968053791, 9.675049682389405, 14.702226388166895});
        SimpleMatrix C7 = createMatrixFromCoords(new double[] {8.743874968053791, 9.675049682389405, 16.548481872774278});
        SimpleMatrix C8 = createMatrixFromCoords(new double[] {3.982214390964783, 13.194184900070238, 16.548481872774278});

        SimpleMatrix C11 = createMatrixFromCoords(new double[] {6.177483704065121, 16.04318311490303, 14.7022263881669});
        SimpleMatrix C21 = createMatrixFromCoords(new double[] {10.953137658046712, 12.513706003960446, 14.7022263881669});
        SimpleMatrix C31 = createMatrixFromCoords(new double[] {10.953137658046712, 12.513706003960446, 16.548481872774283});
        SimpleMatrix C41 = createMatrixFromCoords(new double[] {6.177483704065121, 16.04318311490303, 16.548481872774283});
        SimpleMatrix C51 = createMatrixFromCoords(new double[] {7.123120344762663, 17.322702210855186, 14.7022263881669});
        SimpleMatrix C61 = createMatrixFromCoords(new double[] {11.898774298744254, 13.793225099912604, 14.7022263881669});
        SimpleMatrix C71 = createMatrixFromCoords(new double[] {11.898774298744254, 13.793225099912604, 16.548481872774283});
        SimpleMatrix C81 = createMatrixFromCoords(new double[] {7.123120344762663, 17.322702210855186, 16.548481872774283});

        SimpleMatrix[] BBoxCoords = {C1,C2,C3,C4,C5,C6,C7,C8};
        SimpleMatrix[] BBoxCoords1 = {C11,C21,C31,C41,C51,C61,C71,C81};

        Point[] pxPoints = pixelPointsFromNXBBBoxCoors(BBoxCoords, P);
        Point[] pxPoints1 = pixelPointsFromNXBBBoxCoors(BBoxCoords1, P);


//        System.out.println(Arrays.toString(pxPoints));
//        System.out.println(Arrays.toString(pxPoints1));

////        P.print();
        // hardcoded values to be sent to the UI
//        Point _p1 = new Point(1219,1283);
//        Point _p2 = new Point(1156,1242);
//        Point _p3 = new Point(1160,1262);
//        Point _p4 = new Point(1225,1304);
//        Point _p5 = new Point(1206,1298);
//        Point _p6 = new Point(1143,1257);
//        Point _p7 = new Point(1147,1277);
//        Point _p8 = new Point(1211,1319);


//        Point[] _points = {_p1,_p2,_p3,_p4,_p5,_p6,_p7,_p8};

        VBoundingBox values = new VBoundingBox("#3becd7", "polygon_2","blinking", pxPoints);
        CanvasMessage s1 = new CanvasMessage(
                "canvas-polygon-drawing", values
        );
//        System.out.println("Stringified:" + gson.toJson(s1));
        conn.send(gson.toJson(s1));

        VBoundingBox values1 = new VBoundingBox("#3becd8", "polygon_3","blinking", pxPoints1);
        CanvasMessage s11 = new CanvasMessage(
                "canvas-polygon-drawing", values1
        );

//        System.out.println("Stringified:" + gson.toJson(s11));
//        conn.send(gson.toJson(s11));

//        conn.send("{\"type\": \"requestScreenParams\", \"values\": \"null\" }");
//        conn.send("{\"type\": \"canvas-polygon-drawing\", \"values\": {\"color\":\"#3becd7\",\"points\":[{\"x\":717,\"y\":347},{\"x\":542,\"y\":383},{\"x\":688,\"y\":373},{\"x\":671,\"y\":323},{\"x\":772,\"y\":318},{\"x\":541,\"y\":425},{\"x\":614,\"y\":427},{\"x\":782,\"y\":291}],\"name\":\"polygon_2\",\"mode\":\"blinking\"}}");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[Server] Received message from "	+ conn.getRemoteSocketAddress() + ": " + message);

        GeneralMessage msg = gson.fromJson(message, GeneralMessage.class);
//        System.out.println("[Server] " + msg.getType());

        // Type of Websocket Messages that initiates communication between agents
        if(msg.getType().equals("agent_communication")){
//            System.out.println(msg.getValue().toString());


            VA2ACommunication a2aMsg = gson.fromJson(msg.getValue().toString(), VA2ACommunication.class);
//            System.out.println( a2aMsg.getSender());
//            System.out.println( a2aMsg.getReceiver());
//            System.out.println( a2aMsg.getContext());
//            System.out.println( a2aMsg.getCommunicativeAct());


//            O2A object= new O2A(msg.getType(), a2aMsg.getSender(), a2aMsg.getReceiver(), a2aMsg.getContext(),a2aMsg.getPayload());
            try {
                 if (Objects.equals(a2aMsg.getSender(), "Operator")) { // if operator is the sender this has to be from the UI
                     HumanAgent.putO2AObject(msg,false);
                }

                else if (Objects.equals(a2aMsg.getReceiver(), "Operator")){ //if operator is the receiver this has to be a message from some
                    WebSocket uiConn = sessionMap.get("InteractionUI"); //  agent to the JADE human agent that has to be displayed on the UI
                     UIMessage uiMessage = new UIMessage(a2aMsg.getCommunicativeAct(), a2aMsg.getSender(), a2aMsg.getPayload(),a2aMsg.getInteractionProtocol(), a2aMsg.getConversation_id(), a2aMsg.getReply_with(), a2aMsg.getIn_reply_to(),"");
                    GeneralMessage gm = new GeneralMessage("im-message", new JsonParser().parse(gson.toJson(uiMessage)).getAsJsonObject());
                    uiConn.send(gson.toJson(gm));

                }
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
        }

        else if (msg.getType().equals("identification")){
            VA2ACommunication val = gson.fromJson(msg.getValue().toString(), VA2ACommunication.class);
            if(!sessionMap.containsKey(val.getSender())){

                // store all session in map here
                if(Objects.equals(val.getSender(), "InteractionUI")){
                    System.out.println("Identification web socket message received from Interaction UI");
                    sessionMap.put(val.getSender(), conn);
                    try {
//                        HumanAgent.putO2AObject(UIObj,false);
                        RobotAgent.putO2AObject(conn, false);
                        HumanAgent.putO2AObject(conn, false);
                    } catch (StaleProxyException e) {
                        throw new RuntimeException(e);
                    }
                }
                // TODO: else if for Robot and Operator
            }
        }

        else if (msg.getType().equals("process_description")){

            try {
                HumanAgent.putO2AObject(msg,false);
            } catch (StaleProxyException e) {
                throw new RuntimeException(e);
            }

        }

        //        AgentMessage params = gson.fromJson(message, AgentMessage.class);



        // Receiving data from the CS panel
//         value params.getValues()[0] = Receiver
//         value params.getValues()[1] = CA
        // value params.getValues()[2] = Tasks
//        if (params.getType().equals("taskList")) {
//            conn.send("{\"type\": \"im-message\", \"class\": \"class 1\", \"ca\": \"ca #1\", \"sender\": \"Sender #1\", \"message\": \"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.\" }");
//            System.out.print("Receiver: " + params.getValues()[0] + " CA: " + params.getValues()[1] + " Selected tasks: " + params.getValues()[2]);
//        }

        // Only for testing the function that updates a single tree element
//        if (params.getType().equals("getResponse")) {
//            conn.send("{\"type\": \"tree-status-change\", \"key\": \"1\", \"state\": \"attention needed\", \"color\": null, \"mode\": null}");
//        }

//        if(params.getType().equals("agent_communication")){
//
//            O2A object= new O2A(params.getType(), params.getSender(), params.getReceiver(), params.getActivity());
//            try {
//                HumanAgent.putO2AObject(object,false);
//            } catch (StaleProxyException e) {
//                e.printStackTrace();
//            }
//        }

        // When selecting an option on IM panel, it will return the id of the selected button
//        if (params.getType().equals("option-selection")) {
//            System.out.println("Option " + params.getValues()[0] + " selected");
//        }

        // Screen parameters
//        if (params.getType().equals("currentScreenParameters")) {
//            System.out.println("[Current Screen Parameters] Size" + params.getValues().length);
//            System.out.println(params.getValues());
//
//            this.windowWidth = params.getValues()[0];
//            this.windowHeight= params.getValues()[1];
//            this.pageWidth= params.getValues()[2];
//            this.pageHeight= params.getValues()[3];
//            this.screenWidth= params.getValues()[4];
//            this.screenHeight= params.getValues()[5];
//            this.pageX= params.getValues()[6];
//            this.pageY= params.getValues()[7];
//            this.screenX= params.getValues()[8];
//            this.screenY= params.getValues()[9];
//            this.viewportWidth= params.getValues()[10];
//            this.viewportHeight= params.getValues()[11];
//            this.cssDensity = this.screenWidth / this.viewportWidth;
//        }


//        if (params.type= "currentScreenParameters"){
//            System.out.println("excpected message received");
//        }


    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        System.out.println("received ByteBuffer from "	+ conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("an error occurred on connection " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    public SimpleMatrix createMatrixFromCoords(double[] arr){
        // Creates a 4x1 matrix from an array of 3 coords (homogenous 1 extra coord)

        SimpleMatrix M = new SimpleMatrix(
                new double[][]{
                        new double[] {arr[0]},
                        new double[] {arr[1]},
                        new double[] {arr[2]},
                        new double[] {1}
                }
        );

        return M;
    }

    public Point[] pixelPointsFromNXBBBoxCoors(SimpleMatrix[] NXBBoxCoords, SimpleMatrix P){

//        transforming to world coordinates
        SimpleMatrix C1_trans = RT2.mult(NXBBoxCoords[0]);
        SimpleMatrix C2_trans = RT2.mult(NXBBoxCoords[1]);
        SimpleMatrix C3_trans = RT2.mult(NXBBoxCoords[2]);
        SimpleMatrix C4_trans = RT2.mult(NXBBoxCoords[3]);
        SimpleMatrix C5_trans = RT2.mult(NXBBoxCoords[4]);
        SimpleMatrix C6_trans = RT2.mult(NXBBoxCoords[5]);
        SimpleMatrix C7_trans = RT2.mult(NXBBoxCoords[6]);
        SimpleMatrix C8_trans = RT2.mult(NXBBoxCoords[7]);


//
////         transforming to projector pixels
//
        SimpleMatrix pp_c1 = P.mult(C1_trans);
        SimpleMatrix pp_c2 = P.mult(C2_trans);
        SimpleMatrix pp_c3 = P.mult(C3_trans);
        SimpleMatrix pp_c4 = P.mult(C4_trans);
        SimpleMatrix pp_c5 = P.mult(C5_trans);
        SimpleMatrix pp_c6 = P.mult(C6_trans);
        SimpleMatrix pp_c7 = P.mult(C7_trans);
        SimpleMatrix pp_c8 = P.mult(C8_trans);



        Point p1 = new Point((int) ( pp_c1.get(0,0) / pp_c1.get(2,0)),(int) ( pp_c1.get(1,0) / pp_c1.get(2,0)));
        Point p2 = new Point((int) ( pp_c2.get(0,0) / pp_c2.get(2,0)),(int) ( pp_c2.get(1,0) / pp_c2.get(2,0)));
        Point p3 = new Point((int) ( pp_c3.get(0,0) / pp_c3.get(2,0)),(int) ( pp_c3.get(1,0) / pp_c3.get(2,0)));
        Point p4 = new Point((int) ( pp_c4.get(0,0) / pp_c4.get(2,0)),(int) ( pp_c4.get(1,0) / pp_c4.get(2,0)));
        Point p5 = new Point((int) ( pp_c5.get(0,0) / pp_c5.get(2,0)),(int) ( pp_c5.get(1,0) / pp_c5.get(2,0)));
        Point p6 = new Point((int) ( pp_c6.get(0,0) / pp_c6.get(2,0)),(int) ( pp_c6.get(1,0) / pp_c6.get(2,0)));
        Point p7 = new Point((int) ( pp_c7.get(0,0) / pp_c7.get(2,0)),(int) ( pp_c7.get(1,0) / pp_c7.get(2,0)));
        Point p8 = new Point((int) ( pp_c8.get(0,0) / pp_c8.get(2,0)),(int) ( pp_c8.get(1,0) / pp_c8.get(2,0)));

        Point[] points = {p1, p2, p3, p4, p5, p6, p7, p8};
        return points;
    }

    @Override
    public void onStart() {

        System.out.println("server started successfully on port " + PORT);
        try {
            //starting agents only after server is running as their definitions include connecting to the server

            HumanAgent.start();
            RobotAgent.start();
            snifferAgent.start();
            IntrospectorAgent.start();
            rma.start();

            Process p = java.lang.Runtime.getRuntime().exec(new String[]{"google-chrome","http://localhost:3000/"});
            p.waitFor();
            System.out.println("MR Application launched");

        } catch (StaleProxyException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


    }



    public static void main(String[] args) throws StaleProxyException {

        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();

        AgentContainer main_container = runtime.createMainContainer(profile);

        // defining agents before starting the server as the OnMessage Handler uses their definitions to delegate O2A objects
        rma = main_container.createNewAgent("rma","jade.tools.rma.rma", null);
        HumanAgent = main_container.createNewAgent("Operator", "HumanAgent", new Object[]{UIconn,"ws://"+HOST+":"+PORT});
        RobotAgent = main_container.createNewAgent("Robot", "RobotAgent",  new Object[]{UIconn,"ws://"+HOST+":"+PORT});
        String introspect_params = "Robot";
        IntrospectorAgent = main_container.createNewAgent("Introspector","jade.tools.introspector.Introspector", new Object[]{introspect_params});
        String sniff_params = "Operator;Robot";
        snifferAgent = main_container.createNewAgent("sniffer","jade.tools.sniffer.Sniffer", new Object[]{sniff_params});


        WebSocketServer server = new SimpleServer(new InetSocketAddress(HOST, PORT));
        server.run();






    }
}

