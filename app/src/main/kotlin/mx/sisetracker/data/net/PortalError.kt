package mx.sisetracker.data.net

import java.io.IOException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import mx.sisetracker.core.SiseParseException

/** Why a portal request failed, as far as the user needs to know. */
enum class PortalError {
    /** No connection, timeout… */
    NETWORK,

    /** The secure connection couldn't be verified (e.g. a certificate chain the device doesn't trust). */
    SECURE_CONNECTION,

    /** The portal answered with an HTTP error. */
    SERVER,

    /** The page didn't look as expected: the portal may have changed. */
    UNEXPECTED_PAGE,
}

sealed interface PortalResult<out T> {
    data class Ok<T>(val value: T) : PortalResult<T>

    data class Failed(val error: PortalError) : PortalResult<Nothing>
}

/** Runs a portal call, mapping its expected failures to [PortalError]. */
suspend fun <T> portalCall(block: suspend () -> T): PortalResult<T> =
    try {
        PortalResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SiseHttpException) {
        PortalResult.Failed(PortalError.SERVER)
    } catch (e: SSLException) {
        PortalResult.Failed(PortalError.SECURE_CONNECTION)
    } catch (e: IOException) {
        PortalResult.Failed(PortalError.NETWORK)
    } catch (e: SiseParseException) {
        PortalResult.Failed(PortalError.UNEXPECTED_PAGE)
    }
