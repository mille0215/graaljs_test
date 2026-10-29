# GraalJS (Nashorn 互換) + VSCode デバッグ環境

Nashorn 向けに書かれた JavaScript を GraalJS の Nashorn 互換モードで実行し、VSCode の組み込み JS デバッガ (js-debug) からブレークポイント・ステップ実行・変数の確認ができるようにする環境です。

## 構成

| 要素 | 内容 | ライセンス |
|---|---|---|
| JDK | OpenJDK 21 (scoop `java/openjdk21`、jdk.java.net の GPL ビルド) | GPLv2 + CPE |
| GraalJS | `org.graalvm.polyglot:js-community` 25.0.5 | UPL / GPLv2 + CPE (Community Edition) |
| インスペクタ | `org.graalvm.polyglot:inspect-community` 25.0.5 | 同上 |
| ビルド | Maven (scoop `main/maven`) | Apache 2.0 |

> **注意:** `-community` が付かない `org.graalvm.polyglot:js` / `inspect` は Oracle GraalVM (GFTC ライセンス) です。使わないでください。
> GraalVM JDK は不要です。GraalJS は普通の OpenJDK 上でライブラリとして動きます (JIT は効かずインタプリタ実行になりますが、デバッグ用途なら問題ありません)。

```
.vscode/            launch.json (デバッグ構成) / tasks.json (デバッガなし実行、Maven ビルド)
src/main/java/...   ScriptRunner.java  … jjs の代わりになる実行ランナー / DevDsm.java … 本番の dsm の開発用代替
scripts/run.ps1     ビルド (必要な時だけ) + 実行スクリプト
diet_templates/     デバッグ対象の JS (サンプル: main.js, lib/util.js)
types/              Nashorn グローバル (Java, load, print …) の型宣言。エディタ補完用
```

## セットアップ

```powershell
scoop bucket add java
scoop install java/openjdk21 main/maven
```

インストール後は **VSCode を再起動**してください (PATH と `JAVA_HOME` を読み直すため)。
初回実行時に Maven が依存ライブラリを `~/.m2` にダウンロードします。

## 使い方

### VSCode でデバッグ

1. デバッグしたい `.js` ファイルを開き、ブレークポイントを置く
2. 「実行とデバッグ」で **GraalJS: 現在のファイルをデバッグ** を選んで F5

js-debug が `scripts/run.ps1 -Inspect <開いているファイル>` を直接起動し (出力は**デバッグコンソール**に表示)、GraalJS が `127.0.0.1:9229` で待ち受けを始めたら自動でアタッチします (`launch.json` の `attachSimplePort`)。preLaunchTask は使っていません。

- デバッガが接続するまでスクリプトは実行されないので、先頭付近のブレークポイントも取りこぼしません。
- `load()` で読み込んだファイルにもブレークポイントを張れます。
- 起動時に Java ソースや `pom.xml` が更新されていれば、`run.ps1` が先に Maven ビルドを行います。
- ポートは 9229 固定です。前回のデバッグの GraalJS が残っているとアタッチできないので、その場合は古いターミナルを閉じてください。

| 構成 | 用途 |
|---|---|
| GraalJS: 現在のファイルをデバッグ | 通常のデバッグ |
| GraalJS: 現在のファイルをデバッグ (先頭で停止) | 1 行目で止める (`-InspectBrk`) |
| GraalJS: 起動済みプロセスにアタッチ | 引数付きなどで別ターミナルから `run.ps1 -Inspect` で起動したものに接続 |
| Java: ScriptRunner (ホスト側デバッグ) | Java 側のコードをデバッグ (Extension Pack for Java が必要) |

デバッガなしで実行するだけなら、`.js` を開いて Ctrl+Shift+B (タスク **graaljs: run current file**) で実行できます。

#### デバッグ構成の補足

- GraalJS がデバッガに送るスクリプトのハッシュは V8 (Node.js) と形式が違うため、js-debug の内容検証が通らず、同じファイルが読み取り専用の別タブで開いてしまいます。これを避けるため、GraalJS 用の構成には `"enableContentValidation": false` を指定しています。

### コマンドラインで実行

```powershell
.\scripts\run.ps1 diet_templates\main.js arg1 arg2          # 普通に実行
.\scripts\run.ps1 -Inspect diet_templates\main.js arg1      # デバッガ待ち受け (→「起動済みプロセスにアタッチ」)
.\scripts\run.ps1 -InspectBrk diet_templates\main.js        # 先頭で停止
.\scripts\run.ps1 -Scripting diet_templates\main.js         # jjs -scripting 相当
.\scripts\run.ps1 -Inspect -Port 9230 diet_templates\main.js
```

Chrome の `devtools://devtools/bundled/js_app.html?ws=127.0.0.1:9229/graaljs` からもデバッグできます。

## トラブルシューティング

### ビルド時に `Error while writing to classpath file ... classpath.txt` (`NoSuchFileException`) になる

Windows Defender の「コントロールされたフォルダー アクセス」(ランサムウェア防止) が有効だと、`Documents` 配下に置いたこのプロジェクトへ `java.exe` (Maven) がファイルを作成できません。ブロックはイベントログ (`Microsoft-Windows-Windows Defender/Operational`、イベント ID 1123) で確認できます。

