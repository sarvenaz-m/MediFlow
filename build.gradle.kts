plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        listOf(
                "https.proxyHost",
                "https.proxyPort",
                "http.proxyHost",
                "http.proxyPort",
                "javax.net.ssl.trustStore",
            )
            .forEach { key ->
                System.getProperty(key)?.let { systemProperty(key, it) }
            }
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
