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
export declare interface Same {
    readonly fromA: string;
    readonly __doNotUseOrImplementIt: {
        readonly "a.Same": unique symbol;
    };
}
export declare interface Same {
    readonly fromB: number;
    readonly __doNotUseOrImplementIt: {
        readonly "b.Same": unique symbol;
    };
}
export declare class NestedDto {
    constructor(label: string, weight: number);
    get label(): string;
    get weight(): number;
    copy(label?: string, weight?: number): NestedDto;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
}
export declare namespace NestedDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => NestedDto;
    }
}
export declare class SampleDto {
    constructor(s: string, sNull: Nullable<string>, i: number, iNull: Nullable<number>, d: number, bNull: Nullable<boolean>, listSNull: KtList<Nullable<string>>, onChange: Nullable<(p0: string) => void>, listS: KtList<string>, listDtoNull: Nullable<KtList<NestedDto>>, mapSS: KtMap<string, string>, mapSDtoNull: KtMap<string, Nullable<NestedDto>>, setS: KtSet<string>, arrS: Array<string>, nested: NestedDto, hidden: any/* Hidden */, dateIso: string);
    get s(): string;
    get sNull(): Nullable<string>;
    get i(): number;
    get iNull(): Nullable<number>;
    get d(): number;
    get bNull(): Nullable<boolean>;
    get listSNull(): KtList<Nullable<string>>;
    get onChange(): Nullable<(p0: string) => void>;
    get listS(): KtList<string>;
    get listDtoNull(): Nullable<KtList<NestedDto>>;
    get mapSS(): KtMap<string, string>;
    get mapSDtoNull(): KtMap<string, Nullable<NestedDto>>;
    get setS(): KtSet<string>;
    get arrS(): Array<string>;
    get nested(): NestedDto;
    get hidden(): any/* Hidden */;
    get dateIso(): string;
    copy(s?: string, sNull?: Nullable<string>, i?: number, iNull?: Nullable<number>, d?: number, bNull?: Nullable<boolean>, listSNull?: KtList<Nullable<string>>, onChange?: Nullable<(p0: string) => void>, listS?: KtList<string>, listDtoNull?: Nullable<KtList<NestedDto>>, mapSS?: KtMap<string, string>, mapSDtoNull?: KtMap<string, Nullable<NestedDto>>, setS?: KtSet<string>, arrS?: Array<string>, nested?: NestedDto, hidden?: any/* Hidden */, dateIso?: string): SampleDto;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
}
export declare namespace SampleDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SampleDto;
    }
    abstract class Companion extends KtSingleton<Companion.$metadata$.constructor>() {
        private constructor();
    }
    namespace Companion {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            abstract class constructor {
                get VERSION(): string;
                empty(): string;
                private constructor();
            }
        }
    }
}
export declare function listDtos(): KtList<NestedDto>;
export declare function idOf(raw: string): ProductId;
export declare class LongDto {
    constructor(l: bigint, lNull: Nullable<bigint>, lList: KtList<bigint>);
    get l(): bigint;
    get lNull(): Nullable<bigint>;
    get lList(): KtList<bigint>;
    copy(l?: bigint, lNull?: Nullable<bigint>, lList?: KtList<bigint>): LongDto;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
}
export declare namespace LongDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LongDto;
    }
}
export declare function sumLongs(m: KtMap<string, bigint>): bigint;
export declare interface ProductNoRuntimeDTO {
    readonly id: string;
    readonly name: Nullable<string>;
    readonly tags: KtList<string>;
    readonly status: ProductStatus;
}
export declare abstract class XState implements S2State {
    private constructor();
    static get CREATED(): XState & {
        get name(): "CREATED";
        get ordinal(): 0;
    };
    static get VALIDATED(): XState & {
        get name(): "VALIDATED";
        get ordinal(): 1;
    };
    static values(): [typeof XState.CREATED, typeof XState.VALIDATED];
    static valueOf(value: string): XState;
    get name(): "CREATED" | "VALIDATED";
    get ordinal(): 0 | 1;
    get position(): number;
    set position(value: number);
    readonly __doNotUseOrImplementIt: S2State["__doNotUseOrImplementIt"];
}
export declare namespace XState {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => XState;
    }
}
export declare interface WithS2State<S> {
    s2State(): S;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.WithS2State": unique symbol;
    };
}
export declare interface OrderDTO extends WithS2State<XState> {
    readonly id: string;
    readonly status: XState;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.OrderDTO": unique symbol;
    } & WithS2State<any>["__doNotUseOrImplementIt"];
}
export declare function fetchDto(id: string): Promise<NestedDto>;
export declare interface S2State {
    readonly position: number;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.S2State": unique symbol;
    };
}
export declare abstract class ProductStatus implements S2State {
    private constructor();
    static get DRAFT(): ProductStatus & {
        get name(): "DRAFT";
        get ordinal(): 0;
    };
    static get PUBLISHED(): ProductStatus & {
        get name(): "PUBLISHED";
        get ordinal(): 1;
    };
    static get ARCHIVED(): ProductStatus & {
        get name(): "ARCHIVED";
        get ordinal(): 2;
    };
    static values(): [typeof ProductStatus.DRAFT, typeof ProductStatus.PUBLISHED, typeof ProductStatus.ARCHIVED];
    static valueOf(value: string): ProductStatus;
    get name(): "DRAFT" | "PUBLISHED" | "ARCHIVED";
    get ordinal(): 0 | 1 | 2;
    get position(): number;
    get label(): string;
    readonly __doNotUseOrImplementIt: S2State["__doNotUseOrImplementIt"];
}
export declare namespace ProductStatus {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ProductStatus;
    }
}
export declare interface ProductView {
    readonly id: string;
    readonly status: ProductStatus;
    readonly __doNotUseOrImplementIt: {
        readonly "exp.ProductView": unique symbol;
    };
}
export declare interface Shape {
    readonly __doNotUseOrImplementIt: {
        readonly "exp.Shape": unique symbol;
    };
}
export declare namespace Shape {
    class Circle implements Shape {
        constructor(r: number);
        get r(): number;
        copy(r?: number): Shape.Circle;
        toString(): string;
        hashCode(): number;
        equals(other: Nullable<any>): boolean;
        readonly __doNotUseOrImplementIt: Shape["__doNotUseOrImplementIt"];
    }
    namespace Circle {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            const constructor: abstract new () => Circle;
        }
    }
    class Rect implements Shape {
        constructor(w: number, h: number);
        get w(): number;
        get h(): number;
        copy(w?: number, h?: number): Shape.Rect;
        toString(): string;
        hashCode(): number;
        equals(other: Nullable<any>): boolean;
        readonly __doNotUseOrImplementIt: Shape["__doNotUseOrImplementIt"];
    }
    namespace Rect {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            const constructor: abstract new () => Rect;
        }
    }
}
export declare class ProductId {
    constructor(value: string);
    get value(): string;
    toString(): string;
    hashCode(): number;
    equals(other: Nullable<any>): boolean;
}
export declare namespace ProductId {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ProductId;
    }
}