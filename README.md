# GraalJS (Nashorn 互換) デバッグ環境

本番の Nashorn で動かす JavaScript を、手元の VSCode でブレークポイントやステップ実行を使ってデバッグするための環境です。

この文書は、配布された zip を使ってスクリプトを書く人向けです。環境の構築や zip の作成については [DEVELOPMENT.md](DEVELOPMENT.md) を参照してください。

## セットアップ

必要なものは VSCode だけです。JDK や Maven のインストール、ネットワーク接続は不要です。

1. 配布された `graaljs-bundle.zip` のブロックを解除します。

   ```powershell
   Unblock-File graaljs-bundle.zip
   ```

2. zip を展開します。展開先は `C:\dev\` など `Documents` の外にしてください (→ [トラブルシューティング](#java-ソースを編集した後に実行するとエラーになる))。
3. VSCode で、展開した `graaljs-bundle` フォルダを開きます。

## 使い方

### VSCode でデバッグ

1. デバッグしたい `.js` ファイルを開き、ブレークポイントを置く
2. 「実行とデバッグ」で **GraalJS: 現在のファイルをデバッグ** を選んで F5

出力は**デバッグコンソール**に表示されます。

- デバッガが接続するまでスクリプトは実行されないので、先頭付近のブレークポイントも取りこぼしません。
- `load()` や `dtm.require()` で読み込んだファイルにもブレークポイントを張れます。
- デバッグコンソールではスクリプトに標準入力を渡せません。

| 構成 | 用途 |
|---|---|
| GraalJS: 現在のファイルをデバッグ | 通常のデバッグ |
| GraalJS: 現在のファイルをデバッグ (先頭で停止) | 1 行目で止める |
| GraalJS: 起動済みプロセスにアタッチ | 引数付きなどで、別のターミナルから `run.ps1 -Inspect` で起動したものに接続 |

### デバッガなしで実行

`.js` を開いて Ctrl+Shift+B を押します。

### コマンドラインで実行

```powershell
.\scripts\run.ps1 diet_templates\main.js arg1 arg2          # 普通に実行
.\scripts\run.ps1 -Inspect diet_templates\main.js arg1      # デバッガ待ち受け (→「起動済みプロセスにアタッチ」)
.\scripts\run.ps1 -InspectBrk diet_templates\main.js        # 先頭で停止
.\scripts\run.ps1 -Scripting diet_templates\main.js         # jjs -scripting 相当
.\scripts\run.ps1 -Inspect -Port 9230 diet_templates\main.js
```

実行ポリシーのエラーが出る場合は、`powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 ...` の形で実行してください。

Chrome の `devtools://devtools/bundled/js_app.html?ws=127.0.0.1:9229/graaljs` からもデバッグできます。

スクリプト引数はグローバルの `arguments` に入ります (`-Scripting` 時は `$ARG` にも入ります)。

## スクリプトの書き方

本番は Nashorn (ES5.1) で動くので、スクリプトは **ES5 + Nashorn 独自の拡張** で書きます。この環境は本番に合わせて、ES2015 以降の書き方をエラーにします。

| 書き方 | この環境での結果 |
|---|---|
| `let` / `const`、アロー関数、`class`、テンプレートリテラルなど ES2015 以降の構文 | `SyntaxError` |
| `for...of` | 構文としては通るが、実行時に `TypeError: ... is not iterable` |
| `Map` / `Set` / `Promise` / `Symbol`、`Object.assign`、`Array.prototype.includes` など ES2015 以降の API | `undefined` (呼び出すと `TypeError`) |
| `for each`、`load()`、`Java.type` / `JavaImporter` などの Nashorn 互換機能 | そのまま使える |

これらのエラーは意図した動作なので、ES5 の書き方に直してください (例: `let` → `var`、アロー関数 → `function`、`for...of` → 添字ループや `for each`)。

### 動作を確認した機能

| 分類 | 機能 |
|---|---|
| 読み込み | `load()`、`load("nashorn:mozilla_compat.js")` + `importPackage` / `importClass` |
| Java 連携 | `Java.type` / `from` / `to` / `extend`、`new java.lang.Runnable({ ... })` によるインタフェースの匿名実装、`JavaImporter` + `with`、`Packages.*` / `java.*` によるパッケージ参照 |
| 構文拡張 | `for each (var x in ...)`、関数の式クロージャ (`function (x) x * x`)、条件付き catch (`catch (e if e instanceof TypeError)`) |
| Nashorn の拡張オブジェクト | `__FILE__` / `__DIR__` / `__LINE__`、`__noSuchProperty__`、`JSAdapter`、`String.prototype.trimLeft` |
| `-Scripting` 時 | `$ARG`、バッククォートによるコマンド実行 (`$EXEC`)、ヒアドキュメント (`<<EOS` … `EOS`、`${...}` の埋め込み) |
| ES5 標準 | `JSON`、`Object.keys`、`Array.prototype.map` など |

