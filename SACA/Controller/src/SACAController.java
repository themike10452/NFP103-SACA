import Entities.Airplane;
import Entities.IAirplane;
import Net.TcpConnection;
import Net.TcpListener;
import Utils.Pair;
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

    private Host m_Host;
    private Thread m_BroadcastThread;
    private boolean m_IsRunning;

    private final Object m_Mutex = new Object();

    @Override
    public void init() throws Exception {
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

        private final HashMap<String, Pair<TcpConnection, IAirplane>> m_Airplanes;
        private final Set<TcpConnection> m_Monitors;
        private boolean m_IsRunning;

        private final TcpListener m_AirplaneListener = new TcpListener(Constants.Integers.PILOTING_PORT) {
            @Override
            public void onReceiveNewConnection(TcpConnection connection) {
            }

            @Override
            public void onReceiveMessage(TcpConnection connection, String message) {
                synchronized (m_Mutex) {
                    final IAirplane airplane = Airplane.fromString(message);
                    if (airplane != null) {
                        if (!m_Airplanes.containsKey(airplane.getId())) {
                            m_Airplanes.put(airplane.getId(), new Pair<>(connection, airplane));
                        }
                        else {
                            m_Airplanes
                                    .get(airplane.getId()).Second
                                    .setPosition(airplane.getPosition())
                                    .setDirection(airplane.getDirection())
                                    .setSpeed(airplane.getSpeed());
                        }
                    }
                }
            }

            @Override
            public void onCloseConnection(TcpConnection connection) {
                synchronized (m_Mutex) {
                    m_Airplanes.keySet()
                            .stream()
                            .filter(key -> m_Airplanes.get(key).First == connection)
                            .findFirst()
                            .ifPresent(m_Airplanes::remove);

                    connection.close();
                }
            }
        };

        private final TcpListener m_MonitorListener = new TcpListener(Constants.Integers.MONITORING_PORT) {
            @Override
            public void onReceiveNewConnection(TcpConnection connection) {
                m_Monitors.add(connection);
            }

            @Override
            public void onReceiveMessage(TcpConnection connection, String message) {
            }

            @Override
            public void onCloseConnection(TcpConnection connection) {
                synchronized (m_Mutex) {
                    m_Monitors.remove(connection);
                    connection.close();
                }
            }
        };

        private Host() throws IOException {
            m_Airplanes = new HashMap<>();
            m_Monitors = new HashSet<>();
            m_IsRunning = false;
        }

        private void start() {
            if (m_IsRunning)
                return;

            m_IsRunning = true;
            m_AirplaneListener.startListening();
            m_MonitorListener.startListening();
        }

        private void stop() {
            if (!m_IsRunning)
                return;

            m_IsRunning = false;
            m_AirplaneListener.stopListening();
            m_MonitorListener.stopListening();

            m_Monitors.forEach(TcpConnection::close);
            m_Airplanes.forEach((k, p) -> p.First.close());
        }

        private void broadcastUpdates() {
            synchronized (m_Mutex) {
                final StringBuilder sb = new StringBuilder();
                m_Airplanes.forEach((k, p) -> sb.append(p.Second));

                final String message = sb.toString();
                for (TcpConnection monitor : m_Monitors) {
                    monitor.send(message);
                }
            }
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
