package UI;

/**
 * Created by Mike on 7/23/2017.
 */
public class Viewport {

    public float Width;
    public float Height;
    public float Padding;

    public Viewport(float width, float height) {
        this(width, height, 0);
    }

    public Viewport(float width, float height, float padding) {
        Width = width;
        Height = height;
        Padding = padding;
    }

}
