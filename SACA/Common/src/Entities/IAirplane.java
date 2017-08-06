package Entities;

import FMath.Vector3;
import UI.Viewport;
import javafx.scene.canvas.GraphicsContext;

/**
 * Created by Mike on 7/27/2017.
 */
public interface IAirplane {

    Airplane setPosition(Vector3 position);

    Airplane setDirection(Vector3 direction);

    Airplane setSpeed(float speed);

    Airplane setAltitude(float altitude);

    String getId();

    Vector3 getPosition();

    Vector3 getDirection();

    float getSpeed();

    float getAltitude();

    public boolean hit(double x, double y);

    String toString();

    public void draw(GraphicsContext ctx, Viewport viewport);

}
