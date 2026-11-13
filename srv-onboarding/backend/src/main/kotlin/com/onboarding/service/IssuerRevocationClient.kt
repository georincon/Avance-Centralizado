package com.onboarding.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Cliente de la API interna de revocacion del emisor de la Registraduria. */
interface IssuerRevocationClient {
    val configured: Boolean
    fun revoke(subject: String): LostDeviceConfirmResult
}

@Component
class HttpIssuerRevocationClient(
    @Value("\${onboarding.lost-device.issuer-url:}") private val issuerUrl: String,
    @Value("\${onboarding.lost-device.issuer-api-key:}") private val apiKey: String,
) : IssuerRevocationClient {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val mapper = JsonMapper.builder().build()

    override val configured: Boolean get() = issuerUrl.isNotBlank() && apiKey.isNotBlank()

    override fun revoke(subject: String): LostDeviceConfirmResult {
        // Si el emisor aun no conoce `wallet`, lo ignora; si lo conoce, revoca tambien la wallet.
        val body = mapper.writeValueAsString(mapOf("subject" to subject, "reason" to "user-request", "wallet" to true))
        val request = HttpRequest.newBuilder(URI.create(issuerUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "El emisor respondio ${response.statusCode()}" }
        val json = mapper.readTree(response.body())
        return LostDeviceConfirmResult(
            revoked = json.path("revoked").asInt(0),
            failed = json.path("failed").asInt(0),
            withoutStatus = json.path("withoutStatus").asInt(0),
            blocked = json.path("blocked").asBoolean(false),
            walletRevoked = json.path("wallet").path("revoked").asInt(0),
            walletFailed = json.path("wallet").path("failed").asInt(0),
        )
    }
}
