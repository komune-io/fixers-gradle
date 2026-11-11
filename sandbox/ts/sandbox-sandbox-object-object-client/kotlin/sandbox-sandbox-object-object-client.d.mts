/** A Kotlin value the export cannot express, such as an `Instant`: opaque to TypeScript. */
export declare interface KtOpaque<N extends string> {
    readonly __kotlinOpaque: N;
    readonly __doNotUseOrImplementIt: {
        readonly "kotlin.Opaque": unique symbol;
    };
}
/**
 * The JSON a Kotlin value of type `T` is sent as: a list or a set is an array, a map an object
 * (whose keys are optional when they are an enum's names), a `Long` a number, an enum its name,
 * an opaque value a string, and a nullable property may be absent. The exported types describe
 * the Kotlin objects; a body read from or written to the wire is a `Json<T>`, and the compiled
 * code accepts none: an instance of an exported class that implements an exported interface, or
 * of a data class, is refused at any depth.
 *
 * What it does not say: a sealed or polymorphic type carries no discriminant here, so a
 * serializer's `type` field is not typed. A nullable property may be absent: F2's Kotlin client
 * omits nulls (`explicitNulls = false`), Jackson and a default kotlinx `Json` write `null`. A
 * reader that requires the property refuses the JSON that omits it: a default kotlinx `Json`
 * does, Jackson and F2's `Json` read it as `null`. An instance of a class that is neither of the
 * two above is not told apart from a plain object, and a `Long` beyond 2^53 loses its precision
 * as a JavaScript number.
 */
export type Json<T> =
    T extends { asJsReadonlyArrayView(): ReadonlyArray<infer E> } ? Json<E>[]
    : T extends { asJsReadonlySetView(): ReadonlySet<infer E> } ? Json<E>[]
    : T extends { asJsReadonlyMapView(): ReadonlyMap<infer K, infer V> } ? JsonMap<JsonKey<K>, Json<V>>
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
// A map holds the entries it has: keys out of a finite set, such as an enum's names, are optional.
type JsonMap<K extends string | number, V> =
    string extends K ? Record<string, V> : number extends K ? Record<number, V> : { [P in K]?: V };
type JsonField<T, P extends keyof T> =
    P extends "__doNotUseOrImplementIt" ? never : T[P] extends (...args: never[]) => unknown ? never : P;
type JsonFields<T> = { [P in keyof T]-?: JsonField<T, P> }[keyof T];
type JsonOptional<T> = {
    [P in JsonFields<T>]-?: null extends T[P] ? P : undefined extends T[P] ? P : never
}[JsonFields<T>];
// What a Kotlin object has and JSON never does: the brand of an exported interface, the members of a data class.
type JsonObject<T> =
    { [P in Exclude<JsonFields<T>, JsonOptional<T>>]: Json<T[P]> } & { [P in JsonOptional<T>]?: Json<T[P]> }
    & { readonly [P in "__doNotUseOrImplementIt" | Exclude<"equals" | "hashCode", JsonFields<T>>]?: never };
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
    readonly byShape: KtMap<Shape, number>;
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
    constructor(tags: KtList<string>, owners: KtSet<string>, counts: KtMap<string, KtList<bigint>>, byShape: KtMap<Shape, number>, total: bigint, shape: Shape, level: Level, createdAt: KtOpaque<"Instant">, note: Nullable<string>);
    get tags(): KtList<string>;
    get owners(): KtSet<string>;
    get counts(): KtMap<string, KtList<bigint>>;
    get byShape(): KtMap<Shape, number>;
    get total(): bigint;
    get shape(): Shape;
    get level(): Level;
    get createdAt(): KtOpaque<"Instant">;
    get note(): Nullable<string>;
    copy(tags?: KtList<string>, owners?: KtSet<string>, counts?: KtMap<string, KtList<bigint>>, byShape?: KtMap<Shape, number>, total?: bigint, shape?: Shape, level?: Level, createdAt?: KtOpaque<"Instant">, note?: Nullable<string>): Family;
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
export declare class Page<T> {
    constructor(total: number, items: KtList<T>);
    get total(): number;
    get items(): KtList<T>;
    copy(total?: number, items?: KtList<T>): Page<T>;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<unknown>): boolean;
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
export declare class ObjectModel implements ObjectModelDTO {
    constructor(id: string, name: string);
    get id(): string;
    get name(): string;
    copy(id?: string, name?: string): ObjectModel;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<unknown>): boolean;
    readonly __doNotUseOrImplementIt: ObjectModelDTO["__doNotUseOrImplementIt"];
}
export declare namespace ObjectModel {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ObjectModel;
    }
}
export declare function sampleObjectModel(): ObjectModel;
export declare function countTags(family: FamilyDTO): number;