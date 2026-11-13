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
package eu.europa.ec.eudi.verifier.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class PresentationEventsTO(
    @JsonProperty("transaction_id") val transactionId: String,
    @JsonProperty("last_updated") val lastUpdated: Long,
    @JsonProperty("events") val events: List<PresentationEvent>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "event", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = TransactionInitialized::class, name = "Transaction initialized"),
    JsonSubTypes.Type(value = RequestObjectRetrieved::class, name = "Request object retrieved"),
    JsonSubTypes.Type(value = FailedToRetrieveRequestObject::class, name = "FailedToRetrieve request"),
    JsonSubTypes.Type(value = FailedToRetrievePresentationDefinition::class, name = "Failed to retrieve presentation definition"),
    JsonSubTypes.Type(value = WalletResponsePosted::class, name = "Wallet response posted"),
    JsonSubTypes.Type(value = WalletFailedToPostResponse::class, name = "Wallet failed to post response"),
    JsonSubTypes.Type(value = VerifierGotWalletResponse::class, name = "Verifier got wallet response"),
    JsonSubTypes.Type(value = VerifierFailedToGetWalletResponse::class, name = "Verifier failed to get wallet"),
    JsonSubTypes.Type(value = PresentationExpired::class, name = "Presentation expired"),
    JsonSubTypes.Type(value = AttestationStatusCheckSuccessful::class, name = "Attestation status check succeeded"),
    JsonSubTypes.Type(value = AttestationStatusCheckFailed::class, name = "Attestation status check failed"),
    JsonSubTypes.Type(value = DCApiTransactionInitialized::class, name = "DC Api Transaction initialized"),
)
sealed interface PresentationEvent {
    val timestamp: String
    val event: String
    val actor: String
}

data class TransactionInitialized(
    override val timestamp: String,
    val response: JsonNode,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class RequestObjectRetrieved(
    override val timestamp: String,
    val jwt: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class FailedToRetrieveRequestObject(
    override val timestamp: String,
    val cause: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class FailedToRetrievePresentationDefinition(
    override val timestamp: String,
    val cause: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class WalletResponsePosted(
    override val timestamp: String,
    @JsonProperty("wallet_response")
    val walletResponse: JsonNode,
    @JsonProperty("verifier_response")
    val verifierEndpointResponse: JsonNode?,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class WalletFailedToPostResponse(
    override val timestamp: String,
    val cause: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class VerifierGotWalletResponse(
    override val timestamp: String,
    @JsonProperty("wallet_response")
    val walletResponse: JsonNode,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class VerifierFailedToGetWalletResponse(
    override val timestamp: String,
    val cause: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class PresentationExpired(
    override val timestamp: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class AttestationStatusCheckSuccessful(
    override val timestamp: String,
    @JsonProperty("status_reference")
    val statusReference: JsonNode,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class AttestationStatusCheckFailed(
    override val timestamp: String,
    @JsonProperty("status_reference")
    val statusReference: JsonNode?,
    val cause: String?,
    override val event: String,
    override val actor: String,
) : PresentationEvent

data class DCApiTransactionInitialized(
    override val timestamp: String,
    val response: String,
    val profile: String,
    override val event: String,
    override val actor: String,
) : PresentationEvent
