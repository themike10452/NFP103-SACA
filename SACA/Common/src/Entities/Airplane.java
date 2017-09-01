package Entities;

import FMath.FMath;
import FMath.Ray;
import FMath.Rotator;
import FMath.Vector3;
import Net.TcpConnection;
import Utils.StringUtils;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Constants.Decimals.*;

/**
 * Created by Mike on 7/6/2017.
 */
public class Airplane implements IAirplane {

    // collision detection flags
    public static final int FLAG_CD_WARN  = 0x01;
    public static final int FLAG_CD_PANIC = 0x02;

    // display flags
    public static final int FLAG_DISP_HIGHLIGHTED = 0x04;

    // masks
    public static final int CD_MASK = 0x03;
    public static final int DISP_MASK = 0x04;

    private static final String RegexPattern = "pln::<([^;]+);([^;]+);(\\d+(?:\\.\\d+));(\\d+(?:\\.\\d+));(\\d+(?:\\.\\d+));(\\d+(?:\\.\\d+)?);(\\d+)>";

    private Vector3 m_Position;
    private Vector3 m_Direction;

    private float m_Pitch;
    private float m_Yaw;
    private float m_Roll;
    private float m_Speed;

    private String m_Id;
    private int Flags;
    private boolean m_IsFlying;

    private TcpConnection m_Connection;
    private Rectangle m_Rect;

