declare function KtSingleton<T>(): T & (abstract new() => unknown);
export declare interface ObjectModelDTO {
    readonly id: string;
    readonly name: string;
}