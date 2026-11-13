package com.onboarding.service

import com.onboarding.common.ConflictException
import com.onboarding.common.NotFoundException
import com.onboarding.domain.AuditEvent
import com.onboarding.domain.BiometricCapture
import com.onboarding.domain.BiometricKind
import com.onboarding.domain.CaptureValidationStatus
import com.onboarding.domain.ContactChannel
import com.onboarding.domain.DocumentCapture
import com.onboarding.domain.DocumentSide
import com.onboarding.domain.OnboardingSession
import com.onboarding.domain.OnboardingStatus
import com.onboarding.domain.VerificationDecision
import com.onboarding.domain.VerificationResult
import com.onboarding.repository.AuditEventRepository
import com.onboarding.repository.BiometricCaptureRepository
import com.onboarding.repository.DocumentCaptureRepository
import com.onboarding.repository.OnboardingSessionRepository
import com.onboarding.repository.VerificationResultRepository
import com.onboarding.storage.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class OnboardingService(
    private val sessionRepository: OnboardingSessionRepository,
    private val documentRepository: DocumentCaptureRepository,
    private val biometricRepository: BiometricCaptureRepository,
    private val verificationRepository: VerificationResultRepository,
    private val auditRepository: AuditEventRepository,
    private val otpService: OtpService,
    private val verificationService: VerificationService,
    private val fileStorage: FileStorage,
    @Value("\${onboarding.session-ttl-minutes}") private val ttlMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createSession(): OnboardingSession {
        val session = OnboardingSession(
            status = OnboardingStatus.CREATED,
            expiresAt = Instant.now().plusSeconds(ttlMinutes * 60),
        )
        sessionRepository.save(session)
        audit(session.id, "SESSION_CREATED", "Sesion de onboarding creada")
        return session
    }

    @Transactional(readOnly = true)
    fun getSession(id: UUID): OnboardingSession = requireExisting(id)

    @Transactional(readOnly = true)
    fun getDocuments(id: UUID): List<DocumentCapture> {
        requireExisting(id)
        return documentRepository.findBySessionId(id)
    }

    @Transactional(readOnly = true)
    fun getBiometric(id: UUID): BiometricCapture? {
        requireExisting(id)
        return biometricRepository.findFirstBySessionIdOrderByCreatedAtDesc(id)
    }

    @Transactional(readOnly = true)
    fun getAudit(id: UUID): List<AuditEvent> {
        requireExisting(id)
        return auditRepository.findBySessionIdOrderByCreatedAtAsc(id)
    }

    @Transactional
    fun startContact(id: UUID, channel: ContactChannel, phoneNumber: String?, email: String?): OtpIssueResult {
        val session = requireActive(id)
        require(session.status == OnboardingStatus.CREATED || session.status == OnboardingStatus.CONTACT_PENDING) {
            "La verificacion de contacto no esta disponible en el estado ${session.status}"
        }
        val destination = when (channel) {
            ContactChannel.SMS -> phoneNumber?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Debes enviar phoneNumber para el canal SMS")
            ContactChannel.EMAIL -> email?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Debes enviar email para el canal EMAIL")
        }
        session.phoneNumber = phoneNumber
        session.email = email
        session.contactChannel = channel
        session.status = OnboardingStatus.CONTACT_PENDING
        sessionRepository.save(session)

        val result = otpService.issue(session.id, destination, channel)
        audit(session.id, "OTP_SENT", "Codigo enviado por $channel")
        return result
    }

    @Transactional(noRollbackFor = [com.onboarding.common.UnauthorizedException::class])
    fun verifyContact(id: UUID, code: String) {
        val session = requireActive(id)
        require(session.status == OnboardingStatus.CONTACT_PENDING) {
            "No hay una verificacion de contacto pendiente (estado ${session.status})"
        }
        otpService.verify(id, code)
        session.contactVerified = true
        session.status = OnboardingStatus.CONTACT_VERIFIED
        sessionRepository.save(session)
        audit(id, "CONTACT_VERIFIED", "Contacto verificado")
    }

    @Transactional
    fun acceptConsent(id: UUID, accepted: Boolean, version: String?) {
        val session = requireActive(id)
        require(session.contactVerified) { "Debes verificar tu contacto antes de dar el consentimiento" }
        require(accepted) { "El consentimiento de tratamiento de datos es obligatorio" }
        session.consentAccepted = true
        session.consentAcceptedAt = Instant.now()
        session.status = OnboardingStatus.DOCUMENTS_PENDING
        sessionRepository.save(session)
        audit(id, "CONSENT_ACCEPTED", "Consentimiento aceptado (version=${version ?: "n/a"})")
    }

    /** Guarda los datos que escribio el usuario; luego se comparan con los del documento. */
    @Transactional
    fun declareData(id: UUID, documentNumber: String, firstNames: String, lastNames: String, birthDate: java.time.LocalDate) {
        val session = requireActive(id)
        require(documentNumber.filter { it.isDigit() }.length in 5..10) { "Numero de cedula invalido" }
        require(firstNames.isNotBlank() && lastNames.isNotBlank()) { "Nombres y apellidos son obligatorios" }
        require(birthDate.isBefore(java.time.LocalDate.now())) { "La fecha de nacimiento debe ser pasada" }
        session.declaredDocumentNumber = documentNumber.filter { it.isDigit() }
        session.declaredFirstNames = firstNames.trim()
        session.declaredLastNames = lastNames.trim()
        session.declaredBirthDate = birthDate
        sessionRepository.save(session)
        audit(id, "DATA_DECLARED", "Datos declarados recibidos")
        // Si el documento ya estaba leido, se actualiza el estado de la comparacion.
    }

    @Transactional
    fun captureDocument(
        id: UUID,
        side: DocumentSide,
        filename: String?,
        contentType: String?,
        bytes: ByteArray,
    ): DocumentCapture {
        val session = requireActive(id)
        require(session.status == OnboardingStatus.DOCUMENTS_PENDING || session.status == OnboardingStatus.DOCUMENTS_CAPTURED) {
            "La captura de documentos no esta disponible en el estado ${session.status}"
        }
        require(bytes.isNotEmpty()) { "El archivo del documento esta vacio" }

        val key = "sessions/$id/document-${side.name.lowercase()}-${UUID.randomUUID()}.${extensionFor(contentType)}"
        fileStorage.store(key, bytes, contentType)

        val capture = documentRepository.findBySessionIdAndSide(id, side)
            ?: DocumentCapture(sessionId = id, side = side, storageKey = key)
        if (capture.storageKey != key) {
            runCatching { fileStorage.delete(capture.storageKey) }
        }
        capture.storageKey = key
        capture.validationStatus = CaptureValidationStatus.PENDING
        capture.originalFilename = filename
        capture.contentType = contentType
        capture.sizeBytes = bytes.size.toLong()
        capture.sha256 = sha256(bytes)
        documentRepository.save(capture)
        audit(id, "DOCUMENT_CAPTURED", "Documento ${side.name} capturado")

        val captures = documentRepository.findBySessionId(id)
        val front = captures.firstOrNull { it.side == DocumentSide.FRONT }
        val back = captures.firstOrNull { it.side == DocumentSide.BACK }
        if (front != null && back != null) {
            runDocumentOcr(session, front, back)
        }
        return capture
    }

    @Transactional
    fun captureSelfie(
        id: UUID,
        filename: String?,
        contentType: String?,
        bytes: ByteArray,
    ): BiometricCapture {
        val session = requireActive(id)
        require(session.status == OnboardingStatus.DOCUMENTS_CAPTURED) {
            "Debes completar y validar los documentos antes de la selfie (estado ${session.status})"
        }
        require(bytes.isNotEmpty()) { "La imagen de la selfie esta vacia" }

        val dataMatch = IdentityComparator.compare(session)
            ?: throw ConflictException("Faltan los datos declarados o los leidos del documento para compararlos")
        val front = documentRepository.findBySessionIdAndSide(id, DocumentSide.FRONT)
            ?: throw ConflictException("Falta la foto frontal del documento")
        val reference = fileStorage.load(front.storageKey)
            ?: throw ConflictException("No se pudo recuperar la foto frontal del documento")

        val key = "sessions/$id/selfie-${UUID.randomUUID()}.${extensionFor(contentType)}"
        fileStorage.store(key, bytes, contentType)

        val liveness = verificationService.runLiveness(bytes)
        val faceMatch = verificationService.runFaceMatch(reference, bytes)

        val capture = BiometricCapture(
            sessionId = id,
            kind = BiometricKind.SELFIE,
            storageKey = key,
            contentType = contentType,
            sizeBytes = bytes.size.toLong(),
            provider = verificationService.biometricProviderName,
            livenessStatus = liveness.status,
            livenessScore = liveness.score,
            faceMatchStatus = faceMatch.status,
            faceMatchScore = faceMatch.score,
            details = "liveness=${liveness.details}; faceMatch=${faceMatch.details}",
        )
        biometricRepository.save(capture)
        audit(id, "BIOMETRIC_CAPTURED", "Selfie capturada (liveness=${liveness.status}, match=${faceMatch.status})")

        val decision = verificationService.decide(
            documentValid = front.validationStatus == CaptureValidationStatus.VALID,
            ocrConfidence = front.ocrConfidence ?: 0.0,
            liveness = liveness,
            faceMatch = faceMatch,
            dataMatch = dataMatch,
        )
        applyDecision(session, decision)
        return capture
    }

    private fun runDocumentOcr(session: OnboardingSession, front: DocumentCapture, back: DocumentCapture) {
        val frontBytes = fileStorage.load(front.storageKey)
            ?: throw ConflictException("No se pudo recuperar la foto frontal del documento")
        val backBytes = fileStorage.load(back.storageKey)
        val ocr = verificationService.runOcr(frontBytes, backBytes, front.contentType)

        val status = if (ocr.documentValid) CaptureValidationStatus.VALID else CaptureValidationStatus.INVALID
        for (capture in listOf(front, back)) {
            capture.validationStatus = status
            capture.ocrConfidence = ocr.confidence
            capture.ocrData = null // el detalle de campos ya vive en la sesion; no se duplican datos personales
            documentRepository.save(capture)
        }

        if (ocr.documentValid) {
            val printed = backBytes?.let { verificationService.runPrintedText(it) }
            if (printed?.birthDate != null && printed.birthDate != ocr.data.birthDate) {
                audit(session.id, "OCR_MISMATCH", "La fecha impresa y la del codigo de barras difieren")
            }
            printed?.let {
                session.birthCity = it.birthCity ?: session.birthCity
                session.birthDepartment = it.birthDepartment ?: session.birthDepartment
                session.heightCm = it.heightCm ?: session.heightCm
                session.expeditionPlace = it.issuePlace ?: session.expeditionPlace
                session.issueDate = it.issueDate ?: session.issueDate
                if (ocr.data.bloodType == null) session.bloodType = it.bloodType
            }
            with(ocr.data) {
                documentNumber?.let { session.documentNumber = it }
                firstName?.let { session.firstName = it }
                middleName?.let { session.middleName = it }
                lastName?.let { session.lastName = it }
                secondLastName?.let { session.secondLastName = it }
                birthDate?.let { session.birthDate = it }
                gender?.let { session.gender = it }
                bloodType?.let { session.bloodType = it }
                expeditionPlace?.let { session.expeditionPlace = it }
                issueDate?.let { session.issueDate = it }
            }
            session.failureReason = null
            session.status = OnboardingStatus.DOCUMENTS_CAPTURED
        } else {
            // Documento ilegible o invalido: el usuario puede volver a tomar las fotos.
            session.failureReason = (ocr.details ?: "Documento invalido").take(255)
            session.status = OnboardingStatus.DOCUMENTS_PENDING
        }
        sessionRepository.save(session)
        audit(session.id, "DOCUMENT_VALIDATED", "Documento ${status.name}: ${ocr.details} (conf=${"%.2f".format(ocr.confidence)})")
    }

    private fun applyDecision(session: OnboardingSession, decision: DecisionResult) {
        verificationRepository.save(
            VerificationResult(
                sessionId = session.id,
                decision = decision.decision,
                reason = decision.reason,
            ),
        )
        session.decision = decision.decision
        session.status = when (decision.decision) {
            VerificationDecision.APPROVED -> OnboardingStatus.APPROVED
            VerificationDecision.REJECTED -> OnboardingStatus.REJECTED
            VerificationDecision.MANUAL_REVIEW -> OnboardingStatus.MANUAL_REVIEW
        }
        if (decision.decision == VerificationDecision.REJECTED) {
            session.failureReason = decision.reason
        }
        sessionRepository.save(session)
        audit(session.id, "DECISION_TAKEN", "${decision.decision}: ${decision.reason}")
    }

    private fun requireActive(id: UUID): OnboardingSession {
        val session = requireExisting(id)
        if (session.isExpired()) {
            if (!session.status.isFinal) {
                session.status = OnboardingStatus.EXPIRED
                sessionRepository.save(session)
            }
            throw ConflictException("La sesion de onboarding expiro")
        }
        if (session.status.isFinal) {
            throw ConflictException("La sesion ya finalizo con estado ${session.status}")
        }
        return session
    }

    private fun requireExisting(id: UUID): OnboardingSession =
        sessionRepository.findById(id)
            .orElseThrow { NotFoundException("Sesion de onboarding no encontrada: $id") }

    private fun audit(sessionId: UUID?, type: String, message: String) {
        auditRepository.save(AuditEvent(sessionId = sessionId, type = type, message = message))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun extensionFor(contentType: String?): String = when (contentType?.lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> "bin"
    }
}
