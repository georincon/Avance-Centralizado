/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.signature

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.SignerInformationStore
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Convierte una firma PAdES basica (B-B) en una con sello de tiempo (B-T), sin tocar el documento.
 *
 * El SDK de firma de la wallet siempre firma en nivel B-B y no deja elegir otro: construye cada documento con la
 * configuracion por defecto. Pero un sello de tiempo CAdES no forma parte de lo firmado: va como atributo no
 * firmado (`id-aa-signatureTimeStampToken`, RFC 3161 + ETSI EN 319 122-1) dentro del bloque CMS, asi que se puede
 * anadir despues. Ese bloque va en hexadecimal en `/Contents`, en un hueco que el SDK reserva con mucho margen:
 * el CMS ampliado se escribe en el mismo hueco, rellenando con ceros, y no cambian ni `/ByteRange` ni ningun byte
 * cubierto por la firma, asi que la firma sigue siendo valida.
 */
object PdfSignatureTimestamper {

    private val BYTE_RANGE = Regex("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*\\]")

    class TimestampException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * @param pdf el PDF firmado (la firma de `/ByteRange` mas reciente).
     * @param timestampAuthority recibe una `TimeStampReq` (DER) y devuelve la `TimeStampResp` (DER).
     * @return el PDF con el sello de tiempo, del mismo tamano que `pdf`.
     */
    fun addSignatureTimestamp(pdf: ByteArray, timestampAuthority: (ByteArray) -> ByteArray): ByteArray {
        val groups = BYTE_RANGE.findAll(String(pdf, Charsets.ISO_8859_1)).lastOrNull()?.groupValues
            ?: throw TimestampException("El PDF no tiene firma")
        val holeStart = groups[1].toInt() + groups[2].toInt() // posicion de '<'
        val holeEnd = groups[3].toInt() // posicion justo despues de '>'
        if (holeStart !in 0 until holeEnd || holeEnd > pdf.size ||
            pdf[holeStart] != '<'.code.toByte() || pdf[holeEnd - 1] != '>'.code.toByte()
        ) {
            throw TimestampException("El hueco de la firma no tiene el formato esperado")
        }

        val cmsBytes = cmsDeHex(pdf, holeStart + 1, holeEnd - 1)
        val cms = try {
            CMSSignedData(cmsBytes)
        } catch (e: Exception) {
            throw TimestampException("La firma del PDF no es un CMS valido", e)
        }
        val signer = cms.signerInfos.signers.singleOrNull()
            ?: throw TimestampException("Se esperaba exactamente un firmante")
        if (signer.unsignedAttributes?.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken) != null) {
            return pdf // ya tiene sello
        }

        val token = requestToken(signer, timestampAuthority)
        val unsigned = ASN1EncodableVector().apply {
            signer.unsignedAttributes?.toASN1EncodableVector()?.let { v ->
                for (i in 0 until v.size()) add(v[i])
            }
            add(
                Attribute(
                    PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                    DERSet(token as ASN1Encodable),
                ),
            )
        }
        val signerConSello = SignerInformation.replaceUnsignedAttributes(signer, AttributeTable(unsigned))
        val nuevo = CMSSignedData.replaceSigners(cms, SignerInformationStore(signerConSello))
            .getEncoded(ASN1Encoding.DER)

        val huecoHex = holeEnd - 1 - (holeStart + 1)
        if (nuevo.size * 2 > huecoHex) {
            throw TimestampException("El sello no cabe en el hueco de la firma (${nuevo.size * 2} > $huecoHex)")
        }
        val hex = nuevo.joinToString("") { "%02X".format(it) }.padEnd(huecoHex, '0')
        return pdf.copyOf().also { salida ->
            hex.toByteArray(Charsets.ISO_8859_1).copyInto(salida, destinationOffset = holeStart + 1)
        }
    }

    /** El CMS real mide lo que dice su cabecera DER; el resto del hueco es relleno de ceros. */
    private fun cmsDeHex(pdf: ByteArray, from: Int, to: Int): ByteArray {
        val hex = String(pdf, from, to - from, Charsets.ISO_8859_1).trimEnd('0', ' ', '\n', '\r')
        // trimEnd puede comerse un '0' legitimo del ultimo byte: se completa y se recorta por la cabecera DER.
        val completo = String(pdf, from, minOf(to - from, hex.length + 4), Charsets.ISO_8859_1)
        val crudo = completo.chunked(2).filter { it.length == 2 }.map { it.toInt(16).toByte() }.toByteArray()
        require(crudo.size > 4 && crudo[0] == 0x30.toByte()) { "El contenido de la firma no empieza por un SEQUENCE DER" }
        val primero = crudo[1].toInt() and 0xFF
        // El SDK entrega el CMS en DER (longitud definida); con BER de longitud indefinida no se sabe donde termina.
        if (primero == 0x80) throw TimestampException("El CMS esta en BER de longitud indefinida: no soportado")
        val (cabecera, longitud) = if (primero and 0x80 == 0) {
            2 to primero
        } else {
            val n = primero and 0x7F
            var l = 0
            for (i in 0 until n) l = (l shl 8) or (crudo[2 + i].toInt() and 0xFF)
            (2 + n) to l
        }
        return crudo.copyOf(cabecera + longitud)
    }

    private fun requestToken(signer: SignerInformation, timestampAuthority: (ByteArray) -> ByteArray): org.bouncycastle.asn1.cms.ContentInfo {
        // Sello CAdES-T: la huella es la del VALOR de la firma (ETSI EN 319 122-1, 5.3).
        val huella = MessageDigest.getInstance("SHA-256").digest(signer.signature)
        val request = TimeStampRequestGenerator().apply { setCertReq(true) }
            .generate(TSPAlgorithms.SHA256, huella, BigInteger(64, SecureRandom()))
        val response = try {
            TimeStampResponse(timestampAuthority(request.encoded))
        } catch (e: Exception) {
            throw TimestampException("La autoridad de sellado no respondio con un sello valido", e)
        }
        try {
            response.validate(request)
        } catch (e: Exception) {
            throw TimestampException("La respuesta de la autoridad de sellado no es valida: ${e.message}", e)
        }
        val token = response.timeStampToken
            ?: throw TimestampException("La autoridad de sellado rechazo la solicitud: ${response.statusString}")
        return token.toCMSSignedData().toASN1Structure()
    }
}
