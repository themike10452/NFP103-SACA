package Utils;

import java.util.Base64;

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

    public static String toBase64(String str) {
        return str != null
                ? new String(Base64.getEncoder().encode(str.getBytes()))
                : null;
    }

    public static String fromBase64(String str) {
        return str != null
                ? new String(Base64.getDecoder().decode(str))
                : null;
    }

}