### `dtm.require`

本番システムの `dtm` オブジェクトの代わりに、開発用の代替が用意されています。`dtm.require("tests")` で別のスクリプト (`tests.js`) を読み込めます。

- `dtm.require("<name>")` は、実行したスクリプトのフォルダ (F5 で開いていたファイルのフォルダ) を基準に `<name>.js` を読み込みます。`"lib/util"` のようにサブフォルダも指定できます。
- 読み込んだスクリプトは呼び出し元と同じグローバルで実行されるので、そこで定義した `var` / `function` は呼び出し元からそのまま使えます。
- ファイルが見つからない場合は `IllegalArgumentException` (`dtm.require: script not found: ...`) になります。

本物の `dtm` にある `require` 以外のメソッドを使う場合は、`src/main/java/com/example/graaljs/DevDtm.java` に同じ名前のメソッドを追加してください。次回の実行時に自動でコンパイルされます。

### `dsm.dwrite`

`dsm.dwrite("text")` は、渡した文字列をテキストファイルに出力します。

- 出力先は実行したスクリプトのフォルダで、ファイル名は実行日時の `YYYYMMDDHHMMSS.txt`、文字コードは UTF-8 です。
- ファイル名は最初の呼び出しで決まり、同じ実行中の呼び出しはすべて同じファイルに追記されます。
- 別の実行が同じ秒に作ったファイルがある場合は、上書きせず `FileAlreadyExistsException` になります。

### `[DIEt]` タグ

スクリプト中で `[DIEt]` と `[/DIEt]` で囲んだテキストは、`dsm.dwrite` と同じファイルにそのまま出力されます。`dsm.dwrite` と混在でき、書いた順に出力されます。

```javascript
dsm.dwrite("a");
[DIEt]b
c[/DIEt]dsm.dwrite("d");
```

この例の出力は `ab`、改行、`cd` です。

- タグの内側の改行はスクリプトファイルの改行コードのまま出力され、`"` や `\` もそのまま出力されます。
- 文字列リテラル・コメント・正規表現リテラルの中のタグは変換されません。
- タグは文の区切りとして変換されるので、式の途中には書けません。
- `[DIEt]` が閉じていない場合や、`[/DIEt]` だけがある場合は `IllegalArgumentException` になります。
- 変換されるのは実行したスクリプトと `dtm.require` で読み込んだファイルです。`load()` で読み込んだファイルは変換されません。

### 注意点

- バッククォートは `-Scripting` 時だけ Nashorn と同じくコマンド実行 (`$EXEC`) として扱われます。`-Scripting` なしでは `SyntaxError` になります。ヒアドキュメントと文字列内の `${...}` も `-Scripting` 時だけ使えます。
- この環境で防げるのは「ES2015 以降の構文・API を使ってしまう」ミスだけです。GraalJS と Nashorn には Java 連携まわり (数値の型変換、オーバーロードの選択など) に細かな挙動の違いが残るので、**最終的な確認は本番の Nashorn で行ってください**。非互換の詳細は公式のマイグレーションガイドを参照: https://www.graalvm.org/latest/reference-manual/js/NashornMigrationGuide/

## トラブルシューティング

### 構文ミスがエディタ上で赤線にならない

エディタの JavaScript 検証は、Nashorn 独自の構文を誤ってエラーにするため無効にしています。補完・定義ジャンプ・ホバーは使えますが、本当の構文ミスも実行するまで表示されません。構文ミスは実行時に `SyntaxError` として報告されます。

### デバッガがアタッチできない

ポートは 9229 固定です。前回のデバッグの GraalJS が残っているとアタッチできないので、古いターミナルを閉じてください。

### Java ソースを編集した後に実行するとエラーになる

展開先が `Documents` 配下だと、Windows Defender の「コントロールされたフォルダー アクセス」にコンパイル結果の書き込みを止められることがあります。zip を `C:\dev\` など `Documents` の外に展開し直してください。

### `java.exe` が起動できない

会社のセキュリティポリシーで、未承認のプログラムの実行が制限されている可能性があります。情報システム部門に確認してください。
