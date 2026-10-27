package org.sitenetsoft.activitystreams.json;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient URI parsing for values received from other servers. A string that {@link URI} rejects is repaired the way a
 * browser would: characters that may not appear in a URI (spaces, {@code "<>\^`{|}}, a stray {@code %} not starting
 * an escape, a second {@code #}) in the path, query and fragment are percent-encoded as UTF-8. The scheme and
 * authority are never rewritten: an authority with such characters cannot be repaired and the value is rejected.
 */
final class LenientUris {

    private static final Pattern SCHEME_AND_AUTHORITY = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*:)(//([^/?#]*))?");
    private static final Pattern AUTHORITY_CHARS = Pattern.compile("[A-Za-z0-9._~:@\\[\\]!$&'()*+,;=%\\-]*");
    private static final String ALLOWED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            + "-._~:/?#[]@!$&'()*+,;=";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private LenientUris() {
    }

    /** The parsed URI, repaired if needed, or null when the string cannot be read as a URI. */
    static URI parse(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            // fall through to the repair
        }
        String repaired = repair(text.strip());
        if (repaired == null) {
            return null;
        }
        try {
            URI uri = new URI(repaired);
            if (uri.getRawAuthority() != null && uri.getHost() == null) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String repair(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        int start = 0;
        Matcher m = SCHEME_AND_AUTHORITY.matcher(text);
        if (m.find()) {
            String authority = m.group(3);
            if (authority != null && !AUTHORITY_CHARS.matcher(authority).matches()) {
                return null;
            }
            out.append(m.group());
            start = m.end();
        }
        boolean fragment = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' && i + 2 < text.length() && isHex(text.charAt(i + 1)) && isHex(text.charAt(i + 2))) {
                out.append(c);
            } else if (c == '#' && !fragment) {
                fragment = true;
                out.append(c);
            } else if (c != '#' && c < 0x80 && ALLOWED.indexOf(c) >= 0) {
                out.append(c);
            } else if (c >= 0x80 && !Character.isISOControl(c) && !Character.isSpaceChar(c)) {
                out.append(c); // URI accepts non-ASCII "other" characters as is
            } else {
                int end = Character.isHighSurrogate(c) && i + 1 < text.length() ? i + 2 : i + 1;
                for (byte b : text.substring(i, end).getBytes(StandardCharsets.UTF_8)) {
                    out.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
                }
                i = end - 1;
            }
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
