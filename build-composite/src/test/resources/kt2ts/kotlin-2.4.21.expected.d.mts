declare function KtSingleton<T>(): T & (abstract new() => any);
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
    equals(other?: any): boolean;
}
export declare namespace NestedDto {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => NestedDto;
    }
}
export declare class SampleDto {
    constructor(s: string, sNull: string | undefined, i: number, iNull: number | undefined, d: number, bNull: boolean | undefined, listSNull: (string | undefined)[], onChange: ((p0: string) => void) | undefined, listS: string[], listDtoNull: NestedDto[] | undefined, mapSS: Record<string, string>, mapSDtoNull: Record<string, (NestedDto | undefined)>, setS: string[], arrS: Array<string>, nested: NestedDto, hidden: any/* Hidden */, dateIso: string);
    get s(): string;
    get sNull(): string | undefined;
    get i(): number;
    get iNull(): number | undefined;
    get d(): number;
    get bNull(): boolean | undefined;
    get listSNull(): (string | undefined)[];
    get onChange(): ((p0: string) => void) | undefined;
    get listS(): string[];
    get listDtoNull(): NestedDto[] | undefined;
    get mapSS(): Record<string, string>;
    get mapSDtoNull(): Record<string, (NestedDto | undefined)>;
    get setS(): string[];
    get arrS(): Array<string>;
    get nested(): NestedDto;
    get hidden(): any/* Hidden */;
    get dateIso(): string;
    copy(s?: string, sNull?: string, i?: number, iNull?: number, d?: number, bNull?: boolean, listSNull?: (string | undefined)[], onChange?: ((p0: string) => void), listS?: string[], listDtoNull?: NestedDto[], mapSS?: Record<string, string>, mapSDtoNull?: Record<string, (NestedDto | undefined)>, setS?: string[], arrS?: Array<string>, nested?: NestedDto, hidden?: any/* Hidden */, dateIso?: string): SampleDto;
    toString(): string;
    hashCode(): number;
    equals(other?: any): boolean;
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
    constructor(l: number, lNull: number | undefined, lList: number[]);
    get l(): number;
    get lNull(): number | undefined;
    get lList(): number[];
    copy(l?: number, lNull?: number, lList?: number[]): LongDto;
    toString(): string;
    hashCode(): number;
    equals(other?: any): boolean;
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
    readonly name?: string;
    readonly tags: string[];
    readonly status: ProductStatus;
}
export type XState = "CREATED" | "VALIDATED";
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
export type ProductStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
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
        equals(other?: any): boolean;
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
        equals(other?: any): boolean;
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
    equals(other?: any): boolean;
}
export declare namespace ProductId {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ProductId;
    }
}