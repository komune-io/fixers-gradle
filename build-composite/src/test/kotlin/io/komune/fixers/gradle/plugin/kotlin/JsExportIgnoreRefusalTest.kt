package io.komune.fixers.gradle.plugin.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsExportIgnoreRefusalTest {

    private fun lines(source: String) = jsExportIgnores(source.trimIndent()).map { (line, text) -> "$line: $text" }

    @Test
    fun `finds the annotation on a member, a property and a constructor, with its line`() {
        val found = lines("""
            @JsExport
            class Order @JsExport.Ignore constructor(
                @property:JsExport.Ignore
                val hidden: Hidden,
            ) {
                @kotlin.js.JsExport.Ignore
                fun hidden(): Hidden = Hidden()
                @JsExport . Ignore fun spaced() = Unit
            }
        """)

        assertThat(found).containsExactly(
            "2: class Order @JsExport.Ignore constructor(",
            "3: @property:JsExport.Ignore",
            "6: @kotlin.js.JsExport.Ignore",
            "8: @JsExport . Ignore fun spaced() = Unit",
        )
    }

    @Test
    fun `finds its import and an alias of JsExport`() {
        val found = lines("""
            import kotlin.js.JsExport.Ignore
            import kotlin.js.JsExport as Export

            @Export
            class Order {
                @Export.Ignore
                fun hidden() = Unit
                @Ignore
                fun alsoHidden() = Unit
            }
        """)

        assertThat(found).containsExactly("1: import kotlin.js.JsExport.Ignore", "6: @Export.Ignore")
    }

    @Test
    fun `leaves comments and other Ignore annotations alone`() {
        val found = lines("""
            /**
             * No @JsExport.Ignore here: a façade in jsMain.
             */
            @JsExport
            class Order {
                // was @JsExport.Ignore
                @org.junit.jupiter.api.Disabled @Ignore fun legacy() = Unit /* JsExport.Ignore */
                val other = SomethingElse.Ignore
            }
        """)

        assertThat(found).isEmpty()
    }
}
