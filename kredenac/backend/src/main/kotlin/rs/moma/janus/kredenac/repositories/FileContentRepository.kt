package rs.moma.janus.kredenac.repositories

import rs.moma.janus.kredenac.common.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.minio.BucketExistsArgs
import io.minio.RemoveObjectArgs
import io.minio.MakeBucketArgs
import io.minio.GetObjectArgs
import io.minio.PutObjectArgs
import io.minio.MinioClient
import java.io.InputStream
import kotlin.uuid.Uuid

class FileContentRepository(
    host: String,
    port: String,
    accessKey: String,
    secretKey: String,
    private val bucket: String
) {
    private val client = MinioClient.builder().endpoint("http://$host:$port")
        .credentials(accessKey, secretKey).build()

    init {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
    }

    private fun objectKey(userId: Uuid, fileId: Uuid) = "$userId/$fileId"

    context(owner: Owner)
    suspend fun put(fileId: Uuid, stream: InputStream, size: Long) {
        withContext(Dispatchers.IO) {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey(owner.userId, fileId))
                    .stream(stream, size, -1)
                    .build()
            )
        }
    }

    context(owner: Owner)
    suspend fun get(fileId: Uuid): InputStream = withContext(Dispatchers.IO) {
        client.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectKey(owner.userId, fileId))
                .build()
        )
    }

    context(owner: Owner)
    suspend fun delete(fileId: Uuid) = withContext(Dispatchers.IO) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectKey(owner.userId, fileId))
                .build()
        )
    }
}