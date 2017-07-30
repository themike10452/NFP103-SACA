package Utils;

/**
 * Created by Mike on 7/16/2017.
 */
public class RuntimeUtils {

    public static <T> T safeCast(Object o, Class<T> type) {
        if (type.isInstance(o))
            return type.cast(o);
        else
            return null;
    }

}
