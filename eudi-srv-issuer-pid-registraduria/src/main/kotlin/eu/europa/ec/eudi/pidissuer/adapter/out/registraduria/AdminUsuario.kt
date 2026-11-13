/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 5, para el contexto del login del panel.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * Credenciales de un administrador del panel (tabla `admin_usuarios`, V2.sql).
 * Independiente de Keycloak: este login es solo para el panel de carga de
 * cédulas, no para la emisión de PIDs.
 */
@Table("admin_usuarios")
data class AdminUsuarioEntity(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("username")
    val username: String,
    @Column("password_hash")
    val passwordHash: String,
)

interface AdminUsuarioR2dbcRepository : CoroutineCrudRepository<AdminUsuarioEntity, Long> {
    suspend fun findByUsername(username: String): AdminUsuarioEntity?
}

/**
 * Upsert manual por `username` (sin depender de `ON CONFLICT`, que algunos
 * motores de prueba como H2 no soportan igual que PostgreSQL): conserva el
 * `id` existente si ya hay un admin con ese username.
 */
suspend fun AdminUsuarioR2dbcRepository.upsert(
    username: String,
    passwordHash: String,
) {
    val existingId = findByUsername(username)?.id
    save(AdminUsuarioEntity(id = existingId, username = username, passwordHash = passwordHash))
}
