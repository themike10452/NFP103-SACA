import Constants.Integers;
import Entities.Airplane;
import Entities.IAirplane;
import Net.TcpConnection;
import UI.Viewport;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mike on 7/6/2017.
 */
public class CommandConsole extends Application implements TcpConnection.EventHandler {

    private static final float Scale = 0.01f;
    private static final float CanvasPadding = 25.0f;

    public static void main(String[] args) {
        launch(args);
    }

    private final Object m_Mutex = new Object();
    private TcpConnection m_Connection;
    private List<IAirplane> m_Airplanes;
    private ViewController m_ViewController;

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
        m_ViewController = new ViewController();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Command Console");
        primaryStage.setResizable(true);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/Layout/main.fxml"));
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
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection problem");
            alert.setContentText("Failed to establish connection with SACA controller");
            alert.show();
        }
    }

    @Override
    public void stop() throws Exception {
        m_AnimationTimer.stop();
        // connection is null when connection to SACA fails on start
        if (m_Connection != null) {
            m_Connection.close();
            m_Connection = null;
        }
    }

    @Override
    public void onReceiveMessage(TcpConnection connection, String message) {
        synchronized (m_Mutex) {
            m_Airplanes.clear();
            m_Airplanes.addAll(Airplane.fromStringMultiple(message));
        }
    }

    @Override
    public void onCloseConnection(TcpConnection connection) {

    }

    private class ViewController {
        @FXML
        Canvas m_Canvas;

        GraphicsContext m_Gfx;
        Viewport m_Viewport;
        boolean m_IsReady;

        private ViewController() {
            m_Viewport = new Viewport(0, 0, CanvasPadding, Scale);
            m_IsReady = false;
        }

        @FXML
        public void initialize() {
            m_Gfx = m_Canvas.getGraphicsContext2D();
            m_Viewport.Width = (float) m_Canvas.getWidth();
            m_Viewport.Height = (float) m_Canvas.getHeight();
            m_IsReady = true;
        }

        private void update() {
            if (!m_IsReady) return;

            m_Gfx.clearRect(0.0, 0.0, m_Viewport.Width, m_Viewport.Height);

            for (IAirplane ap : m_Airplanes) {
                Airplane.draw(ap, m_Gfx, m_Viewport);
            }
        }
    }

}
