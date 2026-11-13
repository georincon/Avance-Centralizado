/*
 * Copyright (c) 2025-2026 European Commission
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
package eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist

import com.eygraber.uri.Url
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.RevokeStatusListToken
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

private const val TOKEN_STATUS_LIST_SEGMENT = "/token_status_list/"

/**
 * El servicio de listas de estado localiza la lista en disco con la ruta `/token_status_list/<país>/<tipo>/<lista>`.
 * La URI pública lleva delante el prefijo del proxy (`/status-list/<emisor>/`), que en disco no existe: se envía desde
 * `/token_status_list/` en adelante. Si la URI no contiene ese segmento no es una lista de este servicio.
 */
internal fun statusListServiceUri(publicUri: String): String {
    val path = runCatching { Url.parse(publicUri).path }.getOrNull() ?: publicUri
    val start = path.indexOf(TOKEN_STATUS_LIST_SEGMENT)
    require(start >= 0) { "La URI no es una lista de estado de este servicio" }
    return path.substring(start)
}

/**
 * La URL de `/set` se deriva de la de `/take` (`TOKENSTATUSLISTSERVICE_SERVICEURL`).
 */
internal fun setUrl(takeUrl: Url): String {
    val url = takeUrl.toString()
    require(url.endsWith("/take")) { "TOKENSTATUSLISTSERVICE_SERVICEURL no termina en /take" }
    return url.removeSuffix("/take") + "/set"
}

fun RevokeStatusListToken(
    httpClient: HttpClient,
    serviceUrl: Url,
    apiKey: ApiKey,
): RevokeStatusListToken {
    val target = setUrl(serviceUrl)
    return RevokeStatusListToken { statusList, index ->
        val uri = statusListServiceUri(statusList)
        httpClient.submitForm(
            target,
            Parameters.build {
                append("idx", index.toString())
                append("uri", uri)
                append("status", "1")
            },
        ) {
            expectSuccess = true
            headers.append("X-API-Key", apiKey.value)
        }
    }
}
