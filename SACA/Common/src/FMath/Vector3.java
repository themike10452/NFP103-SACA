package FMath;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Mike on 7/6/2017.
 */
public class Vector3 implements Serializable {

    public float X;
    public float Y;
    public float Z;

    public Vector3() {
        X = 0.0f;
        Y = 0.0f;
        Z = 0.0f;
    }

    public Vector3(float xPos, float yPos, float zPos) {
        X = xPos;
        Y = yPos;
        Z = zPos;
    }

    public Vector3 set(float xPos, float yPos, float zPos) {
        X = xPos;
        Y = yPos;
        Z = zPos;

        return this;
    }

    public Vector3 set(Vector3 other) {
        X = other.X;
        Y = other.Y;
        Z = other.Z;

        return this;
    }

    public Vector3 setX(float inX) {
        X = inX;

        return this;
    }

    public Vector3 setY(float inY) {
        Y = inY;

        return this;
    }

    public Vector3 setZ(float inZ) {
        Z = inZ;

        return this;
    }

    public Vector3 add(float deltaX, float deltaY, float deltaZ) {
        X += deltaX;
        Y += deltaY;
        Z += deltaZ;

        return this;
    }

    public Vector3 add(Vector3 other) {
        X += other.X;
        Y += other.Y;
        Z += other.Z;

        return this;
    }

    public float Length() {
        return (float)Math.sqrt(X*X + Y*Y + Z*Z);
    }

    public Vector3 getNormalized() {
        float len = Length();

        if (len == 0)
            return this;

        return new Vector3(X / len, Y / len, Z / len);
    }

    @Override
    public String toString() {
        return String.format("vec3::<%f;%f;%f>", X, Y, Z);
    }

    public static Vector3 add(Vector3 lhs, Vector3 rhs) {
        return new Vector3(lhs.X + rhs.X, lhs.Y + rhs.Y, lhs.Z + rhs.Z);
    }

    public static Vector3 multiply(Vector3 v, float scale) {
        return new Vector3(v.X * scale, v.Y * scale, v.Z * scale);
    }

    public static Vector3 fromString(String str) {
        Pattern p = Pattern.compile("vec3::<(-?\\d+(?:\\.\\d+)?);(-?\\d+(?:\\.\\d+)?);(-?\\d+(?:\\.\\d+)?)>");
        Matcher m = p.matcher(str);
        if (m.matches()) {
            return new Vector3(
                    Float.parseFloat(m.group(1)),
                    Float.parseFloat(m.group(2)),
                    Float.parseFloat(m.group(3))
            );
        }
        System.err.println("Invalid serialized Vector3: " + str);
        return null;
    }

}
