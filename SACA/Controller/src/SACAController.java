import Entities.Airplane;
import Entities.IAirplane;
import FMath.Ray;
import FMath.Vector3;
import Net.Message;
import Net.TcpConnection;
import Net.TcpListener;
import Utils.RuntimeUtils;
import Utils.StringUtils;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Mike on 7/6/2017.
 */
public class SACAController extends Application implements Runnable {

    private CollisionDetection m_CollDetect;
    private Host m_Host;
    private Thread m_BroadcastThread;
    private boolean m_IsRunning;

    private final Object m_Mutex = new Object();

    @Override
    public void init() throws Exception {
        m_CollDetect = new CollisionDetection();
        m_Host = new Host();
        m_BroadcastThread = new Thread(this);
        m_IsRunning = false;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/Layout/main.fxml"));
        loader.setController(new ViewController());

        GridPane root = loader.load();
        Scene scene = new Scene(root);

        primaryStage.setScene(scene);
        primaryStage.show();

        m_IsRunning = true;

        m_Host.start();
        m_BroadcastThread.start();
    }

    @Override
    public void stop() throws Exception {
        m_IsRunning = false;
        m_BroadcastThread.interrupt();
        m_BroadcastThread.join();
        m_Host.stop();
    }

    @Override
    public void run() {
        while (m_IsRunning) {
            synchronized (m_Mutex) {
                m_CollDetect.update();
                m_Host.broadcastUpdates();
            }

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ie) {
                if (!m_IsRunning)
                    break;
            }
        }
    }

    private class Host {

        private final HashMap<String, IAirplane> m_Airplanes;
        private final HashMap<String, TcpConnection> m_Connections;
        private final HashMap<String, TcpConnection> m_CommandConsoles;
        private final Set<TcpConnection> m_Monitors;
        private boolean m_IsRunning;

        private final TcpListener m_AirplaneListener = new TcpListener(Constants.Integers.PILOTING_PORT) {
            @Override
            public void onReceiveNewConnection(TcpConnection connection) {
            }

            @Override
            public void onReceiveMessage(TcpConnection connection, String message) {
                if (!m_IsRunning) return;

                synchronized (m_Mutex) {
                    final IAirplane airplane = Airplane.fromString(message);
                    if (airplane != null) {
                        if (!m_Airplanes.containsKey(airplane.getId())) {
                            m_Airplanes.put(airplane.getId(), airplane);
                            m_Connections.put(airplane.getId(), connection);
                        }
                        else {
                            m_Airplanes
                                    .get(airplane.getId())
                                    .setPosition(airplane.getPosition())
                                    .setRoll(airplane.getRoll())
                                    .setPitch(airplane.getPitch())
                                    .setYaw(airplane.getYaw())
                                    .setSpeed(airplane.getSpeed());
                        }
                    }
                }
            }

            @Override
            public void onCloseConnection(TcpConnection connection) {
                if (!m_IsRunning) return;

                synchronized (m_Mutex) {
                    m_Connections.keySet()
                            .stream()
                            .filter(key -> m_Connections.get(key) == connection)
                            .findFirst()
                            .ifPresent(key -> {
                                m_Airplanes.remove(key);
                                m_Connections.remove(key);
                            });
                }
            }
        };

        private final TcpListener m_ConsoleListener = new TcpListener(Constants.Integers.MONITORING_PORT) {
            @Override
            public void onReceiveNewConnection(TcpConnection connection) {
                m_Monitors.add(connection);
            }

            @Override
            public void onReceiveMessage(TcpConnection connection, String message) {
                synchronized (m_Mutex) {
                    if (Message.isMessage(message)) {
                        final Message msg = Message.fromString(message);
                        assert msg != null;

                        if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_LOCK)) {
                            if (!StringUtils.isNullOrWhitespace(msg.To)) {
                                if (m_Airplanes.containsKey(msg.To) && !m_CommandConsoles.containsKey(msg.To)) {
                                    m_CommandConsoles.put(msg.To, connection);
                                    connection.send(new Message(Message.HINT_LOCK_ACK, null, msg.From, msg.To).toString());
                                }
                                else {
                                    connection.send(new Message(Message.HINT_ALERT, "Action denied").toString());
                                }
                            }
                        }

                        if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_COMMAND)) {
                            if (!StringUtils.isNullOrWhitespace(msg.To)) {
                                final TcpConnection cnx = m_CommandConsoles.get(msg.To);
                                if (cnx == connection) {
                                    final TcpConnection destConnection = m_Connections.get(msg.To);
                                    if (destConnection != null) {
                                        destConnection.send(msg.Data);
                                    }
                                }
                                else {
                                    connection.send(new Message(Message.HINT_ALERT, "Action denied").toString());
                                }
                            }
                        }

                        if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_RELEASE)) {
                            if (!StringUtils.isNullOrWhitespace(msg.To)) {
                                if (m_CommandConsoles.get(msg.To) == connection) {
                                    m_CommandConsoles.remove(msg.To);
                                    connection.send(new Message(Message.HINT_RELEASE_ACK, null, msg.From, msg.To).toString());
                                }
                                else {
                                    connection.send(new Message(Message.HINT_ALERT, "Action denied").toString());
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onCloseConnection(TcpConnection connection) {
                synchronized (m_Mutex) {
                    m_Monitors.remove(connection);
                }
            }
        };

        private Host() throws IOException {
            m_Airplanes = new HashMap<>();
            m_Connections = new HashMap<>();
            m_CommandConsoles = new HashMap<>();
            m_Monitors = new HashSet<>();
            m_IsRunning = false;
        }

        private void start() {
            if (m_IsRunning)
                return;

            m_IsRunning = true;
            m_AirplaneListener.startListening();
            m_ConsoleListener.startListening();
        }

        private void stop() {
            if (!m_IsRunning)
                return;

            m_IsRunning = false;
            m_AirplaneListener.stopListening();
            m_ConsoleListener.stopListening();

            m_Monitors.forEach(TcpConnection::close);
            m_Connections.values().forEach(TcpConnection::close);

            m_Monitors.clear();
            m_Connections.clear();
        }

        private void broadcastUpdates() {
            synchronized (m_Mutex) {
                final StringBuilder sb = new StringBuilder();
                m_Airplanes.forEach((k, p) -> sb.append(p));

                final Message message = new Message(Message.HINT_AIRPLANE_LIST, sb.toString());
                for (TcpConnection monitor : m_Monitors) {
                    monitor.send(message.toString());
                }
            }
        }

    }

    private class CollisionDetection {

        private static final float VERTICAL_NOTICE_DISTANCE        = 0.4572f;  //  km (equiv to 1500 feet)
        private static final float HORIZONTAL_NOTICE_DISTANCE      = 15.0f;    // km
        private static final float VERTICAL_DISTURBANCE_DISTANCE   = 0.3048f;  // km (equiv to 1000 feet)
        private static final float HORIZONTAL_DISTURBANCE_DISTANCE = 9.0f;     // km
        private static final float VERTICAL_PANIC_DISTANCE         = 0.15f;    // km
        private static final float HORIZONTAL_PANIC_DISTANCE       = 1.0f;     // km
        private static final float FATAL_DISTANCE                  = 0.05f;    // km (equiv to 50 meters)

        private void update() {
            final HashMap<String, Integer> detections = new HashMap<>();

            int flag = 0;
            for (IAirplane target : m_Host.m_Airplanes.values()) {
                final Ray ray1 = target.getRay();

                for (IAirplane other : m_Host.m_Airplanes.values()) {
                    if (target == other || // skip collision test with self
                        detections.containsKey(String.format("%s;%s", target.getId(), other.getId()))) // skip test if already detected
                        continue;

                    final Vector3 pos1 = target.getPosition();
                    final Vector3 pos2 = other.getPosition();

                    final float posDistance = Vector3.distance(pos1, pos2);
                    final float posXyDistance = Vector3.xyDistance(pos1, pos2);
                    final float posZDistance = Math.abs(target.getAltitude() - other.getAltitude());

                    if (posXyDistance <= HORIZONTAL_DISTURBANCE_DISTANCE &&
                        posZDistance <= VERTICAL_DISTURBANCE_DISTANCE)
                        flag = Airplane.FLAG_WARN;

                    final Ray ray2 = other.getRay();

                    final Vector3 r1Cp = ray1.nearestPointToRay(ray2);
                    final Vector3 r2Cp = ray2.nearestPointToRay(ray1);

                    boolean willCross = false;

                    // collision point is null if both rays are collinear
                    if (r1Cp != null && r2Cp != null) {
                        // direction towards nearest point
                        Vector3 d1 = Vector3.subtract(r1Cp, ray1.getPosition());
                        Vector3 d2 = Vector3.subtract(r2Cp, ray2.getPosition());

                        final float dot1 = Vector3.dot(d1, ray1.getDirection());
                        final float dot2 = Vector3.dot(d2, ray2.getDirection());

                        // the dot product is positive if the ray direction
                        // and collision position vectors point in the same direction
                        willCross = dot1 > 0 && dot2 > 0;
                    }

                    if (!willCross)
                        continue;

                    final float pathXyDistance = Vector3.xyDistance(r1Cp, r2Cp);
                    final float pathZDistance = Vector3.zDistance(r1Cp, r2Cp);

                    if (flag == Airplane.FLAG_WARN) {
                        if (pathXyDistance <= HORIZONTAL_PANIC_DISTANCE &&
                            pathZDistance <= VERTICAL_PANIC_DISTANCE)
                            flag = Airplane.FLAG_PANIC;
                    }
                    else {
                        final boolean notice = posZDistance <= VERTICAL_NOTICE_DISTANCE && posXyDistance <= HORIZONTAL_NOTICE_DISTANCE;

                        if (notice) {
                            if (posZDistance <= 0.1524) { // same vertical flight level
                                if (pathXyDistance < HORIZONTAL_DISTURBANCE_DISTANCE) {
                                    flag = Airplane.FLAG_WARN;
                                }
                            }
                            else {
                                if (pathZDistance < VERTICAL_DISTURBANCE_DISTANCE) {
                                    flag = Airplane.FLAG_WARN;
                                }
                            }
                        }
                    }

                    if (flag != 0) {
                        detections.put(String.format("%s;%s", other.getId(), target.getId()), flag);
                    }

                    if (posDistance < FATAL_DISTANCE) {
                        //todo
                    }
                }
            }

            final String collisionList = detections.keySet()
                    .stream()
                    .map(k -> String.format("%s;%d", k, detections.get(k)))
                    .reduce("", (s1, s2) -> String.join("|", s1, s2));

            m_Host.m_Monitors.forEach(m -> m.send(new Message(Message.HINT_COLLISION_DETECTION_LIST, collisionList).toString()));
        }

    }

    public class ViewController {

        @FXML
        private Text m_TextPilotingPort;
        @FXML
        private Text m_TextMonitoringPort;

        @FXML
        public void initialize() {
            m_TextPilotingPort.setText(Integer.toString(Constants.Integers.PILOTING_PORT));
            m_TextMonitoringPort.setText(Integer.toString(Constants.Integers.MONITORING_PORT));
        }

    }

    public static void main(String[] args) {
        launch();
    }

}
