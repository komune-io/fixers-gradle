type Nullable<T> = T | null | undefined
export declare interface KtMutableSet<E> extends KtSet<E>/*, MutableCollection<E> */ {
    asJsSetView(): Set<E>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtMutableSet": unique symbol;
    } & KtSet<any>["__doNotUseOrImplementIt"];
}
export declare namespace KtMutableSet {
    function fromJsSet<E>(set: ReadonlySet<E>): KtMutableSet<E>;
}
export declare interface KtMutableList<E> extends KtList<E>/*, MutableCollection<E> */ {
    asJsArrayView(): Array<E>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtMutableList": unique symbol;
    } & KtList<any>["__doNotUseOrImplementIt"];
}
export declare namespace KtMutableList {
    function fromJsArray<E>(array: ReadonlyArray<E>): KtMutableList<E>;
}
export declare interface KtMutableMap<K, V> extends KtMap<K, V> {
    asJsMapView(): Map<K, V>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtMutableMap": unique symbol;
    } & KtMap<any, any>["__doNotUseOrImplementIt"];
}
export declare namespace KtMutableMap {
    function fromJsMap<K, V>(map: ReadonlyMap<K, V>): KtMutableMap<K, V>;
}
export declare interface Base1 {
    readonly one: string;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.Base1": unique symbol;
    };
}
export declare interface Base2 {
    readonly two: string;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.Base2": unique symbol;
    };
}
export declare class TwoParents implements Base1, Base2 {
    constructor(one: string, two: string);
    get one(): string;
    get two(): string;
    readonly __doNotUseOrImplementIt: Base2["__doNotUseOrImplementIt"] & Base1["__doNotUseOrImplementIt"];
}
export declare namespace TwoParents {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => TwoParents;
    }
}
export declare class MutableDto {
    constructor(ml: KtMutableList<string>, ms: KtMutableSet<string>, mm: KtMutableMap<string, number>, nullKey: KtMap<Nullable<string>, number>);
    get ml(): KtMutableList<string>;
    get ms(): KtMutableSet<string>;
    get mm(): KtMutableMap<string, number>;
    get nullKey(): KtMap<Nullable<string>, number>;
    get label(): Nullable<string>;
    set label(value: Nullable<string>);
}
export declare namespace MutableDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => MutableDto;
    }
}
