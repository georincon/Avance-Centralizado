package com.onboarding

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OnboardingBackendApplication

fun main(args: Array<String>) {
	runApplication<OnboardingBackendApplication>(*args)
}
