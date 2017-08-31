package Entities;

import FMath.Ray;
import FMath.Vector3;
import javafx.scene.shape.Rectangle;

/**
 * Created by Mike on 7/27/2017.
 */
public interface IAirplane {

    IAirplane setPosition(Vector3 position);

    IAirplane setSpeed(float speed);

    IAirplane setAltitude(float altitude);

    IAirplane setPitch(float pitch);

    IAirplane setYaw(float yaw);

    IAirplane setRoll(float roll);

    String getId();

    Vector3 getPosition();

    Vector3 getXyPosition();

    Vector3 getDirection();

    Ray getRay();

    float getSpeed();

    float getAltitude();

    float getPitch();

    float getYaw();

    float getRoll();

    Rectangle getBoundsRect();

    boolean hit(double x, double y);

    String toString();

}
