// Nashorn / GraalJS (js.nashorn-compat) が提供するグローバルの型宣言 (エディタ補完用。実行には影響しない)

declare const Java: {
    type(className: string): any;
    from(javaArrayOrList: any): any[];
    to(jsArray: any[], javaType?: string | any): any;
    extend(...types: any[]): any;
    super(obj: any): any;
    isJavaObject(obj: any): boolean;
    isType(obj: any): boolean;
    typeName(obj: any): string | undefined;
    synchronized(fn: Function, lock?: any): Function;
};
declare class JavaImporter {
    constructor(...packagesOrClasses: any[]);
}
declare const Packages: any;
declare const java: any;
declare const javax: any;

declare function print(...args: any[]): void;
declare function load(pathOrUrl: string | object): any;
declare function loadWithNewGlobal(pathOrUrl: string | object, ...args: any[]): any;
declare function importPackage(...packages: any[]): void;
declare function importClass(...classes: any[]): void;

declare const __FILE__: string;
declare const __DIR__: string;
declare const __LINE__: number;
declare const arguments: string[];

// --scripting 時のみ
declare const $ARG: string[];
declare const $ENV: { [name: string]: string };
declare function $EXEC(command: string, input?: string): string;
declare let $OUT: string;
declare let $ERR: string;
declare let $EXIT: number;

// 本番システムが注入するオブジェクト (開発環境では ScriptRunner が DevDtm を注入する)
declare const dtm: {
    /** スクリプトルート配下の `<name>.js` を読み込み、呼び出し元と同じグローバルで実行する */
    require(name: string): any;
};
