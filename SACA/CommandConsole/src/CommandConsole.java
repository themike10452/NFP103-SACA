import Constants.Integers;
import Entities.Airplane;
import Entities.IAirplane;
import FMath.FMath;
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
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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
                    // unlock disconnected airplanes
                    m_LockedAirplanes.removeIf(id -> m_Airplanes.stream().noneMatch(ap -> id.equals(ap.getId())));
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
        Viewport m_ChildViewport;
        float cursorX;
        float cursorY;
        boolean m_IsReady;

        private ViewController() {
            m_Viewport = new Viewport(0, 0, CanvasPadding);
            m_ChildViewport = new Viewport(200, 50, 0);
            m_IsReady = false;
            cursorX = -1;
            cursorY = -1;
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

            m_Canvas.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
                cursorX = (float) event.getX();
                cursorY = (float) event.getY();
            });

            m_BtnSend.setOnMouseClicked(event -> sendMessage());
            m_TextInput.setOnAction(event -> sendMessage());
        }

        private void update() {
            if (!m_IsReady) return;

            m_Gfx.clearRect(0.0, 0.0, m_Viewport.Width, m_Viewport.Height);

            for (IAirplane ap : m_Airplanes) {
                Airplane.draw(m_Gfx, ap);
                if (ap.getBoundsRect().contains(cursorX, cursorY)) {
                    drawDetails(ap);
                }
            }
        }

        private void drawDetails(IAirplane ap) {
            int altitude = (int) FMath.kilometersToFeet(ap.getAltitude()); // altitude in feet
            int speed = (int) FMath.kilometersToMiles(ap.getSpeed()); // speed in mph
            float posX = ap.getPosition().X;
            float posY = ap.getPosition().Y;

            final float vMargin = m_Viewport.Width - m_ChildViewport.Width + m_ChildViewport.Padding;
            final float rowHeight = 15.0f;
            final float apSize = 50.0f;
            final float halfApSize = apSize/2.0f;

            m_Gfx.setFill(Color.WHITE);
            m_Gfx.setFont(Font.font("Consolas", 14));
            m_Gfx.setEffect(new DropShadow(3.0, 0.0, 0.0, Color.BLACK));
            m_Gfx.fillText(String.format("%12s: %4.1f,%4.1f", "Coordinates", posX, posY), vMargin, rowHeight);
            m_Gfx.fillText(String.format("%12s: %05d MPH", "Speed", speed), vMargin, 2*rowHeight);
            m_Gfx.fillText(String.format("%12s: %05d Feet", "Altitude", altitude), vMargin, 3*rowHeight);
            m_Gfx.fillText(String.format("%12s: %02d Degrees", "Nose Pitch", (int)ap.getPitch()), vMargin, 4*rowHeight);
            m_Gfx.setEffect(null);

            m_Gfx.save();
                m_Gfx.translate(vMargin + 120, 4*rowHeight);
                m_Gfx.translate(halfApSize, halfApSize);
                m_Gfx.rotate(-ap.getPitch());
                m_Gfx.translate(-halfApSize, -halfApSize);
                m_Gfx.drawImage(Resources.Images.airplane_side, 0, 0, apSize, apSize);
            m_Gfx.restore();
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
