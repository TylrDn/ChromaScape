plugins {
	java
	checkstyle
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "6.19.0"
}

group = "com.chromascape"
version = "0.5.0"

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

springBoot {
	mainClass.set("com.chromascape.web.ChromaScapeApplication")
}

repositories {
	mavenCentral()
}

configurations.all {
	exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
	// Spring Boot starters
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter")

	// Logging
	implementation("org.springframework.boot:spring-boot-starter-log4j2")
	annotationProcessor("org.apache.logging.log4j:log4j-core:2.24.3")

	// Other libraries
	implementation("com.github.kwhat:jnativehook:2.2.2")
	implementation("commons-io:commons-io:2.14.0")
	implementation("net.java.dev.jna:jna:5.13.0")
	implementation("net.java.dev.jna:jna-platform:5.13.0")
	implementation("org.bytedeco:javacv-platform:1.5.11")
	implementation("org.apache.commons:commons-math3:3.6.1")

	// Testing
	testImplementation(platform("org.junit:junit-bom:5.10.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation("org.mockito:mockito-core")
	testImplementation("org.mockito:mockito-junit-jupiter")
	testImplementation("org.springframework:spring-test:6.1.6")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.springframework.boot:spring-boot-starter-test")

}

tasks.test {
	useJUnitPlatform()
}

checkstyle {
	toolVersion = "10.26.1"
	configFile = file("config/checkstyle/google_checks.xml")
	configProperties["org.checkstyle.google.suppressionfilter.config"] =
		file("config/checkstyle/checkstyle-suppressions.xml").absolutePath
	isIgnoreFailures = false
}

spotless {
	java {
		googleJavaFormat("1.17.0")
		trimTrailingWhitespace()
		endWithNewline()
	}
}

tasks.named("check") {
	dependsOn("spotlessApply", "spotlessCheck", "checkstyleMain")
}

// FORK DIVERGENCE — do not include in an upstream PR.
// Forward every chromascape.* switch to the application JVM. A -D on the gradle command line
// only reaches the Gradle daemon; bootRun forks a new JVM that would not see it. Forwarding the
// whole namespace (not one named key) is what stops the next flag from silently not applying —
// chromascape.captureBridge shipped with exactly that bug after dryRun was fixed.
// Default is dry-run (safe). Pass -Dchromascape.dryRun=false to send real input.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	System.getProperties()
		.stringPropertyNames()
		.filter { it.startsWith("chromascape.") }
		.forEach { systemProperty(it, System.getProperty(it)) }
	systemProperty(
		"chromascape.dryRun",
		providers.systemProperty("chromascape.dryRun").getOrElse("true"))
}

// FORK DIVERGENCE — tests never need a display; make that explicit so a headless CI or the
// replay profile's own tests behave identically to a desktop run.
tasks.test {
	systemProperty("java.awt.headless", "true")
	testLogging {
		events("failed", "skipped")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}