対処は次のどちらかです。

- 「Windows セキュリティ」→「ウイルスと脅威の防止」→「ランサムウェア防止の管理」→「コントロールされたフォルダー アクセスによるアプリの許可」で `java.exe` を追加する。scoop の `current` ではなくバージョン付きの実パス (例: `C:\Users\<user>\scoop\apps\openjdk21\21.0.2-13\bin\java.exe`) で判定されるため、JDK を更新したら追加し直してください。
- プロジェクトを `Documents` の外 (例: `C:\dev\`) に置く。

### F5 で最後まで実行すると「ターミナル プロセスが終了コード 1 で終了しました」というダイアログが出る

スクリプト自体は正常終了しています (エラー出力もありません)。GraalJS はスクリプトの終了時に先にデバッガとの接続を閉じるため、js-debug がセッションを終了して起動したプロセスを強制終了します。このとき PowerShell がまだ終了処理の途中だと終了コードが 1 になり、統合ターミナルで起動していると VSCode がそれをダイアログで通知します。

このため GraalJS のデバッグ構成では、統合ターミナルを使わず出力をデバッグコンソールに出しています (`"console": "internalConsole"`)。あわせて `"outputCapture": "std"` を指定し、`print` の出力と Java 側のエラー出力 (stderr) がそのまま表示されるようにしています (既定の `"console"` だと stderr が表示されません)。デバッグコンソールではスクリプトに標準入力を渡せない点に注意してください。

### F5 のたびに「preLaunchTask を待機しています」というダイアログが出る

以前の構成では preLaunchTask で `run.ps1 -Inspect` を起動していましたが、VSCode がバックグラウンドタスクの準備完了を検知できず、待機ダイアログが出ていました。現在は preLaunchTask を使わず、js-debug が `run.ps1` を直接起動して `attachSimplePort` のポートにアタッチする方式にしているため、このダイアログは出ません。`launch.json` に preLaunchTask を戻さないでください。

### `for each` などがエディタ上で構文エラー (赤線) になる

VSCode の JS 言語サービスは標準の JavaScript として解析するため、`for each (... in ...)` などの Nashorn 独自の構文を構文エラーとして表示します。`// @ts-ignore` などのコメントでは構文エラーは消せず、特定の構文だけを許可する設定もないので、`.vscode/settings.json` で JavaScript の検証自体を無効にしています。

```jsonc
"[javascript]": {
  "js/ts.validate.enabled": false
}
```

- 補完・定義ジャンプ・ホバーはそのまま使えます。
- `[javascript]` に限定しているので、`types/*.d.ts` (TypeScript) の検証は残ります。
- 本当の構文ミスもエディタ上では表示されなくなります。その場合は実行時に GraalJS が `SyntaxError` を報告します。
- 古い設定名 `javascript.validate.enable` は非推奨です。

### `let` やアロー関数が `SyntaxError` になる / `Map` や `Object.assign` が `undefined` になる

本番の Nashorn (ES5.1) で動かない書き方に開発中に気付けるよう、`ScriptRunner` は GraalJS を ES5 モード (`js.ecmascript-version=5`) で起動しています。GraalJS の既定は ES2022 以降なので、このオプションがないと ES2015 以降の書き方も手元では動いてしまい、本番でだけ失敗します。

| 書き方 | ES5 モードでの結果 |
|---|---|
| `let` / `const`、アロー関数、`class`、テンプレートリテラルなど ES2015 以降の構文 | `SyntaxError` |
| `for...of` | 構文としては通るが、実行時に `TypeError: ... is not iterable` |
| `Map` / `Set` / `Promise` / `Symbol`、`Object.assign`、`Array.prototype.includes` など ES2015 以降の API | `undefined` (呼び出すと `TypeError`) |
| `for each`、`load()`、`Java.type` / `JavaImporter` などの Nashorn 互換機能 | そのまま使える |

これは意図した動作なので、ES5 の書き方に直してください (例: `let` → `var`、アロー関数 → `function`、`for...of` → 添字ループや `for each`)。エディタ補完も `jsconfig.json` の `lib: ["ES5"]` で ES5 の API だけに絞っています。

本番が JDK 9 以降の Nashorn を `--language=es6` 付きで動かしている場合は、`ScriptRunner.java` の `js.ecmascript-version` と `jsconfig.json` の `lib` を変更してください。ただし Nashorn の ES6 対応は一部 (`let` / `const`、アロー関数など) だけで、GraalJS の `6` は ES2015 をすべて受け付けるため、本番で動かない書き方も手元では通るようになります。

## Nashorn 互換について

本番は Nashorn (ES5.1) で動くので、スクリプトは **ES5 + Nashorn 独自の拡張** で書く前提です。この環境はその前提に合わせて、ES2015 以降の書き方はエラーにし、Nashorn の拡張は使えるようにしています。

### ScriptRunner の設定

| 設定 | 内容 |
|---|---|
| `js.ecmascript-version=5` | ES5 モード。ES2015 以降の構文は `SyntaxError`、API は `undefined` になる (→ トラブルシューティング「`let` やアロー関数が `SyntaxError` になる …」) |
| `js.nashorn-compat=true` | `Java.extend`、`JavaImporter`、`__FILE__` / `__DIR__` / `__LINE__`、`JSAdapter` などの Nashorn 互換機能 |
| `js.syntax-extensions=true` | `for each`、関数の式クロージャ、条件付き `catch` などの Nashorn 構文拡張 |
| `allowAllAccess(true)` | Nashorn と同じように Java クラスへ無制限にアクセスできる |
| `js.scripting` (`-Scripting` 時のみ) | `jjs -scripting` 相当 (`$ARG`、`$EXEC`、バッククォート、ヒアドキュメント) |

スクリプト引数はグローバルの `arguments` に入ります (`-Scripting` 時は `$ARG` にも入ります)。エディタ補完も `jsconfig.json` の `lib: ["ES5"]` で ES5 の標準 API だけに絞っています。

### 本番システムの `dsm` オブジェクト (`dsm.require`)

本番システムはスクリプトに Java オブジェクト `dsm` を注入しており、`dsm.require("tests")` で別のスクリプト (`tests.js`) を読み込めます。本物の `dsm` は Nashorn 用の実装なのでこの環境では使えないため、`ScriptRunner` が開発用の代替 `DevDsm` (`src/main/java/com/example/graaljs/DevDsm.java`) を `dsm` として注入しています。

- `dsm.require("<name>")` は、実行したスクリプトのフォルダ (F5 で開いていたファイルのフォルダ) を基準に `<name>.js` を読み込みます。`"lib/util"` のようにサブフォルダも指定できます。
- 読み込んだスクリプトは呼び出し元と同じグローバルで実行されるので、そこで定義した `var` / `function` は呼び出し元からそのまま使えます。
- ファイルのパス付きで評価しているので、**読み込まれた側のファイルにもブレークポイントが効き**、ステップ実行で出入りできます。
- ファイルが見つからない場合は `IllegalArgumentException` (`dsm.require: script not found: ...`) になります。
- エディタ補完用に `types/nashorn-globals.d.ts` に `dsm` の型を宣言しています。

本物の `dsm` に `require` 以外のメソッドがあり、スクリプトから使っている場合は `DevDsm` にも同じ名前のメソッドを追加してください。

### ES5 モードで動作を確認した機能

| 分類 | 機能 |
|---|---|
| 読み込み | `load()`、`load("nashorn:mozilla_compat.js")` + `importPackage` / `importClass` |
| Java 連携 | `Java.type` / `from` / `to` / `extend`、`new java.lang.Runnable({ ... })` によるインタフェースの匿名実装、`JavaImporter` + `with`、`Packages.*` / `java.*` によるパッケージ参照 |
| 構文拡張 | `for each (var x in ...)`、関数の式クロージャ (`function (x) x * x`)、条件付き catch (`catch (e if e instanceof TypeError)`) |
| Nashorn の拡張オブジェクト | `__FILE__` / `__DIR__` / `__LINE__`、`__noSuchProperty__`、`JSAdapter`、`String.prototype.trimLeft` |
| `-Scripting` 時 | `$ARG`、バッククォートによるコマンド実行 (`$EXEC`)、ヒアドキュメント (`<<EOS` … `EOS`、`${...}` の埋め込み) |
| ES5 標準 | `JSON`、`Object.keys`、`Array.prototype.map` など |

### 注意点

- `for each` など Nashorn 独自の構文はエディタ上で構文エラーになるため、JavaScript の検証を無効にしています (→ トラブルシューティング「`for each` などがエディタ上で構文エラー (赤線) になる」)。
- バッククォートは `-Scripting` 時だけ Nashorn と同じくコマンド実行 (`$EXEC`) として扱われます。`-Scripting` なしでは (ES5 にテンプレートリテラルはないので) `SyntaxError` になります。ヒアドキュメントと文字列内の `${...}` も `-Scripting` 時だけ使えます。
- ES5 モードで防げるのは「ES2015 以降の構文・API を使ってしまう」ミスだけです。GraalJS と Nashorn には Java 連携まわり (数値の型変換、オーバーロードの選択など) に細かな挙動の違いが残るので、最終的な確認は本番の Nashorn で行ってください。非互換の詳細は公式のマイグレーションガイドを参照: https://www.graalvm.org/latest/reference-manual/js/NashornMigrationGuide/

## 今後の拡張ポイント

- **Java 側から `engine.put("name", obj)` で値を渡していた場合**: `ScriptRunner` の `context.eval` の前で `context.getBindings("js").putMember("name", obj)` を呼べば同じことができます。
- **既存の Java アプリが `ScriptEngineManager.getEngineByName("nashorn")` を使っている場合**: `js-scriptengine` は依存に入っているので `getEngineByName("graal.js")` に置き換えられます。デバッグするには `-Dpolyglot.inspect=9229 -Dpolyglot.inspect.Path=graaljs -Dpolyglot.inspect.WaitAttached=true` を付けて起動します (この構成は未検証)。
