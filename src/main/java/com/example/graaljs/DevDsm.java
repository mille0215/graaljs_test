package com.example.graaljs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * スクリプトに注入する {@code dsm} オブジェクトの開発用の実装。
 *
 * <p>{@code dsm.dwrite("text")} で、スクリプトルート配下に実行日時を名前にしたテキストファイル
 * ({@code YYYYMMDDHHMMSS.txt}, UTF-8) を出力する。
 * ファイル名は最初の呼び出しで決まり、同じ実行中の呼び出しはすべて同じファイルに追記する。
 * 別の実行が同じ秒に作ったファイルがある場合は、上書きせず {@code FileAlreadyExistsException} になる。
 */
public final class DevDsm {

    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private final File root;
    private File file;

    DevDsm(File root) {
        this.root = root;
    }

    public void dwrite(String text) throws IOException {
        if (file == null) {
            File created = new File(root, LocalDateTime.now().format(FILE_NAME) + ".txt");
            Files.writeString(created.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            file = created;
        } else {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }
}
