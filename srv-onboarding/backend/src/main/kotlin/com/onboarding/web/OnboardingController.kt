package com.onboarding.web

import com.onboarding.domain.OnboardingSession
import com.onboarding.service.OnboardingService
import com.onboarding.service.OtpIssueResult
import com.onboarding.web.dto.AuditEventResponse
import com.onboarding.web.dto.BiometricSummaryDto
import com.onboarding.web.dto.CapturedDocumentDto
import com.onboarding.web.dto.ConsentRequest
import com.onboarding.service.IdentityComparator
import com.onboarding.web.dto.CreateSessionResponse
import com.onboarding.web.dto.DataMatchDto
import com.onboarding.web.dto.DeclaredDataRequest
import com.onboarding.web.dto.ExtractedDataDto
import com.onboarding.web.dto.SelfieResponse
import com.onboarding.web.dto.SessionStatusResponse
import com.onboarding.web.dto.StartContactRequest
import com.onboarding.web.dto.StartContactResponse
import com.onboarding.web.dto.UploadDocumentResponse
import com.onboarding.web.dto.VerifyContactRequest
import com.onboarding.domain.DocumentSide
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/sessions")
class OnboardingController(
    private val service: OnboardingService,
    @org.springframework.beans.factory.annotation.Value("\${onboarding.otp.expose-code:false}")
    private val exposeOtpCode: Boolean,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(): CreateSessionResponse {
        val session = service.createSession()
        return CreateSessionResponse(session.id, session.status.name, session.expiresAt)
    }

    @GetMapping("/{id}")
    fun status(@PathVariable id: UUID): SessionStatusResponse = buildStatus(service.getSession(id))

    @GetMapping("/{id}/audit")
    fun audit(@PathVariable id: UUID): List<AuditEventResponse> =
        service.getAudit(id).map { AuditEventResponse(it.type, it.message, it.createdAt) }

    @PostMapping("/{id}/contact")
    fun startContact(@PathVariable id: UUID, @RequestBody request: StartContactRequest): StartContactResponse {
        val result: OtpIssueResult = service.startContact(id, request.channel, request.phoneNumber, request.email)
        return StartContactResponse(
            sessionId = id,
            status = service.getSession(id).status.name,
            channel = request.channel.name,
            destinationMasked = result.sendResult.destinationMasked,
            provider = result.sendResult.provider,
            devCode = if (exposeOtpCode) result.code else null,
        )
    }

    @PostMapping("/{id}/contact/verify")
    fun verifyContact(@PathVariable id: UUID, @RequestBody request: VerifyContactRequest): SessionStatusResponse {
        service.verifyContact(id, request.code)
        return buildStatus(service.getSession(id))
    }

    @PostMapping("/{id}/consent")
    fun consent(@PathVariable id: UUID, @RequestBody request: ConsentRequest): SessionStatusResponse {
        service.acceptConsent(id, request.accepted, request.policyVersion)
        return buildStatus(service.getSession(id))
    }

    @PostMapping("/{id}/declared-data")
    fun declaredData(@PathVariable id: UUID, @RequestBody request: DeclaredDataRequest): SessionStatusResponse {
        service.declareData(id, request.documentNumber, request.firstNames, request.lastNames, request.birthDate)
        return buildStatus(service.getSession(id))
    }

    @PostMapping("/{id}/documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDocument(
        @PathVariable id: UUID,
        @RequestParam("side") side: DocumentSide,
        @RequestPart("file") file: MultipartFile,
    ): UploadDocumentResponse {
        val capture = service.captureDocument(id, side, file.originalFilename, file.contentType, file.bytes)
        return UploadDocumentResponse(
            sessionId = id,
            side = side.name,
            validationStatus = capture.validationStatus.name,
            ocrConfidence = capture.ocrConfidence,
            sessionStatus = service.getSession(id).status.name,
        )
    }

    @PostMapping("/{id}/selfie", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadSelfie(@PathVariable id: UUID, @RequestPart("file") file: MultipartFile): SelfieResponse {
        val capture = service.captureSelfie(id, file.originalFilename, file.contentType, file.bytes)
        val session = service.getSession(id)
        return SelfieResponse(
            sessionId = id,
            sessionStatus = session.status.name,
            decision = session.decision?.name,
            livenessStatus = capture.livenessStatus?.name,
            faceMatchStatus = capture.faceMatchStatus?.name,
        )
    }

    private fun buildStatus(session: OnboardingSession): SessionStatusResponse {
        val documents = service.getDocuments(session.id)
        val biometric = service.getBiometric(session.id)
        return SessionStatusResponse(
            id = session.id,
            status = session.status.name,
            decision = session.decision?.name,
            contactVerified = session.contactVerified,
            consentAccepted = session.consentAccepted,
            failureReason = session.failureReason,
            data = ExtractedDataDto(
                documentNumber = session.documentNumber,
                firstName = session.firstName,
                middleName = session.middleName,
                lastName = session.lastName,
                secondLastName = session.secondLastName,
                birthDate = session.birthDate,
                gender = session.gender,
                bloodType = session.bloodType,
                birthCity = session.birthCity,
                birthDepartment = session.birthDepartment,
                heightCm = session.heightCm,
                expeditionPlace = session.expeditionPlace,
                issueDate = session.issueDate,
            ),
            dataMatch = IdentityComparator.compare(session)?.let {
                DataMatchDto(it.documentNumberMatch, it.birthDateMatch, it.firstNamesSimilarity, it.lastNamesSimilarity, it.namesMatch)
            },
            documents = documents.map {
                CapturedDocumentDto(
                    side = it.side.name,
                    validationStatus = it.validationStatus.name,
                    ocrConfidence = it.ocrConfidence,
                    contentType = it.contentType,
                )
            },
            biometric = biometric?.let {
                BiometricSummaryDto(
                    status = "CAPTURED",
                    livenessStatus = it.livenessStatus?.name,
                    livenessScore = it.livenessScore,
                    faceMatchStatus = it.faceMatchStatus?.name,
                    faceMatchScore = it.faceMatchScore,
                    provider = it.provider,
                )
            },
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            expiresAt = session.expiresAt,
        )
    }
}
