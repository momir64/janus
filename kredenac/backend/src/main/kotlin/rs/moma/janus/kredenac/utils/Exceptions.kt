package rs.moma.janus.kredenac.utils

sealed class ApiException(message: String) : RuntimeException(message)

class UnauthorizedException(message: String) : ApiException(message)
class BadRequestException(message: String) : ApiException(message)
class ForbiddenException(message: String) : ApiException(message)
class NotFoundException(message: String) : ApiException(message)
class ConflictException(message: String) : ApiException(message)
