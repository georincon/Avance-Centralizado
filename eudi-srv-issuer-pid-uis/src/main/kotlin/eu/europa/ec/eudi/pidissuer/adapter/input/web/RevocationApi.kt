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
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.port.input.RevocationNoticeText
import eu.europa.ec.eudi.pidissuer.port.input.RevokeCredentialsOfSubject
import eu.europa.ec.eudi.pidissuer.port.input.SubjectControls
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import java.security.MessageDigest

/**
 * API interna de administración de revocaciones. Todas las rutas exigen la cabecera `X-Api-Key`.
 *
 *  - `POST /internal/v1/credentials/revoke` `{"subject","reason"?,"block"?}`: revoca las credenciales vigentes del
 *    titular; con `"block": true` además bloquea que se le emitan nuevas.
 *  - `POST /internal/v1/subjects/block` `{"subject","reason"?}` y `POST /internal/v1/subjects/unblock` `{"subject"}`.
 *  - `GET /internal/v1/revocation-notices`: avisos de revocación al titular que siguen sin entregar.
 *  - `POST /internal/v1/revocation-notices/{id}/notified`: confirma que el titular fue avisado por otra vía.
 *
 * Es de uso operativo: el HAProxy responde 404 a todo `/internal/` que venga de fuera de la red docker. Sin
 * `issuer.revocation.api-key` configurada, la API responde 503. Nada de esto borra datos de los titulares.
 */
class RevocationApi(
    private val revokeCredentialsOfSubject: RevokeCredentialsOfSubject,
    private val apiKey: String?,
    private val controls: SubjectControls = SubjectControls.none(),
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            POST(REVOKE, contentType(MediaType.APPLICATION_JSON), ::handleRevoke)
            POST(BLOCK, contentType(MediaType.APPLICATION_JSON), ::handleBlock)
            POST(UNBLOCK, contentType(MediaType.APPLICATION_JSON), ::handleUnblock)
            GET(NOTICES, ::handleNotices)
            POST(NOTICE_DONE, ::handleNoticeDone)
        }

    /** `null` si la clave es válida; si no, la respuesta de error que corresponde. */
    private suspend fun unauthorized(request: ServerRequest): ServerResponse? {
        val expected = apiKey
        if (expected.isNullOrBlank()) return error(HttpStatus.SERVICE_UNAVAILABLE, "REVOCATION_DISABLED")
        val provided = request.headers().firstHeader(API_KEY_HEADER).orEmpty()
        val keyOk = MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
        return if (keyOk) null else error(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY")
    }

    private fun String?.validSubject(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }

    private suspend fun handleRevoke(request: ServerRequest): ServerResponse {
        unauthorized(request)?.let { return it }
        val body = runCatching { request.awaitBody<SubjectRequestTO>() }.getOrNull()
        val subject = body?.subject.validSubject() ?: return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        val reason = body?.reason?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LENGTH) ?: "api-request"

        // Primero el bloqueo: así no se puede pedir una credencial nueva entre la revocación y el bloqueo
        val blocked = body?.block == true && controls.block(subject, reason)
        val result = revokeCredentialsOfSubject(subject, reason)
        return ServerResponse
            .ok()
            .json()
            .bodyValueAndAwait(RevokeResponseTO(result.revoked, result.failed, result.withoutStatus, blocked))
    }

    private suspend fun handleBlock(request: ServerRequest): ServerResponse {
        unauthorized(request)?.let { return it }
        val body = runCatching { request.awaitBody<SubjectRequestTO>() }.getOrNull()
        val subject = body?.subject.validSubject() ?: return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        val reason = body?.reason?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LENGTH) ?: "api-request"
        return ServerResponse.ok().json().bodyValueAndAwait(ChangedTO(controls.block(subject, reason)))
    }

    private suspend fun handleUnblock(request: ServerRequest): ServerResponse {
        unauthorized(request)?.let { return it }
        val body = runCatching { request.awaitBody<SubjectRequestTO>() }.getOrNull()
        val subject = body?.subject.validSubject() ?: return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        return ServerResponse.ok().json().bodyValueAndAwait(ChangedTO(controls.unblock(subject)))
    }

    private suspend fun handleNotices(request: ServerRequest): ServerResponse {
        unauthorized(request)?.let { return it }
        val notices =
            controls.listNotices(true).map {
                NoticeTO(
                    id = it.id,
                    subject = it.subject,
                    credentialTypes = it.credentialTypes,
                    reason = it.reason,
                    message = it.message,
                    revokedAt = RevocationNoticeText.formatDate(it.revokedAt),
                    deadlineAt = RevocationNoticeText.formatDate(it.deadlineAt),
                    status = it.status.name,
                    detail = it.detail,
                )
            }
        return ServerResponse.ok().json().bodyValueAndAwait(notices)
    }

    private suspend fun handleNoticeDone(request: ServerRequest): ServerResponse {
        unauthorized(request)?.let { return it }
        val id = request.pathVariable("id").toLongOrNull() ?: return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        val changed = controls.updateNotice(id, NoticeStatus.MANUAL_DONE, "manual", "confirmado por la API")
        return if (changed) ServerResponse.ok().json().bodyValueAndAwait(ChangedTO(true)) else error(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND")
    }

    private suspend fun error(
        status: HttpStatus,
        code: String,
    ): ServerResponse =
        ServerResponse
            .status(status)
            .json()
            .bodyValueAndAwait(RevocationErrorTO(code))

    companion object {
        const val REVOKE: String = "/internal/v1/credentials/revoke"
        const val BLOCK: String = "/internal/v1/subjects/block"
        const val UNBLOCK: String = "/internal/v1/subjects/unblock"
        const val NOTICES: String = "/internal/v1/revocation-notices"
        const val NOTICE_DONE: String = "/internal/v1/revocation-notices/{id}/notified"
        const val API_KEY_HEADER: String = "X-Api-Key"
        private const val MAX_LENGTH = 255

        /** Rutas internas que el chain de seguridad deja pasar (la autorización real es la `X-Api-Key`). */
        val PATHS: List<String> = listOf(REVOKE, BLOCK, UNBLOCK, NOTICES, "/internal/v1/revocation-notices/*/notified")
    }
}

@Serializable
private data class SubjectRequestTO(
    @SerialName("subject") val subject: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("block") val block: Boolean? = null,
)

@Serializable
private data class RevokeResponseTO(
    @SerialName("revoked") val revoked: Int,
    @SerialName("failed") val failed: Int,
    @SerialName("withoutStatus") val withoutStatus: Int,
    @SerialName("blocked") val blocked: Boolean,
)

@Serializable
private data class ChangedTO(
    @SerialName("changed") val changed: Boolean,
)

@Serializable
private data class NoticeTO(
    @SerialName("id") val id: Long?,
    @SerialName("subject") val subject: String,
    @SerialName("credentialTypes") val credentialTypes: List<String>,
    @SerialName("reason") val reason: String,
    @SerialName("message") val message: String,
    @SerialName("revokedAt") val revokedAt: String,
    @SerialName("deadlineAt") val deadlineAt: String,
    @SerialName("status") val status: String,
    @SerialName("detail") val detail: String?,
)

@Serializable
private data class RevocationErrorTO(
    @SerialName("code") val code: String,
)
