package rs.moma.janus.kredenac.services

import rs.moma.janus.kredenac.repositories.FileContentRepository
import rs.moma.janus.kredenac.repositories.FilesRepository
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.common.MAX_FILENAME_LENGTH
import rs.moma.janus.kredenac.common.MAX_FILE_SIZE_BYTES
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import rs.moma.janus.kredenac.common.NotFoundException
import rs.moma.janus.kredenac.common.MAX_FILE_SIZE_MB
import rs.moma.janus.kredenac.repositories.StoredFile
import rs.moma.janus.kredenac.common.Owner
import rs.moma.janus.kredenac.dtos.FileDto
import javax.crypto.AEADBadTagException
import kotlinx.io.IOException
import java.io.InputStream
import kotlin.uuid.Uuid

class DecryptedFile(val filename: String, val bytes: ByteArray)

class FilesService(
    private val filesRepository: FilesRepository,
    private val fileContentRepository: FileContentRepository,
    private val userService: UserService
) {
    context(owner: Owner)
    suspend fun list(): List<FileDto> {
        val encryptionKey = userService.getEncryptionKey()
        return filesRepository.findAll().map { it.toDto(encryptionKey) }
    }

    context(owner: Owner)
    suspend fun upload(filename: String, stream: InputStream, declaredSize: Long) {
        if (declaredSize !in 0..MAX_FILE_SIZE_BYTES)
            throw BadRequestException("File is larger than the $MAX_FILE_SIZE_MB MB limit")
        if (filename.length > MAX_FILENAME_LENGTH)
            throw BadRequestException("Filename is longer than $MAX_FILENAME_LENGTH characters")

        val encryptionKey = userService.getEncryptionKey()
        val id = Uuid.random()

        val totalSize = declaredSize + AesUtil.GCM_TAG_LENGTH_BYTES
        val fileStream = SizeLimitedInputStream(stream, declaredSize)
        val encryptedFile = AesUtil.encrypt(encryptionKey, fileStream, id.toByteArray())

        try {
            fileContentRepository.put(id, encryptedFile.stream, totalSize)
        } catch (e: Exception) {
            if (e is SizeLimitExceededException || e.cause is SizeLimitExceededException)
                throw BadRequestException("Uploaded file did not match declared size")
            throw e
        }

        val filename = AesUtil.encrypt(encryptionKey, filename.toByteArray(), id.toByteArray())
        filesRepository.insert(id, filename.ciphertext, filename.iv, encryptedFile.iv, fileStream.count)
    }

    context(owner: Owner)
    suspend fun download(id: Uuid): DecryptedFile {
        val stored = filesRepository.findById(id) ?: throw NotFoundException("File not found")
        val key = userService.getEncryptionKey()

        val filename = AesUtil.decrypt(key, stored.encryptedFilename, stored.encryptedFilenameIv, id.toByteArray())
        val ciphertext = fileContentRepository.get(id)
        val content = try {
            AesUtil.decrypt(key, ciphertext, stored.encryptedContentIv, id.toByteArray()).use { it.readBytes() }
        } catch (e: IOException) {
            if (e.cause is AEADBadTagException)
                throw CompromisedException("File content for file=$id failed integrity check")
            throw e
        }

        return DecryptedFile(String(filename), content)
    }

    context(owner: Owner)
    suspend fun delete(fileId: Uuid) {
        filesRepository.findById(fileId) ?: throw NotFoundException("File not found")
        fileContentRepository.delete(fileId)
        filesRepository.delete(fileId)
    }

    private fun StoredFile.toDto(encryptionKey: ByteArray): FileDto {
        val filename = String(AesUtil.decrypt(encryptionKey, encryptedFilename, encryptedFilenameIv, id.toByteArray()))
        return FileDto(id.toString(), filename, size, createdAt.toString())
    }
}

private class SizeLimitExceededException(message: String) : IOException(message)

private class SizeLimitedInputStream(private val delegate: InputStream, private val maxSize: Long) : InputStream() {
    var count = 0L; private set

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) checkAndCount(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n != -1) checkAndCount(n)
        return n
    }

    override fun close() = delegate.close()

    private fun checkAndCount(n: Int) {
        count += n
        if (count > maxSize)
            throw SizeLimitExceededException("Declared size exceeded")
    }
}

