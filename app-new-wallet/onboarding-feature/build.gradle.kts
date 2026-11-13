import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

/**
 * URL del backend de onboarding.
 *
 * Por defecto, el DOMINIO del ecosistema: el onboarding se accede por el proxy unico
 * (srv-nw-nginx) y no publica puertos, asi que la misma APK sirve dentro y fuera de la LAN.
 *
 * Se puede forzar con -PbackendUrl=<url> o, para probar contra el contenedor sin pasar por
 * el proxy, con -PbackendHost=<ip> [-PbackendPort=8195].
 */
fun detectBackendUrl(): String {
    (findProperty("backendUrl") as String?)?.let { return it }
    // Por defecto, el dominio del ecosistema: el onboarding se accede por el
    // proxy unico (srv-nw-nginx) y no publica puertos. La deteccion de la IP
    // local queda como recurso para quien quiera probar contra el contenedor
    // sin pasar por el proxy.
    (findProperty("backendHost") as String?)?.let {
        val port = (findProperty("backendPort") as String?) ?: "8195"
        return "http://$it:$port"
    }
    return "https://civica-desarrollo.avance.org.co/onboarding"
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.onboardingfeature"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "BACKEND_BASE_URL", "\"${detectBackendUrl()}\"")
    }
}

moduleConfig {
    module = LibraryModule.OnboardingFeature
}

dependencies {
    implementation(project(LibraryModule.CommonFeature.path))
    implementation(project(LibraryModule.IssuanceFeature.path))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.OnboardingFeature.classes,
    excludedPackages = KoverExclusionRules.OnboardingFeature.packages,
)
