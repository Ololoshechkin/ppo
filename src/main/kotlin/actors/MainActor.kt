package actors

import akka.actor.*
import api.SearchAPI
import scala.concurrent.duration.FiniteDuration

data class ChildActorDescriptor(val searcherName: String, val searcher: SearchAPI)

const val TIMEOUT = "timeout"

class MainActor(
    requestText: String,
    private val requesterRef: ActorRef,
    private val timeout: FiniteDuration,
    childActors: List<ChildActorDescriptor>,
    private val report: (String, String) -> Unit // <searcher-name, error> --> what to do?
) : UntypedAbstractActor() {

    private val notRespondedSearchers = hashSetOf<String>()

    private val result = mutableListOf<Answer>()

    private val system: ActorSystem
        get() = context.system()

    init {
        childActors.forEachIndexed { i, (searcherName, searcher) ->
            notRespondedSearchers += searcherName

            val child =
                system.actorOf(Props.create(ChildActor::class.java, searcherName, searcher), "child$i")
            child.tell(SearchRequest(requestText), self)

            system
                .scheduler()
                .scheduleOnce(
                    timeout,
                    /*receiver = */self,
                    /*text = */TimeoutResponse(searcherName),
                    system.dispatcher(),
                    /*sender = */self
                )
        }
    }

    private fun tellResultIfReady() {
        if (notRespondedSearchers.isEmpty()) {
            requesterRef.tell(AggregatedResult(result.sortedBy { it.searcherName }), self)
        }
    }

    override fun onReceive(message: Any?) =
        when (message) {
            is SearchResponse -> {
                result += message.answers
                notRespondedSearchers -= message.searcherName

                tellResultIfReady()
            }

            is TimeoutResponse -> if (message.searcherName in notRespondedSearchers) {

                report(
                    message.searcherName,
                    TIMEOUT
                )
                notRespondedSearchers -= message.searcherName

                tellResultIfReady()
            } else Unit

            is ErrorResponse -> report(message.searcherName, message.error).also {
            }

            else -> {
                throw IllegalStateException("Master received unexpected message: $message")
            }
        }

}