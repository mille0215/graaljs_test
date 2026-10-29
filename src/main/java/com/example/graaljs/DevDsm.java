package com.example.graaljs;

import java.io.File;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * 本番システムがスクリプトに注入している {@code dsm} オブジェクトの開発用の代替。
 *
 * <p>{@code dsm.require("tests")} でスクリプトルート配下の {@code tests.js} を読み込み、
 * 呼び出し元と同じグローバルスコープで実行する (読み込んだ側の var / function はそのまま使える)。
 * ファイルから {@link Source} を作って評価するので、読み込まれた側のファイルにもブレークポイントが効く。
 */
public final class DevDsm {

    private final Context context;
    private final File root;

    DevDsm(Context context, File root) {
        this.context = context;
        this.root = root;
    }

    public Value require(String name) throws IOException {
        File file = new File(root, name + ".js");
        if (!file.isFile()) {
            throw new IllegalArgumentException("dsm.require: script not found: " + file);
        }
        return context.eval(Source.newBuilder("js", file).build());
    }
}