    private Thread m_WorkerThread = new Thread() {
        @Override
        public void run() {
            while (m_IsFlying) {
                try {
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
        m_Roll = 0.0f;
        m_Pitch = 0.0f;
        m_Yaw = 0.0f;
        m_Speed = 0.0f;

        Random random = new Random(System.currentTimeMillis());
        m_Id = String.format("AP-%d%d", random.nextInt(10), random.nextInt(10));

        m_Rect = new Rectangle(25, 25);
        m_Connection = null;

        m_IsFlying = false;

        updateBoundsRect();
        updateDirection();
    }

    private Airplane setId(String id) {
        this.m_Id = id;
        return this;
    }

    private Airplane setFlags(int flags) {
        this.Flags = flags;
        return this;
    }

    @Override
    public IAirplane setPosition(Vector3 position) {
        m_Position.setX(position.X);
        m_Position.setY(position.Y);
        setAltitude(position.Z);
        updateBoundsRect();
        return this;
    }

    @Override
    public IAirplane setSpeed(float speed) {
        m_Speed = Math.min(Math.max(0.0f, speed), AIRPLANE_MAX_SPEED);
        return this;
    }

    @Override
    public IAirplane setAltitude(float altitude) {
        m_Position.Z = Math.min(Math.max(0.0f, altitude), AIRPLANE_MAX_ALT);
        return this;
    }

    @Override
    public IAirplane setPitch(float pitch) {
        // limit pitch angle
        if (pitch > AIRPLANE_MAX_PITCH)
            pitch = AIRPLANE_MAX_PITCH;

        if (pitch < -AIRPLANE_MAX_PITCH)
            pitch = -AIRPLANE_MAX_PITCH;

        m_Pitch = pitch;
        updateDirection();
        return this;
    }

    @Override
    public IAirplane setYaw(float yaw) {
        m_Yaw = FMath.clampAngle(yaw);
        updateDirection();
        return this;
    }

    @Override
    public IAirplane setRoll(float roll) {
        // limit roll angle
        if (roll > Constants.Decimals.AIRPLANE_MAX_ROLL)
            roll = Constants.Decimals.AIRPLANE_MAX_ROLL;

        if (roll < -Constants.Decimals.AIRPLANE_MAX_ROLL)
            roll = -Constants.Decimals.AIRPLANE_MAX_ROLL;

        m_Roll = roll;
        updateDirection();
        return this;
    }

    @Override
    public IAirplane setCdState(int state) {
        this.Flags = (this.Flags & ~CD_MASK) | (state & CD_MASK);
        return this;
    }

    @Override
    public IAirplane setDispState(int state) {
        this.Flags = (this.Flags & ~DISP_MASK) | (state & DISP_MASK);
        return this;
    }

    @Override
    public String getId() {
        return m_Id;
    }

    @Override
    public Vector3 getPosition() {
        return new Vector3(m_Position.X, m_Position.Y, m_Position.Z);
    }

    @Override
    public Vector3 getXyPosition() {
        return new Vector3(m_Position.X, m_Position.Y, 0.0f);
    }

    @Override
    public Vector3 getDirection() {
        return new Vector3(m_Direction.X, m_Direction.Y, m_Direction.Z);
    }

    @Override
    public Ray getRay() {
        return new Ray(getPosition(), getDirection());
    }

    @Override
    public float getSpeed() {
        return m_Speed;
    }

    @Override
    public float getAltitude() {
        return m_Position.Z;
    }

    @Override
    public float getPitch() {
        return m_Pitch;
    }

    @Override
    public float getYaw() {
        return m_Yaw;
    }

    @Override
    public float getRoll() {
        return m_Roll;
    }

    @Override
    public int getCdState() {
        return this.Flags & CD_MASK;
    }

    @Override
    public int getDispState() {
        return this.Flags & DISP_MASK;
    }

    @Override
    public Rectangle getBoundsRect() {
        return new Rectangle
        (
            m_Rect.getX(),
            m_Rect.getY(),
            m_Rect.getWidth(),
            m_Rect.getHeight()
        );
    }

    @Override
    public boolean hit(double x, double y) {
        return m_Rect.contains(x, y);
    }

    public void addConnectionEventHandler(TcpConnection.EventHandler handler) {
        if (m_Connection == null) return;

        m_Connection.addEventHandler(handler);
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
    }

    public void closeConnection() throws IOException {
        if (m_Connection == null) return;

        m_Connection.close();
        m_Connection = null;
    }

    public void sendCoordinates() {
        if (m_Connection == null) return;

        m_Connection.send(this.toString());
    }

    public void update(long delta) {
        m_Position.add(Vector3.multiply(m_Direction, (m_Speed / 3600) * (delta / 1000.0f)));
        setAltitude(getAltitude());
        updateBoundsRect();
    }

    private void updateDirection() {
        m_Direction = new Rotator(m_Roll, m_Pitch, m_Yaw).getRotated(new Vector3(1.0f, 0.0f, 0.0f)).getNormalized();
    }

    private void updateBoundsRect() {
        m_Rect.setX(m_Position.X * MAP_SCALE - m_Rect.getWidth() / 2);
        m_Rect.setY(m_Position.Y * MAP_SCALE - m_Rect.getHeight() / 2);
    }

    @Override
    public String toString() {
        return String.format("pln::<%s;%s;%f;%f;%f;%f;%d>",
                m_Id,
                StringUtils.toBase64(m_Position.toString()),
                m_Roll,
                m_Pitch,
                m_Yaw,
                m_Speed,
                Flags
        );
    }

    public static void draw(GraphicsContext ctx, IAirplane airplane) {
        final Vector3 direction = airplane.getDirection();
        final String apId = airplane.getId();
        final Rectangle rect = airplane.getBoundsRect();

        final Image img;
        switch (airplane.getCdState()) {
            case FLAG_CD_PANIC:
                img = Resources.Images.airplane_danger;
                break;
            case FLAG_CD_WARN:
                img = Resources.Images.airplane_warn;
                break;
            default:
                img = Resources.Images.airplane;
                break;
        }

        final double xyRotAngle = Math.toDegrees(Math.atan2(direction.Y, direction.X));

        ctx.save();
            ctx.translate(rect.getX(), rect.getY());

            ctx.setFill(Color.BLACK);
            ctx.fillRect(-5, rect.getHeight(), rect.getWidth() + 5, 12);

            ctx.setFont(Font.font("Consolas", 10));
            ctx.setFill(Color.WHITE);
            ctx.fillText(apId, -4.0, rect.getHeight() + 9.0);

            ctx.translate(rect.getWidth() / 2, rect.getHeight() / 2);
            ctx.rotate(xyRotAngle);
            ctx.translate(-rect.getWidth() / 2, -rect.getHeight() / 2);

            final Color shadowColor = (airplane.getDispState() == FLAG_DISP_HIGHLIGHTED) ? Color.YELLOW : Color.BLACK;

            ctx.setEffect(new DropShadow(3.0, 0.0, 0.0, shadowColor));
            ctx.drawImage(img, 0, 0, rect.getWidth(), rect.getHeight());
            ctx.setEffect(null);
        ctx.restore();
    }

    public static Airplane fromString(String str) {
        Pattern p = Pattern.compile(RegexPattern);
        Matcher m = p.matcher(str);

        if (m.matches()) {
            return (Airplane) new Airplane()
                    .setId(m.group(1))
                    .setFlags(Integer.parseInt(m.group(7)))
                    .setPosition(Vector3.fromString(StringUtils.fromBase64(m.group(2))))
                    .setRoll(Float.parseFloat(m.group(3)))
                    .setPitch(Float.parseFloat(m.group(4)))
                    .setYaw(Float.parseFloat(m.group(5)))
                    .setSpeed(Float.parseFloat(m.group(6)));
        }

        System.err.println("Invalid serialized Airplane: " + str);
        return null;
    }

    public static List<Airplane> fromStringMultiple(String str) {
        Pattern p = Pattern.compile(RegexPattern);
        Matcher m = p.matcher(str);

        List<Airplane> result = new ArrayList<>();

        while (m.find()) {
            Airplane airplane = (Airplane) new Airplane()
                    .setId(m.group(1))
                    .setFlags(Integer.parseInt(m.group(7)))
                    .setPosition(Vector3.fromString(StringUtils.fromBase64(m.group(2))))
                    .setRoll(Float.parseFloat(m.group(3)))
                    .setPitch(Float.parseFloat(m.group(4)))
                    .setYaw(Float.parseFloat(m.group(5)))
                    .setSpeed(Float.parseFloat(m.group(6)));

            result.add(airplane);
        }

        return result;
    }

}
