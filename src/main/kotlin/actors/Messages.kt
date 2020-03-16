package actors

import java.net.URI

data class SearchRequest(val text: String)

data class Answer(val uri: URI, val searcherName: String, val payload: String = "")

data class SearchResponse(val searcherName: String, val answers: List<Answer>)

data class ErrorResponse(val searcherName: String, val error: String)

data class TimeoutResponse(val searcherName: String)

data class AggregatedResult(val answers: List<Answer>)
