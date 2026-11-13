/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.input.web.security.AdminLoginRateLimitWebFilter
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers

/** Cadena de seguridad propia del panel de estudiantes (login por formulario, NO Keycloak). Protege todo lo que cuelga de `/uis/admin`. */
fun configureUisAdminSecurity(
    http: ServerHttpSecurity,
    userDetailsService: ReactiveUserDetailsService,
    loginLimiter: AttemptLimiter,
): SecurityWebFilterChain =
    http {
        securityMatcher(ServerWebExchangeMatchers.pathMatchers("${UisAdminUi.BASE}/**"))
        authenticationManager =
            UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService).apply { setPasswordEncoder(BCryptPasswordEncoder()) }
        http.addFilterBefore(AdminLoginRateLimitWebFilter(loginLimiter, UisAdminUi.LOGIN), SecurityWebFiltersOrder.FORM_LOGIN)
        authorizeExchange {
            authorize(UisAdminUi.LOGIN, permitAll)
            authorize(anyExchange, hasRole("ADMIN"))
        }
        formLogin {
            loginPage = UisAdminUi.LOGIN
            authenticationSuccessHandler = RedirectServerAuthenticationSuccessHandler(UisAdminUi.ESTUDIANTES)
            authenticationFailureHandler =
                org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler("${UisAdminUi.LOGIN}?error")
        }
        logout {
            logoutUrl = UisAdminUi.LOGOUT
            logoutSuccessHandler = RedirectServerLogoutSuccessHandler().apply { setLogoutSuccessUrl(java.net.URI(UisAdminUi.LOGIN)) }
        }
        csrf { }
        cors { disable() }
    }
