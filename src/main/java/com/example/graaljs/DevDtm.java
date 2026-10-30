package com.example.graaljs;

import java.io.File;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * 本番システムがスクリプトに注入している {@code dtm} オブジェクトの開発用の代替。
 *
 * <p>{@code dtm.require("tests")} でスクリプトルート配下の {@code tests.js} を読み込み、
 * 呼び出し元と同じグローバルスコープで実行する (読み込んだ側の var / function はそのまま使える)。
 * ファイルから {@link Source} を作って評価するので、読み込まれた側のファイルにもブレークポイントが効く。
 */
public final class DevDtm {

    private final Context context;
    private final File root;

    DevDtm(Context context, File root) {
        this.context = context;
        this.root = root;
    }

    public Value require(String name) throws IOException {
        File file = new File(root, name + ".js");
        if (!file.isFile()) {
            throw new IllegalArgumentException("dtm.require: script not found: " + file);
        }
        return context.eval(Source.newBuilder("js", file).build());
    }
}
