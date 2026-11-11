type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare interface KtSet<out E> /* extends Collection<E> */ {
    asJsReadonlySetView(): ReadonlySet<E>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtSet": unique symbol;
    };
}
export declare namespace KtSet {
    function fromJsSet<E>(set: ReadonlySet<E>): KtSet<E>;
}
export declare interface KtList<out E> /* extends Collection<E> */ {
    asJsReadonlyArrayView(): ReadonlyArray<E>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtList": unique symbol;
    };
}
export declare namespace KtList {
    function fromJsArray<E>(array: ReadonlyArray<E>): KtList<E>;
}
export declare interface KtMap<K, out V> {
    asJsReadonlyMapView(): ReadonlyMap<K, V>;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.collections.KtMap": unique symbol;
    };
}
export declare namespace KtMap {
    function fromJsMap<K, V>(map: ReadonlyMap<K, V>): KtMap<K, V>;
}
export declare abstract class Shape {
    private constructor();
    static get ROUND(): Shape & {
        get name(): "ROUND";
        get ordinal(): 0;
    };
    static get SQUARE(): Shape & {
        get name(): "SQUARE";
        get ordinal(): 1;
    };
    static values(): [typeof Shape.ROUND, typeof Shape.SQUARE];
    static valueOf(value: string): Shape;
    get name(): "ROUND" | "SQUARE";
    get ordinal(): 0 | 1;
}
export declare namespace Shape {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Shape;
    }
}
export declare abstract class Level {
    private constructor();
    static get LOW(): Level & {
        get name(): "LOW";
        get ordinal(): 0;
    };
    static get HIGH(): Level & {
        get name(): "HIGH";
        get ordinal(): 1;
    };
    static values(): [typeof Level.LOW, typeof Level.HIGH];
    static valueOf(value: string): Level;
    get name(): "LOW" | "HIGH";
    get ordinal(): 0 | 1;
    get weight(): number;
    heavierThan(other: Level): boolean;
}
export declare namespace Level {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Level;
    }
}
export declare interface FamilyDTO {
    readonly tags: KtList<string>;
    readonly owners: KtSet<string>;
    readonly counts: KtMap<string, KtList<bigint>>;
    readonly byShape: KtMap<Shape, number>;
    readonly total: bigint;
    readonly shape: Shape;
    readonly level: Level;
    readonly createdAt: any/* Instant */;
    readonly note: Nullable<string>;
    readonly __doNotUseOrImplementIt: {
        readonly "io.komune.gradle.sandbox.domain.family.FamilyDTO": unique symbol;
    };
}
export declare class Family implements FamilyDTO {
    constructor(tags: KtList<string>, owners: KtSet<string>, counts: KtMap<string, KtList<bigint>>, byShape: KtMap<Shape, number>, total: bigint, shape: Shape, level: Level, createdAt: any/* Instant */, note: Nullable<string>);
    get tags(): KtList<string>;
    get owners(): KtSet<string>;
    get counts(): KtMap<string, KtList<bigint>>;
    get byShape(): KtMap<Shape, number>;
    get total(): bigint;
    get shape(): Shape;
    get level(): Level;
    get createdAt(): any/* Instant */;
    get note(): Nullable<string>;
    copy(tags?: KtList<string>, owners?: KtSet<string>, counts?: KtMap<string, KtList<bigint>>, byShape?: KtMap<Shape, number>, total?: bigint, shape?: Shape, level?: Level, createdAt?: any/* Instant */, note?: Nullable<string>): Family;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
    readonly __doNotUseOrImplementIt: FamilyDTO["__doNotUseOrImplementIt"];
}
export declare namespace Family {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Family;
    }
    abstract class Companion extends KtSingleton<Companion.$metadata$.constructor>() {
        private constructor();
    }
    namespace Companion {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            abstract class constructor {
                get KIND(): string;
                private constructor();
            }
        }
    }
}
export declare function describeFamily(family: FamilyDTO): string;
export declare function sampleFamily(): Family;
export declare class Page<T> {
    constructor(total: number, items: KtList<T>);
    get total(): number;
    get items(): KtList<T>;
    copy(total?: number, items?: KtList<T>): Page<T>;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
}
export declare namespace Page {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new <T>() => Page<T>;
    }
}
export declare function familyPage(): Page<Family>;
export declare interface Node {
    readonly name: string;
    readonly children: KtList<Node>;
    readonly __doNotUseOrImplementIt: {
        readonly "io.komune.gradle.sandbox.domain.family.Node": unique symbol;
    };
}
export declare interface ObjectModelDTO {
    readonly id: string;
    readonly name: string;
    readonly __doNotUseOrImplementIt: {
        readonly "io.komune.gradle.sandbox.domain.model.ObjectModelDTO": unique symbol;
    };
}