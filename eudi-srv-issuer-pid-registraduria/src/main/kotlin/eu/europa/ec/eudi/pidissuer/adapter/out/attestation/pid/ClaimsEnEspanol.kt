/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import eu.europa.ec.eudi.pidissuer.domain.ClaimDefinition
import java.util.Locale

/**
 * Nombres en espanol de los datos del PID para la metadata OpenID4VCI.
 *
 * El `display` de cada dato solo declaraba el ingles, asi que una wallet con el idioma en espanol
 * mostraba "Given Name(s)" o "Birth Date". Son los mismos textos que usa la wallet de Avance
 * (`resources-logic`, `document_claim_*`), para que una credencial se vea igual venga de donde venga.
 *
 * Cada dato se identifica por su nombre (el ultimo elemento de su ruta), que es el mismo en SD-JWT VC
 * y en mdoc.
 */
internal val NombresEnEspanol: Map<String, String> =
    mapOf(
        "address" to "Dirección",
        "age_equal_or_over" to "Verificación de edad",
        "age_over_18" to "Mayor de 18 años",
        "age_over_21" to "Mayor de 21 años",
        "attestation_legal_category" to "Categoría legal de la certificación",
        "birth_date" to "Fecha de nacimiento",
        "birth_family_name" to "Apellido(s) de nacimiento",
        "birth_given_name" to "Nombre(s) de nacimiento",
        "birthdate" to "Fecha de nacimiento",
        "country" to "País",
        "date_of_expiry" to "Fecha de caducidad",
        "date_of_issuance" to "Fecha de emisión",
        "document_number" to "Número de documento",
        "email" to "Correo electrónico",
        "email_address" to "Correo electrónico",
        "estatura_cm" to "Estatura (cm)",
        "expiry_date" to "Fecha de caducidad",
        "family_name" to "Apellido(s)",
        "family_name_birth" to "Apellido(s) de nacimiento",
        "fecha_expedicion" to "Fecha de expedición",
        "formatted" to "Dirección completa",
        "given_name" to "Nombre(s)",
        "given_name_birth" to "Nombre(s) de nacimiento",
        "grupo_sanguineo_rh" to "Grupo sanguíneo y RH",
        "issuance_date" to "Fecha de emisión",
        "issuing_authority" to "Autoridad emisora",
        "issuing_country" to "País emisor",
        "issuing_jurisdiction" to "Jurisdicción emisora",
        "locality" to "Localidad",
        "lugar_expedicion" to "Lugar de expedición",
        "mobile_phone_number" to "Número de teléfono",
        "nationalities" to "Nacionalidad(es)",
        "nationality" to "Nacionalidad",
        "personal_administrative_number" to "Número administrativo personal",
        "phone_number" to "Número de teléfono",
        "picture" to "Foto",
        "place_of_birth" to "Lugar de nacimiento",
        "portrait" to "Retrato",
        "postal_code" to "Código postal",
        "region" to "Región",
        "resident_address" to "Dirección de residencia",
        "resident_city" to "Ciudad de residencia",
        "resident_country" to "País de residencia",
        "resident_postal_code" to "Código postal de residencia",
        "resident_state" to "Provincia/Estado de residencia",
        "resident_street" to "Calle de residencia",
        "sex" to "Sexo",
        "street_address" to "Calle y número",
    )

private val ESPANOL: Locale = Locale.forLanguageTag("es")

/**
 * Anade el espanol al `display` de un dato (y de sus datos anidados) si el dato no lo declara ya.
 * No cambia el ingles ni sustituye un espanol existente, como el de `age_equal_or_over`.
 */
internal fun ClaimDefinition.conEspanol(): ClaimDefinition {
    val nombre = NombresEnEspanol[name]
    val display = if (nombre != null && ESPANOL !in display) display + (ESPANOL to nombre) else display
    return copy(display = display, nested = nested.conEspanol())
}

internal fun List<ClaimDefinition>.conEspanol(): List<ClaimDefinition> = map { it.conEspanol() }
