package com.example.graaljs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

/**
 * Nashorn (jjs) の代わりに GraalJS でスクリプトを実行するランナー。
 *
 * <pre>
 * usage: ScriptRunner [options] script.js [args...]
 *   --inspect[=port]         Chrome DevTools Protocol で待ち受ける (localhost, default 9229)
 *   --inspect-brk[=port]     --inspect と同じだが、スクリプトの先頭行で停止する
 *   --scripting              Nashorn の -scripting 相当 ($ARG, $ENV, $EXEC, heredoc 等)
 * </pre>
 *
 * スクリプト引数は Nashorn と同様にグローバル {@code arguments} (scripting 時は {@code $ARG}) で参照できる。
 */
public final class ScriptRunner {

    /** VSCode の launch.json (websocketAddress) と合わせること。 */
    static final String INSPECT_PATH = "graaljs";
    static final String DEFAULT_INSPECT = "9229";

    public static void main(String[] args) {
        String inspect = null;
        boolean suspend = false;
        boolean scripting = false;

        int i = 0;
        for (; i < args.length && args[i].startsWith("--"); i++) {
            String a = args[i];
            if (a.equals("--inspect") || a.equals("--inspect-brk")) {
                inspect = DEFAULT_INSPECT;
                suspend = a.equals("--inspect-brk");
            } else if (a.startsWith("--inspect=") || a.startsWith("--inspect-brk=")) {
                inspect = a.substring(a.indexOf('=') + 1);
                suspend = a.startsWith("--inspect-brk");
            } else if (a.equals("--scripting")) {
                scripting = true;
            } else {
                usage("unknown option: " + a);
            }
        }
        if (i >= args.length) {
            usage("script file is required");
        }
        File script = new File(args[i]).getAbsoluteFile();
        String[] scriptArgs = Arrays.copyOfRange(args, i + 1, args.length);

        Context.Builder builder = Context.newBuilder("js")
                // Nashorn はホスト (Java) へのアクセスが無制限だったので合わせる
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                // 本番の Nashorn は ES5.1 なので、ES2015 以降の構文・API を使ったらここで気付けるようにする
                .option("js.ecmascript-version", "5")
                .option("js.nashorn-compat", "true")
                .option("js.syntax-extensions", "true")
                .option("js.scripting", Boolean.toString(scripting))
                // 非 GraalVM JDK (OpenJDK) ではインタプリタ実行になる旨の警告を抑止
                .option("engine.WarnInterpreterOnly", "false")
                .arguments("js", scriptArgs);

        if (inspect != null) {
            builder.option("inspect", inspect)
                    .option("inspect.Path", INSPECT_PATH)
                    // デバッガが接続するまで実行を開始しない (ブレークポイント設定前に走り切らないように)
                    .option("inspect.WaitAttached", "true")
                    .option("inspect.Suspend", Boolean.toString(suspend));
        }

        int exitCode = 0;
        try (Context context = builder.build()) {
            // 本番システムが注入している dsm の開発用代替。require の読み込み基準は実行したスクリプトのフォルダ
            context.getBindings("js").putMember("dsm", new DevDsm(context, script.getParentFile()));
            Source source = Source.newBuilder("js", script).build();
            context.eval(source);
        } catch (PolyglotException e) {
            exitCode = report(e);
        } catch (Exception e) {
            e.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    private static int report(PolyglotException e) {
        if (e.isExit()) {
            return e.getExitStatus();
        }
        System.err.println(e.isHostException() ? e.asHostException().toString() : e.getMessage());
        List<String> frames = new ArrayList<>();
        for (PolyglotException.StackFrame f : e.getPolyglotStackTrace()) {
            if (f.isGuestFrame()) {
                frames.add("    at " + f);
            }
        }
        frames.forEach(System.err::println);
        return 1;
    }

    private static void usage(String message) {
        System.err.println(message);
        System.err.println("usage: ScriptRunner [--inspect[=port] | --inspect-brk[=port]] [--scripting] script.js [args...]");
        System.exit(2);
    }
}
