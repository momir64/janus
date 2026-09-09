package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.plugins.authenticatedDelete
import rs.moma.janus.kredenac.common.BadRequestException
import io.ktor.http.ContentType.Application.OctetStream
import rs.moma.janus.kredenac.plugins.authenticatedPost
import rs.moma.janus.kredenac.plugins.authenticatedGet
import rs.moma.janus.kredenac.services.FilesService
import io.ktor.utils.io.jvm.javaio.toInputStream
import rs.moma.janus.kredenac.common.uuidParam
import io.ktor.http.content.forEachPart
import io.ktor.http.content.PartData
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.filesRoutes() {
    val filesService: FilesService by inject()

    route("/files") {
        authenticatedGet("", privezakOnly = true) {
            call.respond(filesService.list())
        }

        authenticatedPost("", privezakOnly = true) {
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
                        filesService.upload(filename, part.provider().toInputStream(), size)
                        uploaded = true
                    }
                    else -> {}
                }
                part.release()
            }

            if (!uploaded) throw BadRequestException("No file provided")
            call.respond(HttpStatusCode.Created)
        }

        authenticatedGet("/{id}", privezakOnly = true) {
            val fileId = call.uuidParam("id")
            val file = filesService.download(fileId)
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.filename}\"")
            call.respondBytes(file.bytes, OctetStream)
        }

        authenticatedDelete("/{id}", privezakOnly = true) {
            val fileId = call.uuidParam("id")
            filesService.delete(fileId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
