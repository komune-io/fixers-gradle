declare function KtSingleton<T>(): T & (abstract new() => any);
export declare interface ObjectModelDTO {
    readonly id: string;
    readonly name: string;
}