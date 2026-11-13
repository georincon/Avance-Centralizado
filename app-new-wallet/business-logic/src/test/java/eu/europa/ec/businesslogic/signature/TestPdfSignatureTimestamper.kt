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

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestPdfSignatureTimestamper {

    private val provider = BouncyCastleProvider().also { Security.addProvider(it) }

    private class Identity(val keys: KeyPair, val cert: X509Certificate)

    private fun identity(cn: String, timeStamping: Boolean = false): Identity {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val name = X500Name("CN=$cn")
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(System.nanoTime()), Date(System.currentTimeMillis() - 60_000),
            Date(System.currentTimeMillis() + 86_400_000), name, keys.public,
        )
        if (timeStamping) {
            builder.addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
        }
        val holder = builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(keys.private))
        return Identity(keys, JcaX509CertificateConverter().getCertificate(holder))
    }

    private val signer = identity("Firmante de prueba")
    private val tsa = identity("TSA de prueba", timeStamping = true)

    /** PDF con una firma CMS desacoplada ya incrustada, como la que deja el SDK: hueco grande rellenado con ceros. */
    private fun signedPdf(holeHexChars: Int = 20_000): ByteArray {
        val prefixBase = "%%PDF-1.7\n1 0 obj<</Type/Sig/ByteRange[0 %-9d %-9d %-9d]/Contents"
        val suffixText = "/Filter/Adobe.PPKLite/SubFilter/ETSI.CAdES.detached>>endobj\ntrailer<<>>\n%%EOF\n"
        val prefixLength = String.format(prefixBase, 0, 0, 0).length
        val start = prefixLength // posicion de '<'
        val end = start + holeHexChars + 2 // justo despues de '>'
        val total = end + suffixText.length
        val prefix = String.format(prefixBase, start, end, total - end)
        val data = (prefix + suffixText).toByteArray(Charsets.ISO_8859_1)
        val covered = prefix.toByteArray(Charsets.ISO_8859_1) + suffixText.toByteArray(Charsets.ISO_8859_1)

        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                    .build(JcaContentSignerBuilder("SHA256withECDSA").build(signer.keys.private), signer.cert),
            )
            addCertificates(JcaCertStore(listOf(signer.cert)))
        }
        // DER, como lo entrega el SDK real (PoDoFo/OpenSSL); el BER de BouncyCastle usa longitud indefinida.
        val cms = generator.generate(CMSProcessableByteArray(covered), false).getEncoded("DER")
        val hex = cms.joinToString("") { "%02X".format(it) }.padEnd(holeHexChars, '0')
        check(data.size == total - (holeHexChars + 2)) { "tamano del PDF de prueba inconsistente" }
        return prefix.toByteArray(Charsets.ISO_8859_1) + "<$hex>".toByteArray(Charsets.ISO_8859_1) +
            suffixText.toByteArray(Charsets.ISO_8859_1)
    }

    /** Autoridad de sellado RFC 3161 de mentira, firmada con la TSA de prueba. */
    private val fakeAuthority: (ByteArray) -> ByteArray = { query ->
        val request = TimeStampRequest(query)
        val tokenGenerator = TimeStampTokenGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(JcaContentSignerBuilder("SHA256withECDSA").build(tsa.keys.private), tsa.cert),
            JcaDigestCalculatorProviderBuilder().build().get(
                org.bouncycastle.asn1.x509.AlgorithmIdentifier(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256),
            ),
            ASN1ObjectIdentifier("1.2.3.4.5"),
        ).apply { addCertificates(JcaCertStore(listOf(tsa.cert))) }
        TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED)
            .generate(request, BigInteger.TEN, Date()).encoded
    }

    private fun holeOf(pdf: ByteArray): IntRange {
        val text = String(pdf, Charsets.ISO_8859_1)
        val g = Regex("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)").find(text)!!.groupValues
        return (g[1].toInt() + g[2].toInt()) until g[3].toInt()
    }

    private fun cmsOf(pdf: ByteArray, original: CMSSignedData? = null): CMSSignedData {
        val hole = holeOf(pdf)
        val hex = String(pdf, hole.first + 1, hole.last - hole.first - 1, Charsets.ISO_8859_1).trimEnd('0')
        val bytes = (hex + "0".repeat(4)).chunked(2).filter { it.length == 2 }.map { it.toInt(16).toByte() }.toByteArray()
        val len = run {
            val b = bytes[1].toInt() and 0xFF
            if (b and 0x80 == 0) 2 + b else 2 + (b and 0x7F) + (1 until (1 + (b and 0x7F))).fold(0) { acc, i -> (acc shl 8) or (bytes[1 + i].toInt() and 0xFF) }
        }
        return CMSSignedData(bytes.copyOf(len))
    }

    @Test
    fun `Given a signed PDF, When a timestamp is added, Then the PDF keeps its size and everything outside the signature hole`() {
        val pdf = signedPdf()
        val stamped = PdfSignatureTimestamper.addSignatureTimestamp(pdf, fakeAuthority)

        assertEquals(pdf.size, stamped.size)
        val hole = holeOf(pdf)
        assertContentEquals(pdf.copyOfRange(0, hole.first), stamped.copyOfRange(0, hole.first))
        assertContentEquals(pdf.copyOfRange(hole.last + 1, pdf.size), stamped.copyOfRange(hole.last + 1, stamped.size))
    }

    @Test
    fun `Given a signed PDF, When a timestamp is added, Then the original signature is still valid and carries a token over its value`() {
        val stamped = PdfSignatureTimestamper.addSignatureTimestamp(signedPdf(), fakeAuthority)
        val hole = holeOf(stamped)
        val covered = stamped.copyOfRange(0, hole.first) + stamped.copyOfRange(hole.last + 1, stamped.size)
        // La firma es desacoplada: para verificarla hay que darle los bytes que cubre.
        val cms = CMSSignedData(CMSProcessableByteArray(covered), cmsOf(stamped).encoded)
        val signerInfo = cms.signerInfos.signers.single()

        assertTrue(signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(signer.cert)))

        val attribute = signerInfo.unsignedAttributes?.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken)
        assertNotNull(attribute)
        val token = org.bouncycastle.tsp.TimeStampToken(
            org.bouncycastle.cms.CMSSignedData(
                org.bouncycastle.asn1.cms.ContentInfo.getInstance(attribute.attrValues.getObjectAt(0)),
            ),
        )
        assertContentEquals(
            MessageDigest.getInstance("SHA-256").digest(signerInfo.signature),
            token.timeStampInfo.messageImprintDigest,
        )
        assertTrue(token.isSignatureValid(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(tsa.cert)))
    }

    @Test
    fun `Given a PDF that already has a timestamp, When a timestamp is added, Then it is returned unchanged`() {
        val once = PdfSignatureTimestamper.addSignatureTimestamp(signedPdf(), fakeAuthority)
        var calls = 0
        val twice = PdfSignatureTimestamper.addSignatureTimestamp(once) { calls++; fakeAuthority(it) }

        assertContentEquals(once, twice)
        assertEquals(0, calls)
    }

    @Test
    fun `Given a PDF without a signature, When a timestamp is added, Then it fails`() {
        assertFailsWith<PdfSignatureTimestamper.TimestampException> {
            PdfSignatureTimestamper.addSignatureTimestamp("%PDF-1.7\n%%EOF\n".toByteArray(), fakeAuthority)
        }
    }

    @Test
    fun `Given an authority that returns garbage, When a timestamp is added, Then it fails and the PDF is not touched`() {
        assertFailsWith<PdfSignatureTimestamper.TimestampException> {
            PdfSignatureTimestamper.addSignatureTimestamp(signedPdf()) { "no es un sello".toByteArray() }
        }
    }

    @Test
    fun `Given a hole too small for the timestamp, When a timestamp is added, Then it fails instead of corrupting the PDF`() {
        assertFailsWith<PdfSignatureTimestamper.TimestampException> {
            PdfSignatureTimestamper.addSignatureTimestamp(signedPdf(holeHexChars = 1_400), fakeAuthority)
        }
    }

    @Test
    fun `Given a stamped PDF, When the covered bytes change, Then the signature no longer verifies`() {
        val stamped = PdfSignatureTimestamper.addSignatureTimestamp(signedPdf(), fakeAuthority)
        val cms = cmsOf(stamped)
        val signerInfo = cms.signerInfos.signers.single()
        val hole = holeOf(stamped)
        val covered = stamped.copyOfRange(0, hole.first) + stamped.copyOfRange(hole.last + 1, stamped.size)

        val intact = CMSSignedData(CMSProcessableByteArray(covered), cms.encoded)
        assertTrue(intact.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(signer.cert)))
        covered[20] = (covered[20].toInt() xor 1).toByte()
        val tampered = CMSSignedData(CMSProcessableByteArray(covered), cms.encoded)
        assertTrue(runCatching {
            tampered.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(provider).build(signer.cert))
        }.getOrDefault(false).not())
        assertNull(signerInfo.unsignedAttributes?.get(PKCSObjectIdentifiers.id_aa_ets_escTimeStamp))
    }
}
