/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.library")
    id("project.wallet.core")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.corelogic"
    defaultConfig {
        // Infraestructura PROPIA de New Wallet (lista de confianza, issuer y wallet provider).
        // Por defecto apuntan a hosts inexistentes (.invalid) para que esta app NUNCA contacte ni
        // modifique la infraestructura de la wallet de Avance. Se fijan con -P o gradle.properties.
        fun endpoint(name: String, path: String): String =
            (findProperty(name) as String?) ?: "https://newwallet.invalid$path"

        // Igual que endpoint(), pero admite "null" como valor: hay listas de
        // confianza que todavia no se publican (los certificados de REGISTRO de
        // partes usuarias) y el SDK acepta que no haya lista.
        fun endpointOrNull(name: String): String {
            val valor = findProperty(name) as String?
            return if (valor.isNullOrBlank()) "null" else "\"$valor\""
        }
        buildConfigField("String", "NW_TL_PID", "\"${endpoint("nw.tl.pid", "/tl/PID/current.json")}\"")
        buildConfigField("String", "NW_TL_PUBEAA", "\"${endpoint("nw.tl.pubeaa", "/tl/PUBEAA/current.json")}\"")
        buildConfigField("String", "NW_TL_WP", "\"${endpoint("nw.tl.wp", "/tl/WP/current.json")}\"")
        // Anclas de las partes usuarias: las publica el mismo srv-tl-publisher local.
        buildConfigField("String", "NW_TL_WRPAC", "\"${endpoint("nw.tl.wrpac", "/tl/RPREG/current.json")}\"")
        buildConfigField("java.lang.String", "NW_TL_WRPRC", endpointOrNull("nw.tl.wrprc"))
        buildConfigField("String", "NW_ISSUER_URL", "\"${endpoint("nw.issuer.url", "/pid-issuer")}\"")
        buildConfigField("String", "NW_CLIENT_ID", "\"${(findProperty("nw.client.id") as String?) ?: "eudiw-abca"}\"")
        buildConfigField("String", "NW_WALLET_PROVIDER_URL", "\"${endpoint("nw.walletprovider.url", "/wallet-provider")}\"")
    }
}

moduleConfig {
    module = LibraryModule.CoreLogic
}

dependencies {
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.StorageLogic.path))
    implementation(project(LibraryModule.AuthenticationLogic.path))
    implementation(project(LibraryModule.NetworkLogic.path))

    implementation(libs.androidx.biometric)

    testImplementation(project(LibraryModule.TestLogic.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.CoreLogic.classes,
    excludedPackages = KoverExclusionRules.CoreLogic.packages,
)