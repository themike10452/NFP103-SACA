package Entities;

import Utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Message {

    public static final int TYPE_COMMAND = 0x1;
    public static final int TYPE_LOCK = 0x2;
    public static final int TYPE_RELEASE = 0x3;

    public int Type;
    public String Data;
    public String From;
    public String To;

    public Message(int type, String data) {
        this(type, data, null, null);
    }

    public Message(int type, String data, String from) {
        this(type, data, from, null);
    }

    public Message(int type, String data, String from, String to) {
        Type = type;
        Data = data;
        From = from;
        To = to;
    }

    @Override
    public String toString() {
        return String.format("msg::<%d;%s;%s;%s>",
            Type,
            StringUtils.toBase64(From),
            StringUtils.toBase64(To),
            StringUtils.toBase64(Data)
        );
    }

    public static boolean isMessage(String str) {
        return !StringUtils.isNullOrWhitespace(str) &&
                Pattern.compile("^msg::<(\\d+);([^;]+);([^;]+);([^;]+)>$").matcher(str).matches();
    }

    public static Message fromString(String message) {
        Matcher m = Pattern.compile("^msg::<(\\d+);([^;]+);([^;]+);([^;]+)>$").matcher(message);

        if (m.matches()) {
            return new Message(
                    Integer.parseInt(m.group(1)),
                    StringUtils.fromBase64(m.group(4)),
                    StringUtils.fromBase64(m.group(2)),
                    StringUtils.fromBase64(m.group(3))
            );
        }

        System.err.println("Invalid serialized message: " + message);

        return null;
    }

}
