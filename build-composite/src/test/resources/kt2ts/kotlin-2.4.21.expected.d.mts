declare function KtSingleton<T>(): T & (abstract new() => unknown);
export declare interface Same {
    readonly fromA: string;
}
export declare interface Same {
    readonly fromB: number;
}
export declare class NestedDto {
    constructor(label: string, weight: number);
    get label(): string;
    get weight(): number;
    copy(label?: string, weight?: number): NestedDto;
    toString(): string;
    hashCode(): number;
    equals(other?: unknown): boolean;
}
export declare namespace NestedDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => NestedDto;
    }
}
export declare class SampleDto {
    constructor(s: string, sNull: string | null, i: number, iNull: number | null, d: number, bNull: boolean | null, listSNull: (string | null)[], onChange: ((p0: string) => void) | null, listS: string[], listDtoNull: NestedDto[] | null, mapSS: Record<string, string>, mapSDtoNull: Record<string, (NestedDto | null)>, setS: string[], arrS: Array<string>, nested: NestedDto, dateIso: string);
    get s(): string;
    get sNull(): string | null;
    get i(): number;
    get iNull(): number | null;
    get d(): number;
    get bNull(): boolean | null;
    get listSNull(): (string | null)[];
    get onChange(): ((p0: string) => void) | null;
    get listS(): string[];
    get listDtoNull(): NestedDto[] | null;
    get mapSS(): Record<string, string>;
    get mapSDtoNull(): Record<string, (NestedDto | null)>;
    get setS(): string[];
    get arrS(): Array<string>;
    get nested(): NestedDto;
    get dateIso(): string;
    copy(s?: string, sNull?: string | null, i?: number, iNull?: number | null, d?: number, bNull?: boolean | null, listSNull?: (string | null)[], onChange?: ((p0: string) => void) | null, listS?: string[], listDtoNull?: NestedDto[] | null, mapSS?: Record<string, string>, mapSDtoNull?: Record<string, (NestedDto | null)>, setS?: string[], arrS?: Array<string>, nested?: NestedDto, dateIso?: string): SampleDto;
    toString(): string;
    hashCode(): number;
    equals(other?: unknown): boolean;
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
export declare function listDtos(): NestedDto[];
export declare function idOf(raw: string): ProductId;
export declare class LongDto {
    constructor(l: number, lNull: number | null, lList: number[]);
    get l(): number;
    get lNull(): number | null;
    get lList(): number[];
    copy(l?: number, lNull?: number | null, lList?: number[]): LongDto;
    toString(): string;
    hashCode(): number;
    equals(other?: unknown): boolean;
}
export declare namespace LongDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LongDto;
    }
}
export declare function sumLongs(m: Record<string, number>): number;
export declare interface ProductNoRuntimeDTO {
    readonly id: string;
    readonly name?: string | null;
    readonly tags: string[];
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
}
export declare namespace XState {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => XState;
    }
}
export declare interface WithS2State<S> {
    s2State(): S;
}
export declare interface OrderDTO extends WithS2State<XState> {
    readonly id: string;
    readonly status: XState;
}
export declare function fetchDto(id: string): Promise<NestedDto>;
export declare interface S2State {
    readonly position: number;
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
}
export declare interface Shape {
}
export declare namespace Shape {
    class Circle implements Shape {
        constructor(r: number);
        get r(): number;
        copy(r?: number): Shape.Circle;
        toString(): string;
        hashCode(): number;
        equals(other?: unknown): boolean;
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
        equals(other?: unknown): boolean;
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
    equals(other?: unknown): boolean;
}
export declare namespace ProductId {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ProductId;
    }
}