import Entities.Airplane;
import FMath.FMath;
import FMath.Vector3;
import Net.TcpConnection;
import UI.Viewport;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Created by Mike on 7/12/2017.
 */
public class Pilot extends Application implements EventHandler<KeyEvent>, TcpConnection.EventHandler {

    private Airplane m_Airplane;
    private ViewController m_ViewController;
    private boolean m_IsFlying;

    private final AnimationTimer m_AnimationTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            m_ViewController.update();
        }
    };

    public Pilot() {
        m_Airplane = new Airplane();
        m_ViewController = new ViewController();
        m_IsFlying = false;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Pilot");
        primaryStage.setResizable(true);

        final FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/Layout/main.fxml"));
        loader.setController(m_ViewController);

        final Pane root = loader.load();
        final Scene scene = new Scene(root);

        primaryStage.setScene(scene);
        primaryStage.show();

        // keyboard event handler
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this);
    }

    @Override
    public void stop() throws Exception {
        m_IsFlying = false;
        m_AnimationTimer.stop();
        m_Airplane.destroy();
    }

    private void deploy() {
        if (m_IsFlying) return;

        try {
            m_Airplane.openConnection();
            m_Airplane.addConnectionEventHandler(this);
        }
        catch (IOException e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection problem");
                alert.setContentText("Failed to establish connection with SACA controller");
                alert.show();
            });
            return;
        }

        m_Airplane.setSpeed(900.0f);

        m_IsFlying = true;
        m_Airplane.takeOff();
    }

    @Override
    public void handle(KeyEvent event) {
        KeyCode code = event.getCode();

        if (code == KeyCode.ENTER) {
            deploy();
        }
        else if (code == KeyCode.A) {
            m_Airplane.setYaw(m_Airplane.getYaw() + 1.0f);
        }
        else if (code == KeyCode.D) {
            m_Airplane.setYaw(m_Airplane.getYaw() - 1.0f);
        }
        else if (code == KeyCode.W) {
            m_Airplane.setPitch(m_Airplane.getPitch() + 1.0f);
        }
        else if (code == KeyCode.S) {
            m_Airplane.setPitch(m_Airplane.getPitch() - 1.0f);
        }
        else if (code == KeyCode.EQUALS || code == KeyCode.PLUS) {
            m_Airplane.setSpeed(m_Airplane.getSpeed() + 10.0f);
        }
        else if (code == KeyCode.UNDERSCORE || code == KeyCode.MINUS) {
            m_Airplane.setSpeed(m_Airplane.getSpeed() - 10.0f);
        }
    }

    @Override
    public void onReceiveMessage(TcpConnection connection, String message) {
        m_ViewController.onReceiveMessage(message);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onCloseConnection(TcpConnection connection) {
        if (!m_IsFlying) return;

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("SACA Connection");
            alert.setContentText("Connection lost with SACA Controller");
            alert.show();
        });
    }

    public class ViewController implements ChangeListener<Number> {

        @FXML
        private Canvas m_Canvas;
        @FXML
        private TextArea m_TextArea;

        private GraphicsContext m_Gfx;
        private Viewport m_Viewport;
        private Viewport m_AltmViewport;
        private Viewport m_DashViewport;
        private boolean m_IsReady;

        private ViewController() {
            m_Viewport = new Viewport(0, 0);
            m_AltmViewport = new Viewport(150, 150, 10);
            m_DashViewport = new Viewport(120, 150, 20);
        }

        @FXML
        public void initialize() {
            m_Gfx = m_Canvas.getGraphicsContext2D();

            m_Viewport.Width = (float) m_Canvas.getWidth();
            m_Viewport.Height = (float) m_Canvas.getHeight();

            //TODO revise this
            m_Canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (!m_IsFlying) {
                    float x = (float) event.getX(),
                          y = (float) event.getY(),
                          z = m_Airplane.getAltitude();

                    x /= Constants.Decimals.MAP_SCALE;
                    y /= Constants.Decimals.MAP_SCALE;

                    m_Airplane.setPosition(new Vector3(x, y, z));
                }
            });

            m_IsReady = true;

            m_AnimationTimer.start();
        }

        private void update() {
            if (!m_IsReady) return;

            int altitude = (int) FMath.kilometersToFeet(m_Airplane.getAltitude()); // altitude in feet
            int speed = (int) FMath.kilometersToMiles(m_Airplane.getSpeed()); // speed in mph

            m_Gfx.clearRect(0, 0, m_Viewport.Width, m_Viewport.Height);

            Airplane.draw(m_Gfx, m_Airplane);

            float halfW = m_AltmViewport.Width / 2;
            float halfH = m_AltmViewport.Height / 2;

            float altH1 = altitude / 10000.0f;
            float altH2 = altitude / 1000.0f;
            float altH3 = altitude / 100.0f;

            float h1Rot = FMath.clampAngle(360.0f * (altH1 / 10.0f));
            float h2Rot = FMath.clampAngle(360.0f * (altH2 / 10.0f));
            float h3Rot = FMath.clampAngle(360.0f * (altH3 / 10.0f));

            float altmOffsetW = m_Viewport.Width - m_AltmViewport.Width - m_AltmViewport.Padding;
            float altmOffsetH = m_Viewport.Height - m_AltmViewport.Height - m_AltmViewport.Padding;

            m_Gfx.setEffect(new DropShadow(3.0, 0.0, 0.0, Color.BLACK));

            m_Gfx.save();
                m_Gfx.translate(altmOffsetW, altmOffsetH);
                m_Gfx.drawImage(Resources.Images.altimeter, 0, 0, m_AltmViewport.Width, m_AltmViewport.Height);
            m_Gfx.restore();
            m_Gfx.setEffect(null);

            m_Gfx.save();
                m_Gfx.translate(altmOffsetW, altmOffsetH);
                m_Gfx.translate(halfW, halfH);
                m_Gfx.rotate(h1Rot);
                m_Gfx.translate(-halfW, -halfH);
                m_Gfx.drawImage(Resources.Images.altimeter_hand1, 0, 0, m_AltmViewport.Width, m_AltmViewport.Height);
            m_Gfx.restore();

            m_Gfx.save();
                m_Gfx.translate(altmOffsetW, altmOffsetH);
                m_Gfx.translate(halfW, halfH);
                m_Gfx.rotate(h2Rot);
                m_Gfx.translate(-halfW, -halfH);
                m_Gfx.drawImage(Resources.Images.altimeter_hand2, 0, 0, m_AltmViewport.Width, m_AltmViewport.Height);
            m_Gfx.restore();

            m_Gfx.save();
                m_Gfx.translate(altmOffsetW, altmOffsetH);
                m_Gfx.translate(halfW, halfH);
                m_Gfx.rotate(h3Rot);
                m_Gfx.translate(-halfW, -halfH);
                m_Gfx.drawImage(Resources.Images.altimeter_hand3, 0, 0, m_AltmViewport.Width, m_AltmViewport.Height);
            m_Gfx.restore();

            final float dashOffsetW = altmOffsetW - m_DashViewport.Width;
            final float dashOffsetH = altmOffsetH + m_DashViewport.Padding;
            final float rowHeight = 15.0f;
            final float apSize = 100.0f;
            final float halfApSize = apSize/2.0f;

            m_Gfx.setFill(Color.WHITE);
            m_Gfx.setFont(Font.font("Consolas", 16));
            m_Gfx.setEffect(new DropShadow(3.0, 0.0, 0.0, Color.BLACK));
            m_Gfx.fillText(String.format("%05d MPH", speed), dashOffsetW, dashOffsetH);
            m_Gfx.fillText(String.format("%05d Feet", altitude), dashOffsetW, dashOffsetH + rowHeight);
            m_Gfx.fillText(String.format("%02d Degrees", (int)m_Airplane.getPitch()), dashOffsetW, m_Viewport.Height - m_DashViewport.Padding);
            m_Gfx.setEffect(null);

            m_Gfx.save();
                m_Gfx.translate(dashOffsetW - 10, dashOffsetH + rowHeight);
                m_Gfx.translate(halfApSize, halfApSize);
                m_Gfx.rotate(-m_Airplane.getPitch());
                m_Gfx.translate(-halfApSize, -halfApSize);
                m_Gfx.drawImage(Resources.Images.airplane_side, 0, 0, 100, 100);
            m_Gfx.restore();

            m_Gfx.setEffect(null);
        }

        private void onReceiveMessage(String message) {
            m_TextArea.appendText(message);
            m_TextArea.appendText("\n");
        }

        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            m_Viewport.Width = (float) m_Canvas.getWidth();
            m_Viewport.Height = (float) m_Canvas.getHeight();
        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        launch(args);
    }

}
