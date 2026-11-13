package com.onboarding.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Configuracion de seguridad.
 *
 * FASE 1 (desarrollo): los endpoints de onboarding quedan abiertos para poder
 * probar el flujo con curl/Postman sin autenticacion.
 *
 * PENDIENTE FASE 2: proteger los endpoints de la API con API Key o JWT y
 * limitar CORS al cliente oficial (app Android).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/**").permitAll()
                    // Pagina estatica «Perdi mi celular» (se monta desde webroot/).
                    .requestMatchers("/perdi-mi-celular.html").permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
        return http.build()
    }
}
