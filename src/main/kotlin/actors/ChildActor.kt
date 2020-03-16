package actors

import api.Response
import api.SearchAPI
import akka.actor.UntypedAbstractActor

class ChildActor(
    private val searcherName: String,
    private val searcher: SearchAPI
) : UntypedAbstractActor() {

    init {
    }

    override fun onReceive(message: Any?) = when (message) {
        is SearchRequest -> {
            val resp = searcher.search(message.text)
            when (resp) {
                is Response.Correct -> sender.tell(
                    SearchResponse(
                        searcherName,
                        resp.items.take(5).map { Answer(it.uri, searcherName, it.payload) }
                    ), self
                )
                is Response.Error -> sender.tell(ErrorResponse(searcherName, resp.error), self)
                Response.Timeout -> Unit
            }
        }

        else -> sender.tell(ErrorResponse(searcherName, "Unexpected request received: $message"), self)
    }

}