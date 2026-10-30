# 開発者向けドキュメント

この環境そのものを保守する人向けの文書です。環境の構築、配布用 zip の作成、内部の仕組みを説明します。スクリプトの書き方やデバッグの手順は [README.md](README.md) を参照してください。

## 構成

Nashorn 向けに書かれた JavaScript を GraalJS の Nashorn 互換モードで実行し、VSCode の組み込み JS デバッガ (js-debug) から接続します。

| 要素 | 内容 | ライセンス |
|---|---|---|
| JDK | Eclipse Temurin 21 (scoop `java/temurin21-jdk`)。Amazon Corretto 21 でも可 | GPLv2 + CPE |
| GraalJS | `org.graalvm.polyglot:js-community` 25.0.5 | UPL / GPLv2 + CPE (Community Edition) |
| インスペクタ | `org.graalvm.polyglot:inspect-community` 25.0.5 | 同上 |
| ビルド | Maven (scoop `main/maven`) | Apache 2.0 |

> **注意:** `-community` が付かない `org.graalvm.polyglot:js` / `inspect` は Oracle GraalVM (GFTC ライセンス) です。使わないでください。
> GraalVM JDK は不要です。GraalJS は Temurin などの普通の JDK 上でライブラリとして動きます (JIT は効かずインタプリタ実行になりますが、デバッグ用途なら問題ありません)。

```
.vscode/            launch.json (デバッグ構成) / tasks.json (デバッガなし実行、Maven ビルド)
src/main/java/...   ScriptRunner.java  … jjs の代わりになる実行ランナー / DevDtm.java … 本番の dtm の開発用代替
scripts/run.ps1     ビルド (必要な時だけ) + 実行スクリプト
scripts/make-bundle.ps1  オフラインバンドルの作成スクリプト
diet_templates/     デバッグ対象の JS (サンプル: main.js, lib/util.js)
types/              Nashorn グローバル (Java, load, print …) の型宣言。エディタ補完用
```

## オフラインバンドル

社内プロキシなどで JDK のインストールや Maven のダウンロードができない環境向けに、JDK・jar・コンパイル済みクラスを 1 つにまとめた zip を配布します。使う側はインストール、Maven、ネットワーク接続のいずれも不要です。

### 作成

Maven Central と api.adoptium.net に接続できる PC で実行します。

```powershell
.\scripts\make-bundle.ps1                                  # Temurin 21 を取得して dist\graaljs-bundle.zip を作成
.\scripts\make-bundle.ps1 -JdkZip C:\temp\OpenJDK21.zip    # 手元の Temurin の zip を使う場合
```

ダウンロードした JDK は SHA-256 を検証し、`dist\cache` に保存します。`dist\` は Git の管理対象外なので、zip はファイルサーバーなどで配布してください。作成のたびに、その時点の Temurin 21 の最新版が入ります。

依存ライブラリや `ScriptRunner` を変更したら作り直してください。

### 動作

`runtime\jdk` があると、`run.ps1` は `JAVA_HOME` と PATH を無視して同梱の JDK を使い、Maven を呼びません。`DevDtm.java` などの Java ソースを編集した場合は、次回の実行時に同梱の `javac` で自動的にコンパイルします。

バンドルでは Maven を使えないため、`maven: compile` タスクは動きません。`pom.xml` の依存を変更した場合は、作成側の PC で zip を作り直してください。

## 開発環境のセットアップ

JDK 21 以降 (JRE ではなく JDK) と Maven が必要です。JDK は **Eclipse Temurin** を標準とし、Amazon Corretto でも動きます。どちらも OpenJDK のビルドで、ライセンスは GPLv2 + CPE (無償) です。GraalJS は普通の Java ライブラリとして動くので、GraalVM JDK は不要です。

> Oracle JDK (Oracle 配布のもの) はライセンス条件が異なるため使わないでください。

### Eclipse Temurin (標準)

scoop の場合:

```powershell
scoop bucket add java
scoop install java/temurin21-jdk main/maven
```

winget の場合 (Maven は別途 scoop などで入れてください):

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --exact
```

