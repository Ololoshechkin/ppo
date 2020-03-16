package api

import java.net.URI

sealed class Response {
    class Item(val uri: URI, val payload: String = "")

    class Correct(vararg val items: Item) : Response()
    class Error(val error: String) : Response()
    object Timeout : Response()
}

sealed class Request {
    class Regular(val text: String) : Request()
    object Any : Request()
}

interface SearchAPI {
    fun search(text: String): Response
}

class SearchBuilder {

    data class SearchCase(val request: Request, val response: Response)

    private val searchCases = arrayListOf<SearchCase>()

    fun match(request: String, response: Response): SearchBuilder {
        searchCases.add(SearchCase(Request.Regular(request), response))

        return this
    }

    fun matchAny(response: Response): SearchBuilder {
        searchCases.add(SearchCase(Request.Any, response))

        return this
    }

    fun build(): SearchAPI = object : SearchAPI {

        override fun search(text: String): Response {
            searchCases.forEach { (request, response) ->
                when (request) {
                    is Request.Regular -> {
                        if (request.text == text) return response
                    }
                    is Request.Any -> return response
                }
            }

            return Response.Error("Unimplemented request: $text")
        }

    }
}