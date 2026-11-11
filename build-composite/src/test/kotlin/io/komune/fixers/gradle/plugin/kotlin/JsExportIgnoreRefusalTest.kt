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

    @Test
    fun `finds a bare Ignore once JsExport's members are star-imported`() {
        val found = lines("""
            import kotlin.js.JsExport.*

            @JsExport
            class Order {
                @Ignore
                fun hidden() = Unit
                @get:Ignore
                val alsoHidden: Int get() = 1
            }
        """)

        assertThat(found).containsExactly("5: @Ignore", "7: @get:Ignore")
    }

    @Test
    fun `finds an alias of JsExport spelled in non-ASCII letters or between backticks`() {
        val found = lines("""
            import kotlin.js.JsExport as Éx
            import kotlin.js.JsExport as `Ex port`

            class Order {
                @Éx.Ignore
                fun hidden() = Unit
                @`Ex port`.Ignore
                fun alsoHidden() = Unit
                @Éxtra.Ignore
                fun other() = Unit
            }
        """)

        assertThat(found).containsExactly("5: @Éx.Ignore", "7: @`Ex port`.Ignore")
    }

    @Test
    fun `finds a grouped or aliased bare Ignore once JsExport's members are star-imported`() {
        val found = lines("""
            import kotlin.js.JsExport.*

            typealias Skip = Ignore

            class Order {
                @[Ignore]
                fun hidden() = Unit
                @[Suppress("x") Ignore] fun alsoHidden() = Unit
                @get:[ Ignore ]
                val hiddenToo: Int get() = 1
                @[Deprecated("m", ReplaceWith("x")) Ignore] fun deprecated() = Unit
                @`Ignore` fun backticked() = Unit
                @Skip fun aliased() = Unit
                @[Other.Ignore] fun qualifiedElsewhere() = Unit
                fun `Ignore me`() = Unit
            }
        """)

        assertThat(found).containsExactly(
            "3: typealias Skip = Ignore",
            "6: @[Ignore]",
            "8: @[Suppress(\"x\") Ignore] fun alsoHidden() = Unit",
            "9: @get:[ Ignore ]",
            "11: @[Deprecated(\"m\", ReplaceWith(\"x\")) Ignore] fun deprecated() = Unit",
            "12: @`Ignore` fun backticked() = Unit",
        )
    }

    @Test
    fun `leaves a grouped Ignore alone without the star import`() {
        val found = lines("""
            import org.junit.Ignore

            class OrderTest {
                @[Ignore] fun skipped() = Unit
                typealias Skip = Ignore
            }
        """)

        assertThat(found).isEmpty()
    }

    @Test
    fun `finds the annotation cut across two lines, at the line it starts`() {
        val found = lines("""
            class Order {
                @JsExport
                    .Ignore
                fun hidden() = Unit
                @kotlin.js.JsExport.
                Ignore fun alsoHidden() = Unit
            }
        """)

        assertThat(found).containsExactly("2: @JsExport", "5: @kotlin.js.JsExport.")
    }

    @Test
    fun `sees code after a string holding comment openers`() {
        val found = lines("""
            val url = "http://f2.test" ; @JsExport.Ignore val a = 1
            val glob = "src/**/*.kt"
            @JsExport.Ignore val b = 2
            val star = '*' ; val slash = '/' ; @JsExport.Ignore val c = 3
        """)

        assertThat(found).containsExactly(
            "1: val url = \"http://f2.test\" ; @JsExport.Ignore val a = 1",
            "3: @JsExport.Ignore val b = 2",
            "4: val star = '*' ; val slash = '/' ; @JsExport.Ignore val c = 3",
        )
    }

    @Test
    fun `leaves strings alone, raw and templated ones included`() {
        val found = lines("""
            val message = "use JsExport.Ignore? no"
            val escaped = "a \" JsExport.Ignore \" b"
            val raw = ${"\"\"\""}
                @JsExport.Ignore in a raw string, ${'$'}{ "and in a " + "template" } JsExport.Ignore
            ${"\"\"\""}
            val templated = "${'$'}{ mapOf("k" to "JsExport.Ignore").size } JsExport.Ignore ${'$'}{ "}" }"
        """)

        assertThat(found).isEmpty()
    }

    @Test
    fun `sees code inside a template, and after it`() {
        val found = lines("""
            val a = "${'$'}{ run { @JsExport.Ignore val x = 1; x } }"
            val b = "${'$'}{ "}" }" ; @JsExport.Ignore val c = 1
        """)

        assertThat(found).containsExactly(
            "1: val a = \"${'$'}{ run { @JsExport.Ignore val x = 1; x } }\"",
            "2: val b = \"${'$'}{ \"}\" }\" ; @JsExport.Ignore val c = 1",
        )
    }

    @Test
    fun `leaves nested comments alone, and sees the code after them`() {
        val found = lines("""
            /* outer /* inner @JsExport.Ignore */ still a comment @JsExport.Ignore */ @JsExport.Ignore val a = 1
            /** KDoc with `"` and /* nested */ JsExport.Ignore */
            val b = 2
        """)

        assertThat(found).containsExactly(
            "1: /* outer /* inner @JsExport.Ignore */ still a comment @JsExport.Ignore */ @JsExport.Ignore val a = 1",
        )
    }

    @Test
    fun `reads a backticked name holding quotes as a name, and the code after it`() {
        val found = lines("""
            fun `a "quoted" name`() = Unit ; @JsExport.Ignore val a = 1
            fun `it's`() = Unit ; @JsExport.Ignore val b = 2
            @kotlin.js.`JsExport`.Ignore val c = 3
        """)

        assertThat(found).containsExactly(
            "1: fun `a \"quoted\" name`() = Unit ; @JsExport.Ignore val a = 1",
            "2: fun `it's`() = Unit ; @JsExport.Ignore val b = 2",
            "3: @kotlin.js.`JsExport`.Ignore val c = 3",
        )
    }

    @Test
    fun `leaves a backticked name spelling JsExport Ignore alone`() {
        val found = lines("""
            fun probe(): Int {
                val `local JsExport.Ignore name` = 1
                return `local JsExport.Ignore name`
            }
        """)

        assertThat(found).isEmpty()
    }

    @Test
    fun `reads a multi-dollar string, whose single-dollar braces are text`() {
        val found = lines("""
            val literal = ${'$'}${'$'}${"\"\"\""}text ${'$'}{ not "a template } JsExport.Ignore${"\"\"\""}
            @JsExport.Ignore val a = 1
            val templated = ${'$'}${'$'}"${'$'}${'$'}{ run { @JsExport.Ignore val x = 1; x } } ${'$'}{ JsExport.Ignore }"
        """)

        assertThat(found).containsExactly(
            "2: @JsExport.Ignore val a = 1",
            "3: val templated = ${'$'}${'$'}\"${'$'}${'$'}{ run { @JsExport.Ignore val x = 1; x } } ${'$'}{ JsExport.Ignore }\"",
        )
    }
}
