/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.DeleteCedula
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.ListCedulas
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.SaveCedula
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.kotlin.CoroutineSortingRepository

/**
 * Spring Data R2DBC repository for [CedulaEntity].
 */
interface CedulaR2dbcRepository :
    CoroutineCrudRepository<CedulaEntity, Long>,
    CoroutineSortingRepository<CedulaEntity, Long> {
    suspend fun findByDocumentoNumero(documentoNumero: String): CedulaEntity?

    @Query(
        """
        SELECT * FROM cedulas
        WHERE :query IS NULL
           OR documento_numero ILIKE CONCAT('%', :query, '%')
           OR apellidos ILIKE CONCAT('%', :query, '%')
           OR nombres ILIKE CONCAT('%', :query, '%')
        ORDER BY apellidos, nombres
        """,
    )
    fun search(query: String?): Flow<CedulaEntity>

    @Query("DELETE FROM cedulas WHERE documento_numero = :documentoNumero")
    suspend fun deleteByDocumentoNumero(documentoNumero: String): Int?
}

/**
 * R2DBC-backed adapter implementing all [Cedula] persistence ports.
 */
class R2dbcCedulaRepository(
    private val r2dbc: CedulaR2dbcRepository,
) {
    val findCedulaByDocumentNumber: FindCedulaByDocumentNumber =
        FindCedulaByDocumentNumber { documentNumber ->
            r2dbc.findByDocumentoNumero(documentNumber.value)?.toDomain()
        }

    val listCedulas: ListCedulas =
        ListCedulas { searchQuery ->
            r2dbc.search(searchQuery?.takeIf { it.isNotBlank() }).toDomainList()
        }

    /**
     * Upsert por [Cedula.documentNumber]: si ya existe un registro con ese
     * número de documento, se conserva su `id` y se actualiza; si no, se crea.
     */
    val saveCedula: SaveCedula =
        SaveCedula { cedula ->
            val existingId = r2dbc.findByDocumentoNumero(cedula.documentNumber.value)?.id
            val toSave = CedulaEntity.fromDomain(cedula.copy(id = existingId ?: cedula.id))
            r2dbc.save(toSave).toDomain()
        }

    val deleteCedula: DeleteCedula =
        DeleteCedula { documentNumber ->
            (r2dbc.deleteByDocumentoNumero(documentNumber.value) ?: 0) > 0
        }
}

private suspend fun Flow<CedulaEntity>.toDomainList(): List<Cedula> = toList().map { it.toDomain() }
