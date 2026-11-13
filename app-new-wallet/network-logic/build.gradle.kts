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
    id("project.ktor")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.networklogic"
}

moduleConfig {
    module = LibraryModule.NetworkLogic
}

dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    testImplementation(project(LibraryModule.TestLogic.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.NetworkLogic.classes,
    excludedPackages = KoverExclusionRules.NetworkLogic.packages,
)
// La CA de desarrollo del issuer Registraduría puede regenerarse (scripts/generar-pki-registraduria.sh): se copia la
// vigente a los recursos del flavor dev en cada compilación para que la app siempre confíe en ella.
val syncRegistraduriaCa = tasks.register<Copy>("syncRegistraduriaCa") {
    val ca = rootProject.projectDir.resolve("../eudi-srv-pid-registraduria/docker-compose/pki-dev/ca.crt")
    onlyIf { ca.exists() }
    from(ca)
    into(layout.projectDirectory.dir("src/dev/res/raw"))
    rename { "nw_registraduria_ca.crt" }
}
tasks.named("preBuild") { dependsOn(syncRegistraduriaCa) }
