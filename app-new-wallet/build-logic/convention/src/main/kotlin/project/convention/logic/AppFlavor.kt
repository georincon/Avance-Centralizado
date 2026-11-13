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

package project.convention.logic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

enum class AppFlavor(
    val dimension: FlavorDimension,
    val applicationIdSuffix: String? = null,
    val applicationNameSuffix: String? = null
) {
    Dev(FlavorDimension.contentType, applicationIdSuffix = ".dev"),

    /**
     * La app de DESARROLLO. Para instalar una segunda copia en el telefono sin
     * tocar la que ya esta, se parametriza el sufijo del paquete y el del nombre
     * visible:
     *
     *   ./gradlew :app:assembleDevDebug -PappIdSuffix=.dev.v2 -PappNameSuffix=" V2"
     *
     * Da una app con paquete `co.org.newwallet.app.dev.v2` y nombre "New Wallet V2",
     * que convive con `co.org.newwallet.app.dev`. Android no admite dos apps con el
     * mismo paquete, y por eso el sufijo es lo unico que hay que cambiar: el codigo
     * y los recursos son los mismos.
     *
     * El Wallet Provider valiva el PAQUETE Android de la wallet, asi que el paquete
     * nuevo necesita su propia entrada en
     * PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_<n>_PACKAGENAME
     * (ver eudi-srv-wallet-provider/docker-compose/docker-compose.yaml).
     *
     * Los deep links (`eu.europa.ec.euidi://authorization`, `eudi-openid4ci://authorize`)
     * NO dependen del paquete: son por esquema, asi que los redirectUris del Keycloak
     * no cambian.
     */

    Demo(FlavorDimension.contentType)
}

fun Project.configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: AppFlavor) -> Unit = {}
) {

    val version = getProperty<String>(
        "VERSION_NAME",
        "version.properties"
    ).orEmpty()

    commonExtension.apply {
        flavorDimensions += FlavorDimension.contentType.name
        productFlavors {
            AppFlavor.entries.forEach {
                create(it.name.lowercase()) {
                    dimension = it.dimension.name
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        versionName = version
                        // El sufijo del paquete se puede forzar desde la linea de
                        // ordenes (-PappIdSuffix=.dev.v2) para instalar una segunda
                        // copia de la app sin tocar la que ya esta instalada.
                        val sufijo = (project.findProperty("appIdSuffix") as String?)
                            ?: it.applicationIdSuffix
                        if (sufijo != null) {
                            applicationIdSuffix = sufijo
                        }
                    }
                    // Por el mismo motivo, el sufijo del nombre visible.
                    manifestPlaceholders["appNameSuffix"] =
                        (project.findProperty("appNameSuffix") as String?)
                            ?: it.applicationNameSuffix.orEmpty()
                    addConfigField(
                        "APP_VERSION",
                        version
                    )
                    flavorConfigurationBlock(this, it)
                }
            }
        }

    }
}
