package FMath;

/**
 * Created by Mike on 7/21/2017.
 */
public class Rotator {

    public float X;
    public float Y;
    public float Z;

    public Rotator() {
        X = 0;
        Y = 0;
        Z = 0;
    }

    public Rotator(float x, float y, float z) {
        X = x;
        Y = y;
        Z = z;
    }

    public Vector3 getRotated(Vector3 vector) {
        return new RotationMatrix(this).getTransformed(vector);
    }

}