公式サイト (https://adoptium.net/) の MSI インストーラーでも構いません。MSI / winget の場合、インストール時に「Set JAVA_HOME variable」を選ばないと `JAVA_HOME` が設定されないことがあります。

### Amazon Corretto

scoop の場合:

```powershell
scoop bucket add java
scoop install java/corretto21-jdk main/maven
```

winget の場合:

```powershell
winget install --id Amazon.Corretto.21.JDK --exact
```

公式サイト (https://aws.amazon.com/corretto/) の MSI インストーラーでも構いません。

### インストール後の確認

1. **VSCode を再起動**してください (PATH と `JAVA_HOME` を読み直すため)。
2. `mvn -v` の `Java home:` が、入れた JDK を指していることを確認してください。`run.ps1` と Maven はどちらも `JAVA_HOME` の JDK を使います。
3. `Documents` 配下にプロジェクトを置いている場合は、新しい `java.exe` を「コントロールされたフォルダー アクセス」に許可してください (→ トラブルシューティング)。JDK を入れ替えたり更新したりした場合も同じです。

初回実行時に Maven が依存ライブラリを `~/.m2` にダウンロードします。JDK を入れ替えた後は `target\classpath.txt` を削除するか `mvn -q compile` を実行してビルドし直すと確実です。

起動時に Java ソースや `pom.xml` が更新されていれば、`run.ps1` が先に Maven ビルドを行います。

## 内部の仕組み

### デバッグ構成

js-debug が `scripts/run.ps1 -Inspect <開いているファイル>` を直接起動し (出力は**デバッグコンソール**に表示)、GraalJS が `127.0.0.1:9229` で待ち受けを始めたら自動でアタッチします (`launch.json` の `attachSimplePort`)。preLaunchTask は使っていません。

Java 側のコードをデバッグするには、**Java: ScriptRunner (ホスト側デバッグ)** の構成を使います (Extension Pack for Java が必要)。

GraalJS がデバッガに送るスクリプトのハッシュは V8 (Node.js) と形式が違うため、js-debug の内容検証が通らず、同じファイルが読み取り専用の別タブで開いてしまいます。これを避けるため、GraalJS 用の構成には `"enableContentValidation": false` を指定しています。

### ScriptRunner の設定

| 設定 | 内容 |
|---|---|
| `js.ecmascript-version=5` | ES5 モード。ES2015 以降の構文は `SyntaxError`、API は `undefined` になる |
| `js.nashorn-compat=true` | `Java.extend`、`JavaImporter`、`__FILE__` / `__DIR__` / `__LINE__`、`JSAdapter` などの Nashorn 互換機能 |
| `js.syntax-extensions=true` | `for each`、関数の式クロージャ、条件付き `catch` などの Nashorn 構文拡張 |
| `allowAllAccess(true)` | Nashorn と同じように Java クラスへ無制限にアクセスできる |
| `js.scripting` (`-Scripting` 時のみ) | `jjs -scripting` 相当 (`$ARG`、`$EXEC`、バッククォート、ヒアドキュメント) |

GraalJS の既定は ES2022 以降なので、`js.ecmascript-version=5` がないと ES2015 以降の書き方も手元では動いてしまい、本番でだけ失敗します。エディタ補完も `jsconfig.json` の `lib: ["ES5"]` で ES5 の標準 API だけに絞っています。

本番が JDK 9 以降の Nashorn を `--language=es6` 付きで動かしている場合は、`ScriptRunner.java` の `js.ecmascript-version` と `jsconfig.json` の `lib` を変更してください。ただし Nashorn の ES6 対応は一部 (`let` / `const`、アロー関数など) だけで、GraalJS の `6` は ES2015 をすべて受け付けるため、本番で動かない書き方も手元では通るようになります。

### `dtm` の代替 (DevDtm)

本番システムはスクリプトに Java オブジェクト `dtm` を注入しています。本物の `dtm` は Nashorn 用の実装なのでこの環境では使えないため、`ScriptRunner` が開発用の代替 `DevDtm` (`src/main/java/com/example/graaljs/DevDtm.java`) を `dtm` として注入しています。

- ファイルのパス付きで評価しているので、読み込まれた側のファイルにもブレークポイントが効き、ステップ実行で出入りできます。
- エディタ補完用に `types/nashorn-globals.d.ts` に `dtm` の型を宣言しています。メソッドを追加したら、ここにも追加してください。

### エディタの検証設定

VSCode の JS 言語サービスは標準の JavaScript として解析するため、`for each (... in ...)` などの Nashorn 独自の構文を構文エラーとして表示します。`// @ts-ignore` などのコメントでは構文エラーは消せず、特定の構文だけを許可する設定もないので、`.vscode/settings.json` で JavaScript の検証自体を無効にしています。

```jsonc
"[javascript]": {
  "js/ts.validate.enabled": false
}
```

- `[javascript]` に限定しているので、`types/*.d.ts` (TypeScript) の検証は残ります。
- 古い設定名 `javascript.validate.enable` は非推奨です。

## トラブルシューティング

### ビルド時に `Error while writing to classpath file ... classpath.txt` (`NoSuchFileException`) になる

Windows Defender の「コントロールされたフォルダー アクセス」(ランサムウェア防止) が有効だと、`Documents` 配下に置いたこのプロジェクトへ `java.exe` (Maven) がファイルを作成できません。ブロックはイベントログ (`Microsoft-Windows-Windows Defender/Operational`、イベント ID 1123) で確認できます。

対処は次のどちらかです。

- 「Windows セキュリティ」→「ウイルスと脅威の防止」→「ランサムウェア防止の管理」→「コントロールされたフォルダー アクセスによるアプリの許可」で `java.exe` を追加する。scoop の `current` ではなくバージョン付きの実パス (例: scoop の Temurin なら `C:\Users\<user>\scoop\apps\temurin21-jdk\21.0.12-101.0\bin\java.exe`、MSI の Temurin なら `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot\bin\java.exe`、MSI の Corretto なら `C:\Program Files\Amazon Corretto\jdk21.x.x_x\bin\java.exe`) で判定されるため、JDK を更新したら追加し直してください。
- プロジェクトを `Documents` の外 (例: `C:\dev\`) に置く。

### F5 で最後まで実行すると「ターミナル プロセスが終了コード 1 で終了しました」というダイアログが出る

スクリプト自体は正常終了しています (エラー出力もありません)。GraalJS はスクリプトの終了時に先にデバッガとの接続を閉じるため、js-debug がセッションを終了して起動したプロセスを強制終了します。このとき PowerShell がまだ終了処理の途中だと終了コードが 1 になり、統合ターミナルで起動していると VSCode がそれをダイアログで通知します。

このため GraalJS のデバッグ構成では、統合ターミナルを使わず出力をデバッグコンソールに出しています (`"console": "internalConsole"`)。あわせて `"outputCapture": "std"` を指定し、`print` の出力と Java 側のエラー出力 (stderr) がそのまま表示されるようにしています (既定の `"console"` だと stderr が表示されません)。

### F5 のたびに「preLaunchTask を待機しています」というダイアログが出る

以前の構成では preLaunchTask で `run.ps1 -Inspect` を起動していましたが、VSCode がバックグラウンドタスクの準備完了を検知できず、待機ダイアログが出ていました。現在は preLaunchTask を使わず、js-debug が `run.ps1` を直接起動して `attachSimplePort` のポートにアタッチする方式にしているため、このダイアログは出ません。`launch.json` に preLaunchTask を戻さないでください。

## 今後の拡張ポイント

- **Java 側から `engine.put("name", obj)` で値を渡していた場合**: `ScriptRunner` の `context.eval` の前で `context.getBindings("js").putMember("name", obj)` を呼べば同じことができます。
- **既存の Java アプリが `ScriptEngineManager.getEngineByName("nashorn")` を使っている場合**: `js-scriptengine` は依存に入っているので `getEngineByName("graal.js")` に置き換えられます。デバッグするには `-Dpolyglot.inspect=9229 -Dpolyglot.inspect.Path=graaljs -Dpolyglot.inspect.WaitAttached=true` を付けて起動します (この構成は未検証)。
