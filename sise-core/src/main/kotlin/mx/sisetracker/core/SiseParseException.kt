package mx.sisetracker.core

/** A portal page doesn't have the structure the parsers expect (the portal may have changed). */
class SiseParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
