package FMath;

public class FMath {

    public static final float VERY_SMALL_NUMBER = 1.e-8f;

    public static float mod(float inValue, float dividend )
    {
        if (Math.abs( dividend ) < VERY_SMALL_NUMBER)
        {
            return 0.f;
        }

        float quotient = (int) (inValue / dividend);
        float intPortion = dividend * quotient;

        if (Math.abs( intPortion ) > Math.abs( inValue ))
        {
            intPortion = inValue;
        }

        return inValue - intPortion;
    }

    public static float clampAngle(float degrees) {
        float angle = mod(degrees, 360.0f);

        if (angle < 0.0f)
            angle += 360.0f;

        return angle;
    }

}
