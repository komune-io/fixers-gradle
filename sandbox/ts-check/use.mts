// The contract as a front reads it: the exported types are the Kotlin objects, Json<T> is the JSON.
// Each @ts-expect-error is a misuse tsc must refuse; an unused one fails the check too.
import { Family, FamilyDTO, Json, Level, Shape, describeFamily, sampleFamily } from "../ts/sandbox-sandbox-object-object-domain/kotlin/sandbox-sandbox-object-object-domain.mjs";
import { Json as ClientJson, countTags } from "../ts/sandbox-sandbox-object-object-client/kotlin/sandbox-sandbox-object-object-client.mjs";
// JSON: every family typed as the wire carries it
const json: Json<FamilyDTO> = { tags: ["a"], owners: ["o"], counts: { x: [1, 2] }, total: 1, shape: "ROUND", level: "HIGH", createdAt: "1970-01-01T00:00:00Z", note: null };
const absent: Json<FamilyDTO> = { tags: [], owners: [], counts: {}, total: 1, shape: "SQUARE", level: "LOW", createdAt: "x" };
const fromClass: Json<Family> = json;
const isRound: boolean = json.shape === "ROUND";
const next: number = json.total + 1;
// @ts-expect-error an unknown enum name
const badEnum: Json<FamilyDTO> = { ...json, shape: "OVAL" };
// @ts-expect-error a Long is a number on the wire
const badLong: Json<FamilyDTO> = { ...json, total: "1" };
// @ts-expect-error a list is an array on the wire
const badList: Json<FamilyDTO> = { ...json, tags: "a" };
// @ts-expect-error a required field
const missing: Json<FamilyDTO> = { owners: [], counts: {}, total: 1, shape: "SQUARE", level: "LOW", createdAt: "x" };
// Kotlin object: each misuse caught at compile time
declare const k: Family;
// @ts-expect-error bigint + number throws a TypeError
const mix = k.total + 1;
// @ts-expect-error an enum instance is never a string
const cmp = k.shape === "ROUND";
const okCmp: boolean = k.shape === Shape.ROUND && k.level.heavierThan(Level.LOW);
// @ts-expect-error a KtList is not an array
const len = k.tags.length;
const tags: ReadonlyArray<string> = k.tags.asJsReadonlyArrayView();
// @ts-expect-error a Kotlin Instant is opaque
const at: string = k.createdAt;
// @ts-expect-error a nullable may be undefined in Kotlin's JS too
const note: string | null = k.note;
// Kotlin code: raw JSON refused, the Kotlin object accepted
// @ts-expect-error JSON passed to Kotlin code
describeFamily(json);
const described: string = describeFamily(sampleFamily());
// @ts-expect-error JSON passed to another package's Kotlin code
countTags(json);
// Two packages, two copies of the declarations: one's Json applies to the other's types
const crossed: ClientJson<FamilyDTO> = json;
export { absent, fromClass, isRound, next, badEnum, badLong, badList, missing, mix, cmp, okCmp, len, tags, at, note, described, crossed };
