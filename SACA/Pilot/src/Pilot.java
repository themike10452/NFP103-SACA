import Entities.Airplane;
import FMath.FMath;
import FMath.Rotator;
import FMath.Vector3;
import UI.Viewport;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Created by Mike on 7/12/2017.
 */
public class Pilot extends Application implements EventHandler<KeyEvent> {

    private static final float Scale = 0.01f;
    private static final float CanvasPadding = 25.0f;

    private Airplane m_Airplane;
    private ViewController m_ViewController;

    private final AnimationTimer m_AnimationTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            m_ViewController.update();
        }
    };

    public Pilot() {
        m_Airplane = new Airplane();
        m_ViewController = new ViewController();
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
        m_AnimationTimer.stop();
        m_Airplane.destroy();
    }

    @Override
    public void handle(KeyEvent event) {
        KeyCode code = event.getCode();

        if (code == KeyCode.A || code == KeyCode.D) {
            Vector3 dir = m_Airplane.getDirection();
            float angle = 0;

            if (code == KeyCode.A) {
                angle = 1;
            } else if (code == KeyCode.D) {
                angle = -1;
            }

            Rotator r = new Rotator(0.0f, 0.0f, angle);
            Vector3 newDir = r.getRotated(dir).getNormalized();
            m_Airplane.setDirection(newDir);
        }
        else {
            if (code == KeyCode.ENTER) {
                deploy();
            }
            else if (code == KeyCode.EQUALS || code == KeyCode.PLUS) {
                m_Airplane.setSpeed(m_Airplane.getSpeed() + 0.2f);
            }
            else if (code == KeyCode.UNDERSCORE || code == KeyCode.MINUS) {
                m_Airplane.setSpeed(m_Airplane.getSpeed() - 0.2f);
            }
        }
    }

    private void deploy() {
        try {
            m_Airplane.openConnection();
        }
        catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection problem");
            alert.setContentText("Failed to establish connection with SACA controller");
            alert.show();
            return;
        }

        m_Airplane.takeOff();

        m_AnimationTimer.start();
    }

    public class ViewController implements ChangeListener<Number> {

        @FXML
        private Canvas m_Canvas;
        @FXML
        private Text m_TextVelocity;
        @FXML
        private Text m_TextAltitude;

        private GraphicsContext m_Gfx;
        private Viewport m_Viewport;
        private Viewport m_AltmViewport;
        private boolean m_IsReady;

        private ViewController() {
            m_Viewport = new Viewport(0, 0, CanvasPadding, Scale);
            m_AltmViewport = new Viewport(180, 180, 20, 1);
        }

        @FXML
        public void initialize() {
            m_Gfx = m_Canvas.getGraphicsContext2D();

            m_Viewport.Width = (float) m_Canvas.getWidth();
            m_Viewport.Height = (float) m_Canvas.getHeight();

            //TODO revise this
            m_Canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                float x = (float) event.getX(), y = (float) event.getY();
                Vector3 newPosition = new Vector3(x, y, 0).add(-CanvasPadding, -CanvasPadding, 0.0f);
                newPosition.set(Vector3.multiply(newPosition, 1 / Scale));
                m_Airplane.getPosition().setX(newPosition.X).setY(newPosition.Y);
            });

            m_IsReady = true;
        }

        private void update() {
            if (!m_IsReady) return;

            m_TextVelocity.setText(Float.toString(m_Airplane.getSpeed()));
            m_TextAltitude.setText(Float.toString(m_Airplane.getPosition().Z));

            m_Gfx.clearRect(0, 0, m_Viewport.Width, m_Viewport.Height);
            Airplane.draw(m_Airplane, m_Gfx, m_Viewport);

            float halfW = m_AltmViewport.Width / 2;
            float halfH = m_AltmViewport.Height / 2;

            int alt = (int) m_Airplane.getPosition().Z;
            float altH1 = alt / 10000.0f;
            float altH2 = alt / 1000.0f;
            float altH3 = alt / 100.0f;

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

            m_Gfx.setEffect(null);
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
