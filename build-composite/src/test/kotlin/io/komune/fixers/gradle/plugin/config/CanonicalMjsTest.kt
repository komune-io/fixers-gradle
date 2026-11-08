package io.komune.fixers.gradle.plugin.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The Kotlin/JS emitter reorders some declarations between two compilations of one source; the
 * canonical form absorbs those reorders and nothing else.
 */
class CanonicalMjsTest {

    @TestFactory
    fun `should tell a reorder from a change`(): List<DynamicTest> = cases().map { (case, variant, same) ->
        dynamicTest(case) {
            assertThat(canonicalMjs(variant) == canonicalMjs(EMITTED)).`as`(case).isEqualTo(same)
        }
    }

    companion object {
        private val EMITTED = listOf(
            "//region block: pre-declaration",
            "import { VOID } from './x.mjs';",
            "class Channel {}",
            "function allowed(user, product) {",
            "  var open = isOpen(product);",
            "  return open && isActor(user, product);",
            "}",
            "//endregion",
            "class Serializer {",
            "  serialize(encoder, value) {",
            "    encoder.encode(value);",
            "  }",
            "}",
            "//region block: post-declaration",
            "initMetadataForClass(Buffered, 'Buffered', VOID, VOID, [Receive, Send], [1, 4, 0]);",
            "//endregion",
            "//region block: init",
            "Companion_instance_0 = new Companion_0();",
            "Companion_instance_1 = new Companion_1();",
            "//endregion",
            "//region block: exports",
            "export {",
            "  allowed as allowed,",
            "};",
            "//endregion",
        ).joinToString("\n")

        private fun String.edit(old: String, new: String): String {
            check(old in this) { "Case fixture does not contain `$old`." }
            return replace(old, new)
        }

        private fun cases(): List<Triple<String, String, Boolean>> = listOf(
            Triple(
                "top-level declarations reordered",
                EMITTED.edit("class Channel {}\n", "").edit("}\n//endregion", "}\nclass Channel {}\n//endregion"),
                true,
            ),
            Triple(
                "declaration moved to another region",
                EMITTED.edit("class Channel {}\n", "").edit("initMetadataFor", "class Channel {}\ninitMetadataFor"),
                false,
            ),
            Triple("interface list reordered", EMITTED.edit("[Receive, Send]", "[Send, Receive]"), true),
            Triple("rule changed", EMITTED.edit("return open && ", "return "), false),
            Triple(
                "statements swapped in one function",
                EMITTED.edit(
                    "  var open = isOpen(product);\n  return open && isActor(user, product);",
                    "  return open && isActor(user, product);\n  var open = isOpen(product);",
                ),
                false,
            ),
            Triple(
                "initialisations reordered",
                EMITTED.edit(
                    "Companion_instance_0 = new Companion_0();\nCompanion_instance_1 = new Companion_1();",
                    "Companion_instance_1 = new Companion_1();\nCompanion_instance_0 = new Companion_0();",
                ),
                false,
            ),
            Triple("interface renamed", EMITTED.edit("[Receive, Send]", "[Receive, Sink]"), false),
            Triple("interface dropped", EMITTED.edit("[Receive, Send]", "[Receive]"), false),
            Triple("numeric list reordered", EMITTED.edit("[1, 4, 0]", "[4, 1, 0]"), false),
            Triple("declaration removed", EMITTED.edit("class Channel {}\n", ""), false),
            Triple(
                "declaration duplicated",
                EMITTED.edit("class Channel {}\n", "class Channel {}\nclass Channel {}\n"),
                false,
            ),
            Triple("member renamed", EMITTED.edit("  serialize(encoder", "  write(encoder"), false),
        )
    }
}
