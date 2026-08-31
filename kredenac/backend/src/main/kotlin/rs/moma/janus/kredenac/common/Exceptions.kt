package rs.moma.janus.kredenac.common

sealed class ApiException(val code: String?, message: String) : RuntimeException(message)

class BadRequestException(message: String = "", code: String? = null) : ApiException(code, message)
class UnauthorizedException(message: String, code: String? = null) : ApiException(code, message)
class ForbiddenException(message: String, code: String? = null) : ApiException(code, message)
class NotFoundException(message: String, code: String? = null) : ApiException(code, message)
class ConflictException(message: String, code: String? = null) : ApiException(code, message)
class CompromisedException(message: String) : ApiException(null, message)
