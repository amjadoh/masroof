package com.baraa.masroof.lab.beta

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Bounded authenticated chunks: no archive paths, compression or attacker-selected KDF cost. */
internal class BetaEncryptedArchive(private val originKey: (Boolean) -> SecretKey = ::installationKey) {
    fun write(folder: File, output: OutputStream, password: CharArray) {
        require(password.size in 12..256)
        val files = ENTRIES.map { File(folder, it) }
        files.forEachIndexed { index, file -> require(file.isFile && file.length() in 1..limit(index)) }
        val size = files.sumOf { 8L + it.length() } + 32
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(8).also(random::nextBytes)
        val header = ByteArrayOutputStream().also { DataOutputStream(it).apply { write(MAGIC); write(salt); write(nonce); writeLong(size) } }.toByteArray()
        val key = derive(password, salt)
        val mac = Mac.getInstance("HmacSHA256").apply { init(originKey(true)); update(MAGIC) }
        output.write(header)
        val encrypted = ChunkOutput(output, header, nonce, key)
        val authenticated = object : OutputStream() {
            override fun write(value: Int) { mac.update(value.toByte()); encrypted.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { mac.update(bytes, offset, length); encrypted.write(bytes, offset, length) }
        }
        val data = DataOutputStream(authenticated)
        files.forEach { file -> data.writeLong(file.length()); file.inputStream().use { it.copyTo(data) } }
        encrypted.write(mac.doFinal()); encrypted.finish(); output.flush()
    }

    fun read(input: InputStream, folder: File, password: CharArray) {
        require(password.size in 12..256)
        val data = DataInputStream(input)
        val header = ByteArray(MAGIC.size + 16 + 8 + 8).also(data::readFully)
        val fields = DataInputStream(header.inputStream())
        require(ByteArray(MAGIC.size).also(fields::readFully).contentEquals(MAGIC)) { "Unsupported Beta archive" }
        val salt = ByteArray(16).also(fields::readFully)
        val nonce = ByteArray(8).also(fields::readFully)
        val length = fields.readLong()
        require(length in 35..MAX_PAYLOAD) { "Invalid Beta archive size" }
        val decrypted = ChunkInput(data, header, nonce, derive(password, salt), length)
        val mac = Mac.getInstance("HmacSHA256").apply { init(originKey(false)); update(MAGIC) }
        val authenticated = object : InputStream() {
            override fun read(): Int = decrypted.read().also { if (it >= 0) mac.update(it.toByte()) }
            override fun read(bytes: ByteArray, offset: Int, count: Int): Int = decrypted.read(bytes, offset, count).also { if (it > 0) mac.update(bytes, offset, it) }
        }
        val payload = DataInputStream(authenticated)
        var used = 32L
        ENTRIES.forEachIndexed { index, name ->
            val size = payload.readLong(); used += 8 + size
            require(size in 1..limit(index) && used <= length) { "Invalid Beta archive entry" }
            FileOutputStream(File(folder, name)).use { file ->
                var remaining = size
                val buffer = ByteArray(CHUNK)
                while (remaining > 0) { val count = payload.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt()); require(count > 0); file.write(buffer, 0, count); remaining -= count }
                file.fd.sync()
            }
        }
        require(used == length) { "Unexpected Beta archive payload" }
        val expected = mac.doFinal()
        val actual = ByteArray(32).also { DataInputStream(decrypted).readFully(it) }
        require(MessageDigest.isEqual(actual, expected)) { "Untrusted Beta archive origin" }
        require(decrypted.read() == -1 && input.read() == -1) { "Trailing Beta archive data" }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }

    private fun crypt(mode: Int, key: SecretKey, nonce: ByteArray, header: ByteArray, index: Int, bytes: ByteArray): ByteArray {
        val indexBytes = java.nio.ByteBuffer.allocate(4).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, key, GCMParameterSpec(128, nonce + indexBytes)); updateAAD(header); updateAAD(indexBytes); doFinal(bytes)
        }
    }
    private inner class ChunkOutput(val output: OutputStream, val header: ByteArray, val nonce: ByteArray, val key: SecretKey) : OutputStream() {
        val buffer = ByteArrayOutputStream(CHUNK); var index = 0
        override fun write(value: Int) { buffer.write(value); if (buffer.size() == CHUNK) emit() }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var position = offset; var remaining = length
            while (remaining > 0) { val count = minOf(CHUNK - buffer.size(), remaining); buffer.write(bytes, position, count); position += count; remaining -= count; if (buffer.size() == CHUNK) emit() }
        }
        fun emit() { val plain = buffer.toByteArray(); try { output.write(crypt(Cipher.ENCRYPT_MODE, key, nonce, header, index++, plain)) } finally { plain.fill(0); buffer.reset() } }
        fun finish() { if (buffer.size() > 0) emit() }
    }
    private inner class ChunkInput(val input: DataInputStream, val header: ByteArray, val nonce: ByteArray, val key: SecretKey, var remaining: Long) : InputStream() {
        var buffer = ByteArrayInputStream(byteArrayOf()); var index = 0
        override fun read(): Int { if (!next()) return -1; return buffer.read() }
        override fun read(bytes: ByteArray, offset: Int, count: Int): Int { if (count == 0) return 0; if (!next()) return -1; return buffer.read(bytes, offset, count) }
        fun next(): Boolean {
            if (buffer.available() > 0) return true
            if (remaining == 0L) return false
            val count = minOf(CHUNK.toLong(), remaining).toInt()
            val cipher = ByteArray(count + 16).also(input::readFully)
            buffer = ByteArrayInputStream(crypt(Cipher.DECRYPT_MODE, key, nonce, header, index++, cipher)); remaining -= count
            return true
        }
    }
    companion object {
        private val MAGIC = "MASROOF:BETA:ARCHIVE:1\u0000".toByteArray(Charsets.US_ASCII)
        private val ENTRIES = listOf("database.db", "preferences.json", "manifest.json")
        private const val CHUNK = 64 * 1024
        private const val MAX_PAYLOAD = 512L * 1024 * 1024 + 4L * 1024 * 1024 + 56
        private fun limit(index: Int) = if (index == 0) 512L * 1024 * 1024 else 2L * 1024 * 1024
        private fun installationKey(create: Boolean): SecretKey = synchronized(BetaEncryptedArchive::class.java) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "masroof.beta.archive.origin.v1"
            val retained = store.getKey(alias, null)
            if (retained != null) {
                check(retained is SecretKey) { "Invalid Beta archive origin key" }
                retained
            } else {
                check(!store.containsAlias(alias)) { "Invalid Beta archive origin alias" }
                check(create) { "Beta archive origin key is unavailable" }
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).setKeySize(256).build()); generateKey()
                }
            }
        }
    }
}
