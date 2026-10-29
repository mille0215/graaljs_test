// Nashorn 時代によくある書き方のサンプル。ブレークポイントを置いて動作確認する用。

// 別ファイルの読み込み (Nashorn の load)。__DIR__ は Nashorn 互換モードで使える
load(__DIR__ + "lib/util.js");

// Java クラスの参照
var ArrayList = Java.type("java.util.ArrayList");
var LocalDate = Java.type("java.time.LocalDate");

var list = new ArrayList();
for (var i = 0; i < 5; i++) {
    list.add(util.square(i));
}
print("list = " + list);

// Java 配列 <-> JS 配列
var javaArray = Java.to([3, 1, 2], "int[]");
java.util.Arrays.sort(javaArray);
print("sorted = " + Java.from(javaArray));

// JavaImporter + with (Nashorn 互換)
var imports = new JavaImporter(java.util, java.time);
with (imports) {
    var map = new HashMap();
    map.put("today", LocalDate.now().toString());
    print("map = " + map);
}

// Rhino/Nashorn 互換の importPackage を使う場合
// load("nashorn:mozilla_compat.js");
// importPackage(java.io);

print("args = " + JSON.stringify(Array.prototype.slice.call(arguments)));
print("greeting: " + util.greet("GraalJS"));
