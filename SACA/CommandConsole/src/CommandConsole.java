import Constants.Integers;
import Entities.Airplane;
import Entities.IAirplane;
import Net.Message;
import Net.TcpConnection;
import UI.Viewport;
import Utils.RuntimeUtils;
import Utils.StringUtils;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mike on 7/6/2017.
 */
public class CommandConsole extends Application implements TcpConnection.EventHandler {

    private static final float CanvasPadding = 25.0f;

    public static void main(String[] args) {
        launch(args);
    }

    private static final Object m_Mutex = new Object();

    private TcpConnection m_Connection;
    private final List<IAirplane> m_Airplanes;
    private final ObservableList<String> m_LockedAirplanes;
    private final ViewController m_ViewController;
    private boolean m_IsRunning;

    private final AnimationTimer m_AnimationTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            synchronized (m_Mutex) {
                m_ViewController.update();
            }
        }
    };

    public CommandConsole() {
        m_Airplanes = new ArrayList<>();
        m_LockedAirplanes = FXCollections.observableArrayList();
        m_ViewController = new ViewController();
        m_IsRunning = false;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Command Console");
        primaryStage.setResizable(true);

        final FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/Layout/main.fxml"));
        loader.setController(m_ViewController);

        final Pane root = loader.load();
        final Scene scene = new Scene(root);

        primaryStage.setScene(scene);
        primaryStage.show();

        try {
            m_Connection = new TcpConnection("127.0.0.1", Integers.MONITORING_PORT);
            m_Connection.addEventHandler(this);
            m_AnimationTimer.start();
        }
        catch (ConnectException ce) {
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection problem");
            alert.setContentText("Failed to establish connection with SACA controller");
            alert.show();
            return;
        }

        m_LockedAirplanes.addListener(m_ViewController);
        m_IsRunning = true;
    }

    @Override
    public void stop() throws Exception {
        m_IsRunning = false;
        m_AnimationTimer.stop();
        // connection is null when connection to SACA fails on start
        if (m_Connection != null) {
            m_Connection.close();
            m_Connection = null;
        }
    }

    @Override
    public void onReceiveMessage(TcpConnection connection, String message) {
        if (!m_IsRunning) return;

        synchronized (m_Mutex) {
            if (Message.isMessage(message)) {
                final Message msg = Message.fromString(message);
                assert msg != null;

                if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_ALERT) &&
                    !StringUtils.isNullOrWhitespace(msg.Data)) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Controller message");
                        alert.setContentText(msg.Data);
                        alert.show();
                    });
                }
                else if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_AIRPLANE_LIST)) {
                    m_Airplanes.clear();
                    m_Airplanes.addAll(Airplane.fromStringMultiple(msg.Data));
                    // sort by altitude in desc order
                    m_Airplanes.sort((a1, a2) -> (int) ((a1.getAltitude() - a2.getAltitude()) * 1000));
                }

                if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_LOCK_ACK)) {
                    if (!m_LockedAirplanes.contains(msg.To)) {
                        m_LockedAirplanes.add(msg.To);
                    }
                }
                else if (RuntimeUtils.isFlagSet(msg.Hint, Message.HINT_RELEASE_ACK)) {
                    m_LockedAirplanes.remove(msg.To);
                }
            }
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onCloseConnection(TcpConnection connection) {
        if (!m_IsRunning) return;

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("SACA Connection");
            alert.setContentText("Connection lost with SACA Controller");
            alert.show();
        });
    }

    private class ViewController implements ListChangeListener<String> {
        @FXML
        Canvas m_Canvas;
        @FXML
        TextArea m_TextArea;
        @FXML
        TextField m_TextInput;
        @FXML
        Button m_BtnSend;
        @FXML
        ComboBox<String> m_ComboAirplanes;

        GraphicsContext m_Gfx;
        Viewport m_Viewport;
        boolean m_IsReady;

        private ViewController() {
            m_Viewport = new Viewport(0, 0, CanvasPadding);
            m_IsReady = false;
        }

        @FXML
        public void initialize() {
            m_Gfx = m_Canvas.getGraphicsContext2D();
            m_Viewport.Width = (float) m_Canvas.getWidth();
            m_Viewport.Height = (float) m_Canvas.getHeight();
            m_IsReady = true;

            //todo ap control
            m_Canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                for (IAirplane ap : m_Airplanes) {
                    if (ap.hit(event.getX(), event.getY())) {
                        if (event.getButton() == MouseButton.PRIMARY) {
                            m_Connection.send(new Message(Message.HINT_LOCK, null, null, ap.getId()).toString());
                        }
                        else if (event.getButton() == MouseButton.SECONDARY) {
                            m_Connection.send(new Message(Message.HINT_RELEASE, null, null, ap.getId()).toString());
                        }
                        else if (event.getButton() == MouseButton.MIDDLE) {
                            if (m_ComboAirplanes.getItems().contains(ap.getId())) {
                                m_ComboAirplanes.getSelectionModel().select(ap.getId());
                            }
                        }
                        break;
                    }
                }
            });

            m_BtnSend.setOnMouseClicked(event -> sendMessage());
            m_TextInput.setOnAction(event -> sendMessage());
        }

        private void update() {
            if (!m_IsReady) return;

            m_Gfx.clearRect(0.0, 0.0, m_Viewport.Width, m_Viewport.Height);

            for (IAirplane ap : m_Airplanes) {
                Airplane.draw(m_Gfx, ap);
            }
        }

        private void sendMessage() {
            String text = m_TextInput.getText();
            if (!StringUtils.isNullOrWhitespace(text)) {
                String apId = m_ComboAirplanes.getSelectionModel().getSelectedItem();
                if (!StringUtils.isNullOrEmpty(apId)) {
                    m_Connection.send(new Message(Message.HINT_COMMAND, text, null, apId).toString());
                    m_TextInput.clear();
                    m_TextArea.appendText(text);
                    m_TextArea.appendText("\n");
                }
            }
        }

        @Override
        public void onChanged(final Change<? extends String> c) {
            Platform.runLater(() -> {
                while (c.next()) {
                    if (c.wasRemoved()) m_ComboAirplanes.getItems().removeAll(c.getRemoved());
                    if (c.wasAdded()) m_ComboAirplanes.getItems().addAll(c.getAddedSubList());
                }
            });
        }
    }

}
