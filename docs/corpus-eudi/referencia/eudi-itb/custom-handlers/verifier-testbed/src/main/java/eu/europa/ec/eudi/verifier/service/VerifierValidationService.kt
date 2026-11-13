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
package eu.europa.ec.eudi.verifier.service

import com.gitb.core.AnyContent
import com.gitb.core.ValueEmbeddingEnumeration
import com.gitb.tr.TAR
import com.gitb.tr.TestResultType
import com.gitb.tr.ValidationCounters
import com.gitb.vs.GetModuleDefinitionResponse
import com.gitb.vs.ValidateRequest
import com.gitb.vs.ValidationResponse
import com.gitb.vs.ValidationService
import com.gitb.vs.Void
import eu.europa.ec.eudi.gitb.Utils
import eu.europa.ec.eudi.verifier.dto.AttestationStatusCheckFailed
import eu.europa.ec.eudi.verifier.dto.FailedToRetrievePresentationDefinition
import eu.europa.ec.eudi.verifier.dto.FailedToRetrieveRequestObject
import eu.europa.ec.eudi.verifier.dto.PresentationEvent
import eu.europa.ec.eudi.verifier.dto.PresentationEventsTO
import eu.europa.ec.eudi.verifier.dto.PresentationExpired
import eu.europa.ec.eudi.verifier.dto.RequestObjectRetrieved
import eu.europa.ec.eudi.verifier.dto.ValidationWarnings
import eu.europa.ec.eudi.verifier.dto.VerifierFailedToGetWalletResponse
import eu.europa.ec.eudi.verifier.dto.VerifierGotWalletResponse
import eu.europa.ec.eudi.verifier.dto.WalletFailedToPostResponse
import eu.europa.ec.eudi.verifier.dto.WalletResponsePosted
import eu.europa.ec.eudi.verifier.dto.Warning
import eu.europa.ec.eudi.verifier.utils.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class VerifierValidationService(
    private val utils: Utils,
    @Qualifier("utilsJson")
    private val json: Json,
) : ValidationService {
    private val log: Logger = LoggerFactory.getLogger(VerifierValidationService::class.java)

    override fun getModuleDefinition(parameters: Void?): GetModuleDefinitionResponse = GetModuleDefinitionResponse()

    override fun validate(parameters: ValidateRequest?): ValidationResponse {
        log.info(
            "Received 'validate' command from test bed for session [{}]",
            parameters!!.getSessionId(),
        )

        // First extract the parameters and check to see if they are as expected.
        val providedText = utils.getRequiredString(parameters.getInput(), "text")
        val expectError =
            runCatching {
                utils.getRequiredString(parameters.getInput(), "expectedEvent")
            }.getOrNull()

        val providedLogs = json.reader.readValue(providedText, PresentationEventsTO::class.java)
        val events = providedLogs.events

        val warningsMap = extractWarnings(events)
        val nonRecoverableError = checkNonRecoverableErrors(events, expectError, warningsMap)

        val report = createReport(providedLogs, nonRecoverableError, warningsMap)
        log.info("Validation report created: {}", report)
        return ValidationResponse().apply { this.report = report }
    }

    private fun extractWarnings(events: List<PresentationEvent>): Map<String, String?> =
        events.mapNotNull {
            when (it) {
                is AttestationStatusCheckFailed -> it.event to it.cause
                is WalletFailedToPostResponse -> it.event to it.cause
                is FailedToRetrievePresentationDefinition -> it.event to it.cause
                is FailedToRetrieveRequestObject -> it.event to it.cause
                is PresentationExpired -> it.event to it.actor
                is VerifierFailedToGetWalletResponse -> it.event to it.cause
                else -> null
            }
        }.toMap()

    private fun checkNonRecoverableErrors(
        events: List<PresentationEvent>,
        expectError: String?,
        warningsMap: Map<String, String?>,
    ): String? =
        when (expectError) {
            "attestation_error" ->
                if (warningsMap["Attestation status check failed"] == null) {
                    "Attestation step should fail to post response but did anyways or/and other error occurred (ex: Presentation Timeout)"
                } else {
                    null
                }
            "certificate_error" ->
                if (warningsMap["Wallet failed to post response"] == null) {
                    "Wallet should fail to post response but did anyways or/and other error occurred (ex: Presentation Timeout)"
                } else {
                    null
                }
            "access_certificate_error" -> {
                val requestObjectRetrieved = events.any { it is RequestObjectRetrieved }
                val verifierGotWalletResponse = events.any { it is VerifierGotWalletResponse }
                val verifierFailedToGetWallet = events.any { it is VerifierFailedToGetWalletResponse }

                if (
                    !requestObjectRetrieved ||
                    verifierGotWalletResponse ||
                    !verifierFailedToGetWallet
                ) {
                    "Wallet should fail to post response since access certificate is invalid but did not"
                } else {
                    null
                }
            }
            "access_certificate_error_dc_api" -> {
                if (events.any { it is VerifierGotWalletResponse }) {
                    "Wallet should fail to post response since access certificate is invalid but did not"
                } else {
                    null
                }
            }
            else -> {
                val verifierWalletResponseEvent = events.filterIsInstance<VerifierGotWalletResponse>().firstOrNull()
                val walletResponseEvent = events.filterIsInstance<WalletResponsePosted>().firstOrNull()

                val verifierQuery = verifierWalletResponseEvent?.walletResponse
                val walletQuery = walletResponseEvent?.walletResponse
                val successCheck = verifierWalletResponseEvent != null

                if (verifierQuery == walletQuery && successCheck) {
                    null
                } else {
                    "Wallet query and verifier query do not match"
                }
            }
        }

    private fun createReport(
        providedLogs: PresentationEventsTO,
        nonRecoverableErrors: String?,
        warningsMap: Map<String, String?>,
    ): TAR {
        val report =
            utils.createReport(
                if (nonRecoverableErrors != null) {
                    TestResultType.FAILURE
                } else {
                    TestResultType.SUCCESS
                },
            )

        report.apply {
            counters = ValidationCounters()
            context.item.add(providedLogs.toContent("Verifier's Logs"))

            if (nonRecoverableErrors != null) {
                log.info("nonRecoverableErrors created: {}", nonRecoverableErrors)
                context.item.add(
                    nonRecoverableErrors.toContent("Non-recoverable errors"),
                )
                counters.nrOfErrors = 1.toBigInteger()
            }

            if (warningsMap.isNotEmpty()) {
                val warningsTO = ValidationWarnings(warnings = warningsMap.values.distinct().map { Warning(warning = it) })
                context.item.add(warningsTO.toContent("Validation warnings"))
            }
            counters.nrOfWarnings = warningsMap.size.toBigInteger()
        }
        return report
    }

    private fun Any.toContent(
        name: String,
        type: String = "application/json",
    ): AnyContent =
        AnyContent().apply {
            this.name = name
            mimeType = type
            encoding = "UTF-8"
            item.add(
                utils.createAnyContentSimple(
                    "JSON Data",
                    json.writer.writeValueAsString(this@toContent),
                    ValueEmbeddingEnumeration.STRING,
                ),
            )
        }
}
