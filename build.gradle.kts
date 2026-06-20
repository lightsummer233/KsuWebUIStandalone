// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.withType<UpdateDaemonJvm> {
    languageVersion = JavaLanguageVersion.of(25)
    vendor = JvmVendorSpec.AZUL
}
