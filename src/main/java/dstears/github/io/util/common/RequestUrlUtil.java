package dstears.github.io.util.common;

public class RequestUrlUtil {
    private RequestUrlUtil() {
    }

    private final static String prefix = "/";

    public static String format(String url) {
        if (url == null) {
            return null;
        }
        if (prefix.equals(url)) {
            return url;
        }
        if (!url.startsWith(prefix)) {
            url = prefix + url;
        }
        if (url.endsWith(prefix)) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
