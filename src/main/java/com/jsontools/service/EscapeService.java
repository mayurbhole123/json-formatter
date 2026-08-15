package com.jsontools.service;

import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Text-level utilities: JSON escaping, Base64 and percent-encoding. */
@Service
public class EscapeService {

    // ==================================================================
    // JSON escape / unescape
    // ==================================================================

    public String escape(String text, boolean wrapInQuotes, boolean escapeNonAscii) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        if (wrapInQuotes) {
            out.append('"');
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || (escapeNonAscii && c > 0x7E)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        if (wrapInQuotes) {
            out.append('"');
        }
        return out.toString();
    }

    public String unescape(String text) {
        String source = text.trim();
        // Tolerate a fully quoted literal as well as a bare escaped fragment.
        if (source.length() >= 2 && source.startsWith("\"") && source.endsWith("\"")) {
            source = source.substring(1, source.length() - 1);
        }

        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= source.length()) {
                throw new IllegalArgumentException("Input ends with a dangling backslash at position " + i + ".");
            }
            char next = source.charAt(++i);
            switch (next) {
                case '"'  -> out.append('"');
                case '\\' -> out.append('\\');
                case '/'  -> out.append('/');
                case 'b'  -> out.append('\b');
                case 'f'  -> out.append('\f');
                case 'n'  -> out.append('\n');
                case 'r'  -> out.append('\r');
                case 't'  -> out.append('\t');
                case 'u'  -> {
                    if (i + 4 >= source.length()) {
                        throw new IllegalArgumentException("Truncated \\u escape at position " + (i - 1) + ".");
                    }
                    String hex = source.substring(i + 1, i + 5);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("\\u" + hex + " is not a valid escape at position " + (i - 1) + ".");
                    }
                    i += 4;
                }
                default -> throw new IllegalArgumentException(
                        "Unknown escape sequence \\" + next + " at position " + (i - 1) + ".");
            }
        }
        return out.toString();
    }

    // ==================================================================
    // Base64
    // ==================================================================

    public String base64Encode(String text, boolean urlSafe, boolean padding) {
        Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
        if (!padding) {
            encoder = encoder.withoutPadding();
        }
        return encoder.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public String base64Decode(String text) {
        String cleaned = text.replaceAll("\\s", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("No input provided.");
        }
        // Accept either alphabet, padded or not, by normalising to the standard one.
        String normalised = cleaned.replace('-', '+').replace('_', '/');
        int remainder = normalised.length() % 4;
        if (remainder == 1) {
            throw new IllegalArgumentException("Not valid Base64 - the input length cannot be a multiple of 4 plus 1.");
        }
        if (remainder != 0) {
            normalised += "=".repeat(4 - remainder);
        }
        try {
            return new String(Base64.getDecoder().decode(normalised), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not valid Base64: " + e.getMessage());
        }
    }

    // ==================================================================
    // Percent-encoding
    // ==================================================================

    public String urlEncode(String text, boolean queryMode) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        // URLEncoder is form-encoding; a path segment wants %20 and literal sub-delims.
        return queryMode ? encoded : encoded.replace("+", "%20");
    }

    public String urlDecode(String text, boolean queryMode) {
        try {
            // In path mode a literal '+' is just a plus, so protect it from the decoder.
            String source = queryMode ? text : text.replace("+", "%2B");
            return URLDecoder.decode(source, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not valid percent-encoding: " + e.getMessage());
        }
    }
}
