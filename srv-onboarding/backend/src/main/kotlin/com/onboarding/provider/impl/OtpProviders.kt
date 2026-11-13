package com.onboarding.provider.impl

import com.onboarding.domain.ContactChannel
import com.onboarding.provider.OtpProvider
import com.onboarding.provider.OtpSendResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Proveedor por defecto para desarrollo: NO envia nada realmente, solo
 * escribe el codigo en el log de la aplicacion. Es la opcion "gratis total".
 */
@Component
@ConditionalOnProperty(name = ["onboarding.otp.provider"], havingValue = "logging", matchIfMissing = true)
class LoggingOtpProvider : OtpProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "logging"

    override fun send(destination: String, channel: ContactChannel, code: String): OtpSendResult {
        log.info("==============================================")
        log.info(" OTP [{}] destino={} codigo={}", channel, destination, code)
        log.info("==============================================")
        return OtpSendResult(delivered = true, provider = name, destinationMasked = maskDestination(destination))
    }
}

/**
 * Envia el OTP por correo electronico usando el SMTP configurado.
 * Con Docker Compose apunta a MailHog (http://localhost:8025).
 */
@Component
@ConditionalOnProperty(name = ["onboarding.otp.provider"], havingValue = "email")
class EmailOtpProvider(private val mailSender: JavaMailSender) : OtpProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    override val name = "email"

    override fun send(destination: String, channel: ContactChannel, code: String): OtpSendResult {
        if (channel != ContactChannel.EMAIL) {
            log.warn("EmailOtpProvider recibio un canal {}; se registra el codigo en el log.", channel)
            log.info(" OTP [{}] destino={} codigo={}", channel, destination, code)
            return OtpSendResult(true, name, maskDestination(destination))
        }
        val message = SimpleMailMessage().apply {
            setTo(destination)
            setFrom("no-reply@onboarding.local")
            setSubject("Tu codigo de verificacion")
            setText("Tu codigo de verificacion es: $code\n\nVence en pocos minutos. No lo compartas con nadie.")
        }
        mailSender.send(message)
        log.info("OTP enviado por correo a {}", maskDestination(destination))
        return OtpSendResult(true, name, maskDestination(destination))
    }
}
