package Net;

import Utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Message {

    public static final int HINT_COMMAND = 1 << 0;
    public static final int HINT_LOCK    = 1 << 1;
    public static final int HINT_RELEASE = 1 << 2;
    public static final int HINT_ALERT = 1 << 3;
    public static final int HINT_LOCK_ACK = 1 << 4;
    public static final int HINT_RELEASE_ACK = 1 << 5;
    public static final int HINT_AIRPLANE_LIST = 1 << 10;

    public int Hint;
    public String Data;
    public String From;
    public String To;

    public Message(int hint, String data) {
        this(hint, data, null, null);
    }

    public Message(int hint, String data, String from) {
        this(hint, data, from, null);
    }

    public Message(int hint, String data, String from, String to) {
        Hint = hint;
        Data = data;
        From = from;
        To = to;
    }

    @Override
    public String toString() {
        return String.format("msg::<%d;%s;%s;%s>",
            Hint,
            StringUtils.toBase64(From),
            StringUtils.toBase64(To),
            StringUtils.toBase64(Data)
        );
    }

    public static boolean isMessage(String str) {
        return !StringUtils.isNullOrWhitespace(str) &&
                Pattern.compile("^msg::<(\\d+);([^;]*);([^;]*);([^;]*)>$").matcher(str).matches();
    }

    public static Message fromString(String message) {
        Matcher m = Pattern.compile("^msg::<(\\d+);([^;]*);([^;]*);([^;]*)>$").matcher(message);

        if (m.matches()) {
            return new Message(
                    Integer.parseInt(m.group(1)),       //hint
                    StringUtils.fromBase64(m.group(4)), //data
                    StringUtils.fromBase64(m.group(2)), //from
                    StringUtils.fromBase64(m.group(3))  //to
            );
        }

        System.err.println("Invalid serialized message: " + message);

        return null;
    }

}
