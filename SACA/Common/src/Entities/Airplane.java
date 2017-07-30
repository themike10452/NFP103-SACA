package Entities;

import FMath.Vector3;
import Net.TcpConnection;
import UI.Viewport;
import Utils.Chrono;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Mike on 7/6/2017.
 */
public class Airplane implements IAirplane, TcpConnection.EventHandler {

    private Vector3 m_Position;
    private Vector3 m_Direction;
    private float m_Speed;

    private String m_Id;
    private boolean m_IsFlying;

    private TcpConnection m_Connection;

    private Thread m_WorkerThread = new Thread() {
        @Override
        public void run() {
            Chrono chrono = new Chrono();
            chrono.start();

            while (m_IsFlying) {
                try {
                    chrono.tick();
                    update(chrono.delta());
                    sendCoordinates();

                    Thread.sleep(500);
                }
                catch (InterruptedException ie) {
                    if (!m_IsFlying)
                        break;
                }
            }
        }
    };

    public Airplane() {
        m_Position = new Vector3();
        m_Direction = new Vector3(1, 1, 0).getNormalized();
        m_Speed = 1.0f;

        m_Id = "AP-" + new Random(System.currentTimeMillis()).nextInt(100);
        m_IsFlying = false;

        m_Connection = null;
    }

    private Airplane(String id) {
        this();
        m_Id = id;
    }

    @Override
    public Airplane setPosition(Vector3 position) {
        m_Position.set(position);
        return this;
    }

    @Override
    public Airplane setDirection(Vector3 direction) {
        m_Direction.set(direction);
        return this;
    }

    @Override
    public Airplane setSpeed(float speed) {
        m_Speed = Math.max(0.1f, speed);
        return this;
    }

    @Override
    public Airplane setAltitude(float altitude) {
        m_Position.Z = Math.max(0.0f, altitude);
        return this;
    }

    @Override
    public String getId() {
        return m_Id;
    }

    @Override
    public Vector3 getPosition() {
        return m_Position;
    }

    @Override
    public Vector3 getDirection() {
        return m_Direction;
    }

    @Override
    public float getSpeed() {
        return m_Speed;
    }

    @Override
    public float getAltitude() {
        return m_Position.Z;
    }

    public void takeOff() {
        m_IsFlying = true;
        m_WorkerThread.start();
    }

    public void destroy() {
        m_IsFlying = false;

        try {
            closeConnection();
        }
        catch (IOException ioe) {
            // do nothing
        }

        try {
            if (Thread.currentThread().getId() != m_WorkerThread.getId()) {
                m_WorkerThread.interrupt();
                m_WorkerThread.join();
            }
        }
        catch (InterruptedException ie) {
            // do nothing
        }
    }

    public void openConnection() throws IOException {
        if (m_Connection != null) return;

        m_Connection = new TcpConnection("127.0.0.1", Constants.Integers.PILOTING_PORT);
        m_Connection.addEventHandler(this);
    }

    public void closeConnection() throws IOException {
        if (m_Connection == null) return;

        m_Connection.close();
        m_Connection = null;
    }

    private void sendCoordinates() {
        if (m_Connection == null) return;

        m_Connection.send(this.toString());
    }

    private void update(long delta) {
        m_Position.add(Vector3.multiply(m_Direction, m_Speed * delta));
    }

    @Override
    public String toString() {
        return String.format("pln::<%s;%s;%s;%f>", m_Id, m_Position, m_Direction, m_Speed);
    }

    @Override
    public void onReceiveMessage(TcpConnection connection, String message) {
        //TODO handle received messages
    }

    @Override
    public void onCloseConnection(TcpConnection connection) {
        // do nothing
    }

    public static Airplane fromString(String str) {
        Pattern p = Pattern.compile("pln::<([^;]+);([^;]+);([^;]+);(\\d+(?:\\.\\d+)?)>");
        Matcher m = p.matcher(str);

        if (m.matches()) {
            return new Airplane(m.group(1))
                    .setPosition(Vector3.fromString(m.group(2)))
                    .setDirection(Vector3.fromString(m.group(3)))
                    .setSpeed(Float.parseFloat(m.group(4)));
        }

        System.err.println("Invalid serialized Airplane: " + str);
        return null;
    }

    public static List<Airplane> fromStringMultiple(String str) {
        Pattern p = Pattern.compile("pln::<([^;]+);([^;]+);([^;]+);(\\d+(?:\\.\\d+)?)>");
        Matcher m = p.matcher(str);

        List<Airplane> result = new ArrayList<>();

        while (m.find()) {
            Airplane airplane = new Airplane(m.group(1))
                    .setPosition(Vector3.fromString(m.group(2)))
                    .setDirection(Vector3.fromString(m.group(3)))
                    .setSpeed(Float.parseFloat(m.group(4)));

            result.add(airplane);
        }

        return result;
    }

    public static void draw(IAirplane airplane, GraphicsContext ctx, Viewport viewport) {
        Vector3 direction = airplane.getDirection();
        Vector3 position = airplane.getPosition();
        Vector3 drawPosition = Vector3.multiply(position, viewport.Scale).add(viewport.Padding, viewport.Padding, 0.0f);
        double xyRotAngle = Math.toDegrees(Math.atan2(direction.Y, direction.X));

        ctx.save();
        ctx.translate(drawPosition.X, drawPosition.Y);

        ctx.setFill(Color.BLACK);
        ctx.fillRect(-25, 25, 34, 12);

        ctx.setFont(Font.font("Consolas", 10));
        ctx.setFill(Color.WHITE);
        ctx.fillText(airplane.getId(), -23.0, 34.0);

        ctx.rotate(xyRotAngle);

        //TODO remove this
        /*m_Gfx.setStroke(Color.RED);
        m_Gfx.setLineWidth(2.0f);
        m_Gfx.strokeLine(0.0, 0.0, 50.0, 0.0);*/

        ctx.setEffect(new DropShadow(3.0, 0.0, 0.0, Color.BLACK));
        ctx.drawImage(Resources.Images.airplane, -25, -25, 50.0, 50.0);
        Airplane.class.getResource("");
        ctx.setEffect(null);

        ctx.restore();
    }

}
