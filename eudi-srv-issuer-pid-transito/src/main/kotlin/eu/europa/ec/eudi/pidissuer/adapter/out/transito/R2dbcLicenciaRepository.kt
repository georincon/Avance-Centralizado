/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import eu.europa.ec.eudi.pidissuer.port.out.transito.DeleteLicencia
import eu.europa.ec.eudi.pidissuer.port.out.transito.FindLicenciaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.transito.ListLicencias
import eu.europa.ec.eudi.pidissuer.port.out.transito.SaveLicencia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.kotlin.CoroutineSortingRepository

interface LicenciaR2dbcRepository :
    CoroutineCrudRepository<LicenciaEntity, Long>,
    CoroutineSortingRepository<LicenciaEntity, Long> {
    suspend fun findByDocumentoNumero(documentoNumero: String): LicenciaEntity?

    @Query(
        """
        SELECT * FROM licencias
        WHERE :query IS NULL
           OR documento_numero ILIKE CONCAT('%', :query, '%')
           OR apellidos ILIKE CONCAT('%', :query, '%')
           OR nombres ILIKE CONCAT('%', :query, '%')
        ORDER BY apellidos, nombres
        """,
    )
    fun search(query: String?): Flow<LicenciaEntity>

    @Query("DELETE FROM licencias WHERE documento_numero = :documentoNumero")
    suspend fun deleteByDocumentoNumero(documentoNumero: String): Int?
}

interface LicenciaCategoriaR2dbcRepository : CoroutineCrudRepository<LicenciaCategoriaEntity, Long> {
    fun findByLicenciaId(licenciaId: Long): Flow<LicenciaCategoriaEntity>

    @Query("DELETE FROM licencia_categorias WHERE licencia_id = :licenciaId")
    suspend fun deleteByLicenciaId(licenciaId: Long): Int?
}

class R2dbcLicenciaRepository(
    private val r2dbc: LicenciaR2dbcRepository,
    private val categorias: LicenciaCategoriaR2dbcRepository,
) {
    private suspend fun LicenciaEntity.completa(): Licencia =
        toDomain(id?.let { categorias.findByLicenciaId(it).toList() }.orEmpty())

    val findLicenciaByDocumentNumber: FindLicenciaByDocumentNumber =
        FindLicenciaByDocumentNumber { documentNumber ->
            r2dbc.findByDocumentoNumero(documentNumber.value)?.completa()
        }

    val listLicencias: ListLicencias =
        ListLicencias { searchQuery ->
            r2dbc.search(searchQuery?.takeIf { it.isNotBlank() }).toList().map { it.completa() }
        }

    /**
     * Upsert por [Licencia.documentNumber]: si ya existe un registro con ese número de documento, se conserva su
     * `id` y se actualiza; si no, se crea. Las categorías se reemplazan por completo.
     */
    val saveLicencia: SaveLicencia =
        SaveLicencia { licencia ->
            val existingId = r2dbc.findByDocumentoNumero(licencia.documentNumber.value)?.id
            val saved = r2dbc.save(LicenciaEntity.fromDomain(licencia.copy(id = existingId ?: licencia.id)))
            val licenciaId = checkNotNull(saved.id) { "licencia sin id tras guardar" }
            categorias.deleteByLicenciaId(licenciaId)
            val nuevas = categorias.saveAll(licencia.categorias.map { LicenciaCategoriaEntity.fromDomain(licenciaId, it) }).toList()
            saved.toDomain(nuevas)
        }

    val deleteLicencia: DeleteLicencia =
        DeleteLicencia { documentNumber ->
            val id = r2dbc.findByDocumentoNumero(documentNumber.value)?.id
            id?.let { categorias.deleteByLicenciaId(it) }
            (r2dbc.deleteByDocumentoNumero(documentNumber.value) ?: 0) > 0
        }
}
