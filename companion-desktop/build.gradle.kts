plugins {
    application
}

group = "com.borealis.dosey"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.json:json:20250517")
}

application {
    mainClass.set("com.borealis.dosey.desktop.DoseyCompanionApp")
}
