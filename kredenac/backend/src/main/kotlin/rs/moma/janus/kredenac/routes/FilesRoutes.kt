package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.plugins.authenticatedDelete
import rs.moma.janus.kredenac.common.BadRequestException
import io.ktor.http.ContentType.Application.OctetStream
import rs.moma.janus.kredenac.plugins.authenticatedPost
import rs.moma.janus.kredenac.plugins.authenticatedGet
import rs.moma.janus.kredenac.service.FilesService
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.http.content.forEachPart
import io.ktor.http.content.PartData
import io.ktor.server.util.getOrFail
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.ktor.http.*

fun Route.filesRoutes() {
    val filesService: FilesService by inject()

    route("/files") {
        authenticatedGet("") {
            call.respond(filesService.list())
        }

        authenticatedPost("") {
            var declaredSize: Long? = null
            var uploaded = false

            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "size") declaredSize = part.value.toLongOrNull()
                    }
                    is PartData.FileItem -> {
                        val size = declaredSize ?: throw BadRequestException("Missing size field")
                        val filename = part.originalFileName ?: "file"
                        val contentType = part.contentType?.toString() ?: OctetStream.toString()
                        filesService.upload(filename, contentType, part.provider().toInputStream(), size)
                        uploaded = true
                    }
                    else -> {}
                }
                part.release()
            }

            if (!uploaded) throw BadRequestException("No file provided")
            call.respond(HttpStatusCode.Created)
        }

        authenticatedGet("/{id}") {
            val fileId = Uuid.parse(call.parameters.getOrFail("id"))
            val file = filesService.download(fileId)
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.filename}\"")
            call.respondBytes(file.bytes, ContentType.parse(file.contentType))
        }

        authenticatedDelete("/{id}") {
            val fileId = Uuid.parse(call.parameters.getOrFail("id"))
            filesService.delete(fileId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
