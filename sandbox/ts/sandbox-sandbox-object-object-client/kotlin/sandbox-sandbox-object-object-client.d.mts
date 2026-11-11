/** A Kotlin value the export cannot express, such as an `Instant`: opaque to TypeScript. */
export declare interface KtOpaque<N extends string> {
    readonly __kotlinOpaque: N;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.Opaque": unique symbol;
    };
}
/**
 * The JSON a Kotlin value of type `T` is sent as: a list or a set is an array, a map an object, a
 * `Long` a number, an enum its name, an opaque value a string, and a nullable property may be
 * absent. The exported types describe the Kotlin objects; a body read from or written to the wire
 * is a `Json<T>`, and the compiled code accepts none.
 */
export type Json<T> =
    T extends { asJsReadonlyArrayView(): ReadonlyArray<infer E> } ? Json<E>[]
    : T extends { asJsReadonlySetView(): ReadonlySet<infer E> } ? Json<E>[]
    : T extends { asJsReadonlyMapView(): ReadonlyMap<infer K, infer V> } ? Record<JsonKey<K>, Json<V>>
    : T extends { readonly __kotlinOpaque: string } ? string
    : T extends bigint ? number
    : T extends string | number | boolean | null | undefined ? T
    : T extends { readonly name: infer N extends string; readonly ordinal: infer O extends number }
        ? string extends N ? JsonObject<T> : number extends O ? JsonObject<T> : N
    : T extends ReadonlyArray<infer E> ? Json<E>[]
    : T extends (...args: never[]) => unknown ? never
    : T extends object ? JsonObject<T>
    : T;
type JsonKey<K> = Json<K> extends string | number ? Json<K> : string;
type JsonField<T, P extends keyof T> =
    P extends "__doNotUseOrImplementIt" ? never : T[P] extends (...args: never[]) => unknown ? never : P;
type JsonFields<T> = { [P in keyof T]-?: JsonField<T, P> }[keyof T];
type JsonOptional<T> = {
    [P in JsonFields<T>]-?: null extends T[P] ? P : undefined extends T[P] ? P : never
}[JsonFields<T>];
type JsonObject<T> =
    { [P in Exclude<JsonFields<T>, JsonOptional<T>>]: Json<T[P]> } & { [P in JsonOptional<T>]?: Json<T[P]> };
type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => object);
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
    readonly total: bigint;
    readonly shape: Shape;
    readonly level: Level;
    readonly createdAt: KtOpaque<"Instant">;
    readonly note: Nullable<string>;
    readonly __doNotUseOrImplementIt: {
        readonly "io.komune.gradle.sandbox.domain.family.FamilyDTO": unique symbol;
    };
}
export declare class Family implements FamilyDTO {
    constructor(tags: KtList<string>, owners: KtSet<string>, counts: KtMap<string, KtList<bigint>>, total: bigint, shape: Shape, level: Level, createdAt: KtOpaque<"Instant">, note: Nullable<string>);
    get tags(): KtList<string>;
    get owners(): KtSet<string>;
    get counts(): KtMap<string, KtList<bigint>>;
    get total(): bigint;
    get shape(): Shape;
    get level(): Level;
    get createdAt(): KtOpaque<"Instant">;
    get note(): Nullable<string>;
    copy(tags?: KtList<string>, owners?: KtSet<string>, counts?: KtMap<string, KtList<bigint>>, total?: bigint, shape?: Shape, level?: Level, createdAt?: KtOpaque<"Instant">, note?: Nullable<string>): Family;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<unknown>): boolean;
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
export declare interface ObjectModelDTO {
    readonly id: string;
    readonly name: string;
    readonly __doNotUseOrImplementIt: {
        readonly "io.komune.gradle.sandbox.domain.model.ObjectModelDTO": unique symbol;
    };
}
export declare function countTags(family: FamilyDTO): number;