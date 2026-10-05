package mx.sisetracker.testing

import mx.sisetracker.core.FormRequest
import mx.sisetracker.data.net.SiseClient

/** A portal that answers from fixtures, recording every request. Never touches the network. */
class FakeSiseClient : SiseClient {
    val requests = mutableListOf<String>()

    var onGet: (url: String) -> String = { error("Unexpected GET $it") }
    var onPost: (request: FormRequest) -> String = { error("Unexpected POST $it") }

    override suspend fun getPage(url: String): String {
        requests += "GET $url"
        return onGet(url)
    }

    override suspend fun postForm(request: FormRequest): String {
        requests += "POST ${request.url} ${request.encodedBody}"
        return onPost(request)
    }
}
