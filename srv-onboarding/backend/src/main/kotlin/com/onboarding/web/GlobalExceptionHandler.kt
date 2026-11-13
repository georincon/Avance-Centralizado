package com.onboarding.web

import com.onboarding.common.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException

/** Traduce excepciones a respuestas RFC 7807 (ProblemDetail). */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ProblemDetail {
        log.warn("Error de negocio: {} - {}", ex.status, ex.message)
        return ProblemDetail.forStatusAndDetail(ex.status, ex.message).apply {
            title = ex.status.reasonPhrase
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ProblemDetail {
        log.warn("Solicitud invalida: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Solicitud invalida").apply {
            title = "Bad Request"
        }
    }

    /**
     * Peticion mal formada: un parametro que no se puede convertir al tipo que
     * pide el controlador (por ejemplo, un id de sesion que no es un UUID) o un
     * cuerpo JSON ilegible. Es un error del CLIENTE (400), no del servidor:
     * sin este manejador caia en el generico y devolvia 500, que hace creer
     * que el servicio esta roto cuando solo se ha llamado mal.
     */
    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MissingServletRequestPartException::class,
        MethodArgumentNotValidException::class,
    )
    fun handleBadRequest(ex: Exception): ProblemDetail {
        log.warn("Peticion invalida: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Peticion invalida").apply {
            title = "Bad Request"
        }
    }

    /**
     * Metodo HTTP no soportado para esa ruta (por ejemplo, GET sobre un
     * endpoint que solo admite POST). Es 405, no 500: el recurso existe, la
     * peticion no. Sin este manejador el generico lo convertia en 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ProblemDetail {
        log.warn("Metodo no soportado: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Metodo no soportado").apply {
            title = "Method Not Allowed"
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail {
        log.error("Error inesperado", ex)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrio un error inesperado",
        ).apply { title = "Internal Server Error" }
    }
}
