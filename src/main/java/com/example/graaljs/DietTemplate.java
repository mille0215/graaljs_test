package com.example.graaljs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.graalvm.polyglot.Source;

/**
 * スクリプト中の {@code [DIEt]...[/DIEt]} を {@code dsm.dwrite("...");} に変換して読み込む。
 *
 * <p>囲まれたテキストの改行は文字列の行継続として残すので、変換後も行番号は元のファイルと一致する。
 * 文字列リテラル・コメント・正規表現リテラルの中のタグは変換しない。
 */
final class DietTemplate {

    private static final String OPEN = "[DIEt]";
    private static final String CLOSE = "[/DIEt]";
    /** 直後の / を除算ではなく正規表現の開始とみなす文字 */
    private static final String BEFORE_REGEX = "(,=:[!&|?{;+-*%<>~^";

    private DietTemplate() {
    }

    /** ファイルのパスは保ったまま内容だけ差し替えるので、ブレークポイントは元のファイルに対して効く。 */
    static Source load(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return Source.newBuilder("js", file).content(expand(content, file)).build();
    }

    static String expand(String src, File file) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        char prev = 0;
        int i = 0;
        while (i < n) {
            if (src.startsWith(OPEN, i)) {
                int close = src.indexOf(CLOSE, i + OPEN.length());
                if (close < 0) {
                    throw new IllegalArgumentException(OPEN + " is not closed: " + position(src, i, file));
                }
                out.append("dsm.dwrite(\"");
                appendEscaped(out, src, i + OPEN.length(), close);
                out.append("\");");
                prev = ';';
                i = close + CLOSE.length();
                continue;
            }
            if (src.startsWith(CLOSE, i)) {
                throw new IllegalArgumentException(CLOSE + " without " + OPEN + ": " + position(src, i, file));
            }

            char c = src.charAt(i);
            boolean comment = src.startsWith("//", i) || src.startsWith("/*", i);
            int end;
            if (src.startsWith("//", i)) {
                end = lineEnd(src, i);
            } else if (src.startsWith("/*", i)) {
                int close = src.indexOf("*/", i + 2);
                end = close < 0 ? n : close + 2;
            } else if (c == '"' || c == '\'' || c == '`') {
                end = skipString(src, i);
            } else if (c == '/' && (prev == 0 || BEFORE_REGEX.indexOf(prev) >= 0)) {
                end = skipRegex(src, i);
            } else {
                end = i + 1;
            }
            out.append(src, i, end);
            if (!comment && !Character.isWhitespace(c)) {
                prev = src.charAt(end - 1);
            }
            i = end;
        }
        return out.toString();
    }

    private static void appendEscaped(StringBuilder out, String src, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = src.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n\\\n");
                case '\r' -> {
                    out.append("\\r");
                    // CRLF の行継続は続く \n の側で行う
                    if (i + 1 >= end || src.charAt(i + 1) != '\n') {
                        out.append("\\\r");
                    }
                }
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> out.append(c);
            }
        }
    }

    /** 閉じていない文字列は行末までとする (テンプレートリテラル以外は行をまたがない)。 */
    private static int skipString(String src, int start) {
        char quote = src.charAt(start);
        int n = src.length();
        int i = start + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (quote != '`' && isNewline(c)) {
                return i;
            } else {
                i++;
            }
        }
        return n;
    }

    private static int skipRegex(String src, int start) {
        int n = src.length();
        boolean inClass = false;
        int i = start + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (isNewline(c)) {
                return i;
            } else if (c == '/' && !inClass) {
                return i + 1;
            } else {
                inClass = c == '[' || (inClass && c != ']');
                i++;
            }
        }
        return n;
    }

    private static int lineEnd(String src, int start) {
        int i = start;
        while (i < src.length() && !isNewline(src.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isNewline(char c) {
        return c == '\n' || c == '\r';
    }

    private static String position(String src, int index, File file) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return file + ":" + line;
    }
}
