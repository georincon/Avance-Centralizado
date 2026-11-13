package com.onboarding.common

import org.springframework.http.HttpStatus

/** Excepcion base con estado HTTP asociado. */
open class ApiException(
    val status: HttpStatus,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)

class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)

class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, message)
