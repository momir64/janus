package rs.moma.janus.lokot.files

import rs.moma.janus.lokot.io.secretsRoot

interface Destination {
    val root: String
    val envFile: String
    val vaultFile: String

    fun makeDirectory(path: String): Boolean
    fun write(path: String, bytes: ByteArray): Boolean
    fun read(path: String): String?
    fun readBytes(path: String): ByteArray?
    fun list(path: String): List<String>
    fun delete(path: String): Boolean
    fun deleteDirectory(path: String): Boolean
    fun close() {}
}

class LocalDestination(override val envFile: String, override val vaultFile: String) : Destination {
    override val root = secretsRoot()

    override fun makeDirectory(path: String) = Files.makeDirectory(path)
    override fun write(path: String, bytes: ByteArray) = runCatching { Files.writeSecret(path, bytes) }.isSuccess
    override fun read(path: String) = Files.readText(path)
    override fun readBytes(path: String) = Files.readBytes(path)
    override fun list(path: String) = Files.list(path)
    override fun delete(path: String) = Files.delete(path)
    override fun deleteDirectory(path: String) = Files.deleteDirectory(path)
}

class RemoteDestination(
    private val sftp: Sftp,
    uid: Int?,
    directory: String,
    envFile: String,
    vaultFile: String,
) : Destination {
    override val root = uid?.let { "/dev/shm/lokot-$it" } ?: "$directory/.lokot-secrets"
    override val envFile = "$directory/$envFile"
    override val vaultFile = "$directory/$vaultFile"

    override fun makeDirectory(path: String) = sftp.makeDirectory(path, DIRECTORY_MODE)
    override fun write(path: String, bytes: ByteArray) = sftp.write(path, bytes, FILE_MODE)
    override fun read(path: String) = sftp.read(path)?.decodeToString()
    override fun readBytes(path: String) = sftp.read(path)
    override fun list(path: String) = sftp.list(path).orEmpty()
    override fun delete(path: String) = sftp.delete(path)
    override fun deleteDirectory(path: String) = sftp.deleteDirectory(path)
    override fun close() {
        sftp.close()
    }

    companion object {
        private const val DIRECTORY_MODE = 448 // 0700, the gate
        private const val FILE_MODE = 420      // 0644, so a container that is not root can read it
    }
}
