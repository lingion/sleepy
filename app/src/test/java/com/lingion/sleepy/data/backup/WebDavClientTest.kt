package com.lingion.sleepy.data.backup

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient
    private lateinit var config: WebDavConfig

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()
        client = WebDavClient(
            OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
        )
        config = WebDavConfig(
            serverUrl = server.url("/remote.php/dav/").toString(),
            directory = "Sleepy/backups",
            username = "alice",
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun testConnectionCreatesMissingFoldersAndVerifiesCollection() {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(xmlResponse(207, collectionResponse("/remote.php/dav/Sleepy/backups/")))

        client.testConnection(config, "secret")

        val initial = server.takeRequest()
        assertEquals("PROPFIND", initial.method)
        assertEquals("0", initial.headers["Depth"])
        assertEquals(Credentials.basic("alice", "secret", Charsets.UTF_8), initial.headers["Authorization"])
        assertEquals("/remote.php/dav/Sleepy/backups", initial.target)
        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test
    fun uploadListAndDownloadUseOnlySafeFilesUnderConfiguredDirectory() {
        val fileName = "sleepy-backup-20261114-123456-789.sleepybackup"
        val archive = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(xmlResponse(207, """
            <D:multistatus xmlns:D="DAV:">
              ${collectionResponse("/remote.php/dav/Sleepy/backups/")}
              ${fileResponse("/remote.php/dav/Sleepy/backups/$fileName", "Sat, 14 Nov 2026 12:34:56 GMT", "4")}
              ${fileResponse("https://other.example/steal.sleepybackup", "Sat, 14 Nov 2026 12:34:56 GMT", "4")}
              ${fileResponse("/outside/not-in-this-folder.sleepybackup", "Sat, 14 Nov 2026 12:34:56 GMT", "4")}
            </D:multistatus>
        """.trimIndent()))
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(archive)).build())

        client.upload(config, "secret", fileName, archive)
        val backups = client.listBackups(config, "secret")
        val restored = client.download(config, "secret", fileName)

        assertEquals(listOf(fileName), backups.map(WebDavRemoteBackup::fileName))
        assertEquals(4L, backups.single().sizeBytes)
        assertArrayEquals(archive, restored)

        val upload = server.takeRequest()
        assertEquals("PUT", upload.method)
        assertEquals("/remote.php/dav/Sleepy/backups/$fileName", upload.target)
        assertEquals(Credentials.basic("alice", "secret", Charsets.UTF_8), upload.headers["Authorization"])
        assertArrayEquals(archive, upload.body?.toByteArray() ?: ByteArray(0))
        val listing = server.takeRequest()
        assertEquals("PROPFIND", listing.method)
        assertEquals("1", listing.headers["Depth"])
        val download = server.takeRequest()
        assertEquals("GET", download.method)
        assertEquals("/remote.php/dav/Sleepy/backups/$fileName", download.target)
    }

    @Test
    fun plaintextServerIsRejectedBeforeAnyRequest() {
        val error = assertThrows(WebDavFailure::class.java) {
            client.testConnection(config.copy(serverUrl = "http://example.test/dav"), "secret")
        }

        assertEquals(WebDavFailureKind.HTTPS_REQUIRED, error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun remoteFileNameCannotEscapeBackupDirectory() {
        val error = assertThrows(WebDavFailure::class.java) {
            client.download(config, "secret", "../other.zip")
        }

        assertEquals(WebDavFailureKind.INVALID_CONFIGURATION, error.kind)
        assertEquals(0, server.requestCount)
    }

    private fun xmlResponse(code: Int, xml: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .body(xml)
        .build()

    private fun collectionResponse(href: String) = """
        <D:response>
          <D:href>$href</D:href>
          <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
            <D:status>HTTP/1.1 200 OK</D:status>
          </D:propstat>
        </D:response>
    """.trimIndent()

    private fun fileResponse(href: String, modified: String, size: String) = """
        <D:response>
          <D:href>$href</D:href>
          <D:propstat><D:prop><D:resourcetype/><D:getlastmodified>$modified</D:getlastmodified>
            <D:getcontentlength>$size</D:getcontentlength></D:prop>
            <D:status>HTTP/1.1 200 OK</D:status>
          </D:propstat>
        </D:response>
    """.trimIndent()
}
