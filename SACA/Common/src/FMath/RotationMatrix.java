package FMath;

/**
 * Created by Mike on 7/21/2017.
 */
public class RotationMatrix extends Matrix4 {

    public RotationMatrix(Rotator rotator) {
        this(rotator.X, rotator.Y, rotator.Z);
    }

    public RotationMatrix(float x, float y, float z) {
        final float cx = (float)Math.cos(Math.toRadians(x));
        final float cy = (float)Math.cos(Math.toRadians(y));
        final float cz = (float)Math.cos(Math.toRadians(z));

        final float sx = (float)Math.sin(Math.toRadians(x));
        final float sy = (float)Math.sin(Math.toRadians(y));
        final float sz = (float)Math.sin(Math.toRadians(z));

        // RxRyRz
        Data[0][0]	= cy*cz;
        Data[0][1]	= -cy*sz;
        Data[0][2]	= sy;
        Data[0][3]	= 0.f;

        Data[1][0]	= cx*sz+cz*sx*sy;
        Data[1][1]	= cx*cz-sx*sy*sz;
        Data[1][2]	= -cy*sx;
        Data[1][3]	= 0.f;

        Data[2][0]	= sx*sz-cx*cz*sy;
        Data[2][1]	= cz*sx+cx*sy*sz;
        Data[2][2]	= cx*cy;
        Data[2][3]	= 0.f;

        Data[3][0]	= 0.0f; //x trans
        Data[3][1]	= 0.0f; //y trans
        Data[3][2]	= 0.0f; //z trans
        Data[3][3]	= 1.f;
    }

}
