package FMath;

public class Ray {

    private final Vector3 m_Position;
    private final Vector3 m_Direction;

    public Ray(Vector3 position, Vector3 direction) {
        m_Position = position;
        m_Direction = direction;
    }

    public Vector3 getPosition() {
        return new Vector3(m_Position.X, m_Position.Y, m_Position.Z);
    }

    public Vector3 getDirection() {
        return new Vector3(m_Direction.X, m_Direction.Y, m_Direction.Z);
    }

    public Vector3 nearestPointToRay(Ray other) {
        final Vector3 d1 = m_Direction;
        final Vector3 d2 = other.m_Direction;

        final Vector3 p1p2 = Vector3.subtract(other.m_Position, m_Position);

        final float p = Vector3.dot(d1, d2);
        final float q = Vector3.dot(d1, p1p2);
        final float r = Vector3.dot(d2, p1p2);
        final float s = Vector3.dot(d1, d1);
        final float t = Vector3.dot(d2, d2);

        if (s == 0) {
            System.err.println("Ray 1 direction vector is zero");
            return null;
        }

        if (t == 0) {
            System.err.println("Ray 2 direction vector is zero");
            return null;
        }

        if (p*p == s*t) {
            System.err.println("Ray 1 and Ray 2 are collinear");
            return null;
        }

        final float fact = (-p * r + q * t) / (s * t - p * p);

        return Vector3.add(m_Position, Vector3.multiply(d1, fact)); // nearest point on ray1 to ray2
    }

    public static float shortestDistance(Ray r1, Ray r2) {
        final Vector3 p1 = r1.m_Position;
        final Vector3 p2 = r2.m_Position;

        final Vector3 d1 = r1.m_Direction;
        final Vector3 d2 = r2.m_Direction;

        final Vector3 p1p2 = Vector3.subtract(p2, p1);

        final float p = Vector3.dot(d1, d2);
        final float q = Vector3.dot(d1, p1p2);
        final float r = Vector3.dot(d2, p1p2);
        final float s = Vector3.dot(d1, d1);
        final float t = Vector3.dot(d2, d2);

        if (s == 0) {
            System.err.println("Ray 1 direction vector is zero");
            return Float.MAX_VALUE;
        }

        if (t == 0) {
            System.err.println("Ray 2 direction vector is zero");
            return Float.MAX_VALUE;
        }

        if (p*p == s*t) {
            System.err.println("Ray 1 and Ray 2 are collinear");
            return Float.MAX_VALUE;
        }

        final float fact1 = (-p * r + q * t) / (s * t - p * p);
        final float fact2 = (p * q - r * s) / (s * t - p * p);

        final Vector3 np1 = Vector3.add(p1, Vector3.multiply(d1, fact1)); // nearest point on ray1 to ray2
        final Vector3 np2 = Vector3.add(p2, Vector3.multiply(d2, fact2)); // nearest point on ray2 to ray1

        return Vector3.subtract(np2, np1).getLength();
    }

}
