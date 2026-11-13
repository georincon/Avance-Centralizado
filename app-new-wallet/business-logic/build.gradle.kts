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
    id("project.rqes.sdk")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.businesslogic"
    defaultConfig {
        // Prestador de firma remota (flavor dev): srv-andes-scd. Igual que la infraestructura de
        // core-logic, por defecto apunta a un host inexistente (.invalid) para que un APK sin
        // configurar no contacte con nada. Se fijan con -P (ver docker-compose/README.md).
        fun prop(name: String, default: String): String = (findProperty(name) as String?) ?: default
        buildConfigField("String", "NW_RQES_URL", "\"${prop("nw.rqes.url", "https://newwallet.invalid/andes-scd")}\"")
        buildConfigField("String", "NW_RQES_CLIENT_ID", "\"${prop("nw.rqes.client.id", "wallet-client")}\"")
        buildConfigField("String", "NW_RQES_CLIENT_SECRET", "\"${prop("nw.rqes.client.secret", "")}\"")
        // Centro de auditoría centralizado (srv-auditoria): a donde la wallet reporta cada actividad.
        // Sin URL o sin clave el reporte queda desactivado (no contacta con nada).
        buildConfigField("String", "NW_AUDITORIA_URL", "\"${prop("nw.auditoria.url", "https://newwallet.invalid/auditoria")}\"")
        // Banco Cívica: de aquí la wallet recoge sola la credencial de cada cuenta que se abre (sin URL, desactivado).
        buildConfigField("String", "NW_BANCO_URL", "\"${prop("nw.banco.url", "https://newwallet.invalid/banco")}\"")
        buildConfigField("String", "NW_AUDITORIA_CLAVE", "\"${prop("nw.auditoria.clave", "")}\"")
    }
}

moduleConfig {
    module = LibraryModule.BusinessLogic
}

dependencies {
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(libs.gson)
    implementation(libs.androidx.appAuth)
    implementation(libs.google.phonenumber)
    implementation(libs.timber)
    implementation(libs.androidx.biometric)
    // Sello de tiempo de las firmas PDF (PdfSignatureTimestamper): CMS y RFC 3161.
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.androidx.datastore.prefs)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.tink)

    testImplementation(project(LibraryModule.TestLogic.path))
    androidTestImplementation(project(LibraryModule.TestLogic.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.BusinessLogic.classes,
    excludedPackages = KoverExclusionRules.BusinessLogic.packages,
)
