package com.onboarding.provider.impl

/** Enmascara un dato de contacto dejando visibles solo los ultimos 4 caracteres. */
internal fun maskDestination(value: String): String {
    if (value.length <= 4) return "****"
    return "*".repeat(value.length - 4) + value.takeLast(4)
}
