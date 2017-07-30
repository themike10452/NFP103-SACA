package FMath;

/**
 * Created by Mike on 7/21/2017.
 */
public class Matrix4 {

    public final float Data[][];

    public Matrix4() {
        Data = new float[4][4];
    }

    public Vector3 getTransformed(Vector3 vector) {
        return new Vector3(
                vector.X * Data[0][0] + vector.Y * Data[1][0] + vector.Z * Data[2][0] + Data[3][0],
                vector.X * Data[0][1] + vector.Y * Data[1][1] + vector.Z * Data[2][1] + Data[3][1],
                vector.X * Data[0][2] + vector.Y * Data[1][2] + vector.Z * Data[2][2] + Data[3][2]
        );
    }

}
