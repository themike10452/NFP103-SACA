package Utils;

/**
 * Created by Mike on 7/16/2017.
 */
public class StringUtils {

    public static boolean isNullOrWhitespace(String str) {
        return str == null || str.trim().length() == 0;
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.length() == 0;
    }

}
