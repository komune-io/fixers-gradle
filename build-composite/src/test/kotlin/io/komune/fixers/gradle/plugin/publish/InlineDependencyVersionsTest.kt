package io.komune.fixers.gradle.plugin.publish

import groovy.util.Node
import groovy.xml.XmlParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InlineDependencyVersionsTest {

	private fun parsePom(xml: String): Node = XmlParser(false, false).parseText(xml)

	private fun Node.toXmlString(): String {
		val writer = java.io.StringWriter()
		val printer = groovy.xml.XmlNodePrinter(java.io.PrintWriter(writer))
		printer.isPreserveWhitespace = true
		printer.print(this)
		return writer.toString()
	}

	@Test
	fun `inlines versions from dependencyManagement into unversioned dependencies`() {
		val pom = parsePom("""
			<project>
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>io.komune.test</groupId>
			  <artifactId>my-module</artifactId>
			  <version>1.0.0</version>
			  <dependencyManagement>
			    <dependencies>
			      <dependency>
			        <groupId>org.jetbrains.kotlin</groupId>
			        <artifactId>kotlin-reflect</artifactId>
			        <version>2.2.20</version>
			      </dependency>
			      <dependency>
			        <groupId>io.komune.f2</groupId>
			        <artifactId>f2-dsl-cqrs</artifactId>
			        <version>0.28.2</version>
			      </dependency>
			    </dependencies>
			  </dependencyManagement>
			  <dependencies>
			    <dependency>
			      <groupId>org.jetbrains.kotlin</groupId>
			      <artifactId>kotlin-reflect</artifactId>
			      <scope>runtime</scope>
			    </dependency>
			    <dependency>
			      <groupId>io.komune.f2</groupId>
			      <artifactId>f2-dsl-cqrs</artifactId>
			      <scope>compile</scope>
			    </dependency>
			  </dependencies>
			</project>
		""".trimIndent())

		inlineDependencyVersions(pom)

		val result = pom.toXmlString()

		// Both dependencies should now have versions inlined
		assertThat(result).contains("<version>2.2.20</version>")
		assertThat(result).contains("<version>0.28.2</version>")

		// dependencyManagement section should be removed
		assertThat(result).doesNotContain("<dependencyManagement>")
	}

	@Test
	fun `skips BOM imports in dependencyManagement`() {
		val pom = parsePom("""
			<project>
			  <dependencyManagement>
			    <dependencies>
			      <dependency>
			        <groupId>io.komune.f2</groupId>
			        <artifactId>f2-bom</artifactId>
			        <version>0.28.2</version>
			        <type>pom</type>
			        <scope>import</scope>
			      </dependency>
			      <dependency>
			        <groupId>org.jetbrains.kotlin</groupId>
			        <artifactId>kotlin-reflect</artifactId>
			        <version>2.2.20</version>
			      </dependency>
			    </dependencies>
			  </dependencyManagement>
			  <dependencies>
			    <dependency>
			      <groupId>org.jetbrains.kotlin</groupId>
			      <artifactId>kotlin-reflect</artifactId>
			      <scope>runtime</scope>
			    </dependency>
			  </dependencies>
			</project>
		""".trimIndent())

		inlineDependencyVersions(pom)

		val result = pom.toXmlString()

		// kotlin-reflect should have version inlined
		assertThat(result).contains("<version>2.2.20</version>")
		// dependencyManagement removed
		assertThat(result).doesNotContain("<dependencyManagement>")
	}

	@Test
	fun `does not modify dependencies that already have versions`() {
		val pom = parsePom("""
			<project>
			  <dependencyManagement>
			    <dependencies>
			      <dependency>
			        <groupId>org.jetbrains.kotlin</groupId>
			        <artifactId>kotlin-reflect</artifactId>
			        <version>2.2.20</version>
			      </dependency>
			    </dependencies>
			  </dependencyManagement>
			  <dependencies>
			    <dependency>
			      <groupId>org.jetbrains.kotlin</groupId>
			      <artifactId>kotlin-reflect</artifactId>
			      <version>2.1.0</version>
			      <scope>runtime</scope>
			    </dependency>
			  </dependencies>
			</project>
		""".trimIndent())

		inlineDependencyVersions(pom)

		val result = pom.toXmlString()

		// Should keep the existing version (2.1.0), not overwrite with 2.2.20
		assertThat(result).contains("<version>2.1.0</version>")
	}

	@Test
	fun `no-op when no dependencyManagement section`() {
		val pom = parsePom("""
			<project>
			  <dependencies>
			    <dependency>
			      <groupId>org.jetbrains.kotlin</groupId>
			      <artifactId>kotlin-stdlib</artifactId>
			      <version>2.2.20</version>
			    </dependency>
			  </dependencies>
			</project>
		""".trimIndent())

		val before = pom.toXmlString()
		inlineDependencyVersions(pom)
		val after = pom.toXmlString()

		assertThat(after).isEqualTo(before)
	}

	@Test
	fun `removes dependencyManagement when it has only BOM imports`() {
		val pom = parsePom("""
			<project>
			  <dependencyManagement>
			    <dependencies>
			      <dependency>
			        <groupId>io.komune.f2</groupId>
			        <artifactId>f2-bom</artifactId>
			        <version>0.28.2</version>
			        <type>pom</type>
			        <scope>import</scope>
			      </dependency>
			    </dependencies>
			  </dependencyManagement>
			  <dependencies>
			    <dependency>
			      <groupId>io.komune.f2</groupId>
			      <artifactId>f2-dsl-cqrs</artifactId>
			      <scope>compile</scope>
			    </dependency>
			  </dependencies>
			</project>
		""".trimIndent())

		inlineDependencyVersions(pom)

		val result = pom.toXmlString()

		// dependencyManagement should be removed (Gradle's versionMapping handles BOM resolution)
		assertThat(result).doesNotContain("<dependencyManagement>")
	}
}
