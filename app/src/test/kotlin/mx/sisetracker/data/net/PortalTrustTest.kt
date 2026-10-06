package mx.sisetracker.data.net

import java.io.File
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The portal's certificate chain (leaf ← Let's Encrypt YR2, as the server
 * sends it) must validate against the ISRG Root YR the app bundles, and the
 * network security config must trust that root only for the portal's domain.
 * Uses saved public certificates; never touches the network.
 */
class PortalTrustTest {
    private val certs = CertificateFactory.getInstance("X.509")

    private fun cert(path: String): X509Certificate =
        File(path).inputStream().use { certs.generateCertificate(it) as X509Certificate }

    private val root = cert("src/main/res/raw/isrg_root_yr.pem")
    private val chain = listOf(cert("src/test/resources/tls/dgej_leaf.pem"), cert("src/test/resources/tls/lets_encrypt_yr2.pem"))

    /** Inside the saved leaf's validity, so the test doesn't expire with it. */
    private val validationDate = Date.from(Instant.parse("2026-10-06T00:00:00Z"))

    private fun validate(anchors: Set<TrustAnchor>) {
        val params = PKIXParameters(anchors).apply {
            isRevocationEnabled = false
            date = validationDate
        }
        CertPathValidator.getInstance("PKIX").validate(certs.generateCertPath(chain), params)
    }

    @Test
    fun `the bundled root is ISRG Root YR`() {
        assertEquals("CN=Root YR,O=ISRG,C=US", root.subjectX500Principal.name)
        assertEquals(root.subjectX500Principal, root.issuerX500Principal)
        root.verify(root.publicKey)
    }

    @Test
    fun `the portal chain validates against the bundled root`() {
        validate(setOf(TrustAnchor(root, null)))
        assertTrue(chain.first().subjectAlternativeNames.orEmpty().any { it[1] == "www.dgej.cjf.gob.mx" })
    }

    @Test
    fun `the portal chain does not validate against the JDK roots alone`() {
        // Why the bundled root is needed: Root YR isn't in common trust stores yet.
        val jdk = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            File(System.getProperty("java.home"), "lib/security/cacerts").inputStream().use { load(it, null) }
        }
        val anchors = jdk.aliases().toList()
            .mapNotNull { jdk.getCertificate(it) as? X509Certificate }
            .filterNot { it.subjectX500Principal == root.subjectX500Principal }
            .map { TrustAnchor(it, null) }
            .toSet()
        assertThrows(Exception::class.java) { validate(anchors) }
    }

    @Test
    fun `the network security config trusts the root only for the portal domain`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/network_security_config.xml"))
        val base = doc.getElementsByTagName("base-config").item(0).textContent
        val domainConfig = doc.getElementsByTagName("domain-config").item(0)
        val domains = doc.getElementsByTagName("domain")
        val sources = (0 until doc.getElementsByTagName("certificates").length).map {
            doc.getElementsByTagName("certificates").item(it).attributes.getNamedItem("src").nodeValue
        }

        assertEquals(1, domains.length)
        assertEquals("cjf.gob.mx", domains.item(0).textContent.trim())
        assertEquals("true", domains.item(0).attributes.getNamedItem("includeSubdomains").nodeValue)
        assertTrue(domainConfig.textContent.isNotEmpty())
        assertTrue("@raw/isrg_root_yr" !in base)
        assertEquals(listOf("system", "system", "@raw/isrg_root_yr"), sources)
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
    }
}
