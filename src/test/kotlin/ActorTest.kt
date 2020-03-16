import actors.*
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import api.Response
import api.SearchBuilder
import junit.framework.TestCase
import org.junit.Test
import scala.concurrent.duration.`FiniteDuration$`
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit

class ActorTest : TestCase() {

    fun urls(vararg urls: String) = Response.Correct(*urls.map { url ->
        Response.Item(uri = URI.create(url))
    }.toTypedArray())

    val G_GOOGLE_RESPONSE = urls(
        "www.google.ru",
        "www.google.com",
        "https://twitter.com/Google",
        "about.google",
        "https://www.blog.google"
    )

    private val DOLLARS = "$$$$$$$$$$$$$$$$$$$$$$$"

    private val PERLAMUTER = "перламутровое"

    private val G_PERLAMUTER_RESPONCE = urls(
        "https://ru.wikipedia.org/wiki/Перламутровые_папулы",
        "https://ru.wikipedia.org/wiki/Перламутр",
        "https://zen.yandex.ru/media/vsvete/perlamutrovoe-shampanskoe-pit-ili-ne-pit-5c120de6f4d8a900aa368802",
        "https://borderlands.fandom.com/ru/wiki/Перламутровые_предметы",
        "https://alexanderbar.ru/stati/perlamutrovoe-shampanskoe-aviva/"
    )

    private val BAREL_ROLL = "do a barrel roll"

    private val G_BAREL_ROLL_RESPONCE = urls(
        "https://www.youtube.com/watch?v=_dXbC2xqNZg",
        "https://elgoog.im/doabarrelroll/",
        "https://www.dictionary.com/e/memes/do-a-barrel-roll/",
        "https://searchengineland.com/google-do-a-barrel-roll-99933",
        "https://mashable.com/2011/11/03/google-easter-eggs-2/"
    )

    private val PLEASE_DO_A_TIMEOUT = "please, do a timeout for me"

    val googleSearch = SearchBuilder()
        .match(
            request = "google",
            response = G_GOOGLE_RESPONSE
        )
        .match(
            request = PERLAMUTER,
            response = G_PERLAMUTER_RESPONCE
        )
        .match(
            request = BAREL_ROLL,
            response = G_BAREL_ROLL_RESPONCE
        )
        .match(
            request = DOLLARS,
            response = urls("Too.many.dollars.for.Russian.economy")
        )
        .match(
            request = PLEASE_DO_A_TIMEOUT,
            response = Response.Timeout
        )
        .matchAny(
            response = Response.Timeout
        )
        .build()

    val Y_GOOGLE_RESPONSE = urls(
        "www.google.ru",
        "www.gmail.com",
        "https://yandex.ru/images/search?text=google&stype=image&lr=2&parent-reqid=1583790537469236-236666156580172447900067-man1-4065&source=wiz",
        "https://www.google.ru/maps",
        "https://www.google.ru/chrome/"
    )

    val yandexSearch = SearchBuilder()
        .match(
            request = "google",
            response = Y_GOOGLE_RESPONSE
        )
        .match(
            request = "do a barrel roll",
            response = Response.Error("Not a yandex trick")
        )
        .match(
            request = DOLLARS,
            response = Response.Timeout
        )
        .match(
            request = PLEASE_DO_A_TIMEOUT,
            response = Response.Timeout
        )
        .matchAny(
            response = urls("ko.ko.ko")
        )
        .build()

    val B_GOOGLE_RESPONSE = urls(
        "https://www.facebook.com",
        "www.google.ru",
        "www.google.com",
        "www.maps.google.com",
        "http://www.bing.com/videos/search?q=google&qpvt=google&FORM=VDRE",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah",
        "blah.blah"
    )

    val B_GOOGLE_RESPONSE_TOP5 = urls(
        "https://www.facebook.com",
        "www.google.ru",
        "www.google.com",
        "www.maps.google.com",
        "http://www.bing.com/videos/search?q=google&qpvt=google&FORM=VDRE"
    )

    val bingSearch = SearchBuilder()
        .match(
            request = "google",
            response = B_GOOGLE_RESPONSE
        )
        .match(
            request = PLEASE_DO_A_TIMEOUT,
            response = Response.Timeout
        )
        .matchAny(
            response = Response.Timeout
        )
        .build()

    val google = ChildActorDescriptor("Google", googleSearch)
    val yandex = ChildActorDescriptor("Яндекс", yandexSearch)
    val bing = ChildActorDescriptor("Bing", bingSearch)

    fun Collection<Pair<Response.Correct, String>>.toResult() =
        sortedBy { it.second }
            .map { (resp, searcherName) ->
                resp.items.map {
                    Answer(
                        uri = it.uri,
                        searcherName = searcherName,
                        payload = it.payload
                    )
                }
            }
            .flatten()
            .let(::AggregatedResult)


    private fun errorText(searcherName: String, error: String) = "Searcher: $searcherName got an error: ${error}"

    private fun report(searcherName: String, error: String) = when (error) {
        TIMEOUT -> println(errorText(searcherName, error))
        else -> throw Exception(errorText(searcherName, error))
    }

    @Test
    fun testSingle() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */"google",
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(100, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google),
                        /*report = */::report
                    ), "MainActor"
                )

                probe.expectMsg(
                    Duration.ofMillis(1000),
                    listOf(G_GOOGLE_RESPONSE to "Google").toResult()

                )
            }
        }
    }

    @Test
    fun testThree() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */"google",
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(100, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google, yandex, bing),
                        /*report = */::report
                    ), "MainActor"
                )

                probe.expectMsg(
                    Duration.ofMillis(500),
                    listOf(
                        G_GOOGLE_RESPONSE to "Google",
                        Y_GOOGLE_RESPONSE to "Яндекс",
                        B_GOOGLE_RESPONSE_TOP5 to "Bing"
                    ).toResult()
                )
            }
        }
    }

    @Test
    fun testOneTimeout() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */PERLAMUTER,
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(50, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google, yandex, bing),
                        /*report = */::report
                    ), "MainActor"
                )

                probe.expectMsg(
                    Duration.ofMillis(500),
                    listOf(G_PERLAMUTER_RESPONCE to "Google", urls("ko.ko.ko") to "Яндекс").toResult()
                )
            }
        }
    }

    @Test
    fun testTwoTimeouts() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */DOLLARS,
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(50, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google, yandex, bing),
                        /*report = */::report
                    ), "MainActor"
                )

                probe.expectMsg(
                    Duration.ofMillis(500),
                    listOf(urls("Too.many.dollars.for.Russian.economy") to "Google").toResult()
                )
            }
        }
    }


    @Test
    fun testAllTimeouts() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */PLEASE_DO_A_TIMEOUT,
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(50, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google, yandex, bing),
                        /*report = */::report
                    ), "MainActor"
                )

                probe.expectMsg(
                    Duration.ofMillis(500),
                    AggregatedResult(emptyList())
                )
            }
        }
    }

    @Test
    fun testError() {
        val system = ActorSystem.create()

        object : TestKit(system) {
            init {
                val probe = TestKit(system)

                val errors = mutableListOf<String>()

                fun collectingReport(searcherName: String, error: String) = when (error) {
                    TIMEOUT -> println(errorText(searcherName, error))
                    else -> errors += errorText(searcherName, error)
                }

                system.actorOf(
                    Props.create(
                        MainActor::class.java,
                        /*requestText = */BAREL_ROLL,
                        /*requesterRef = */ probe.ref,
                        /*timeout = */`FiniteDuration$`.`MODULE$`.apply(50, TimeUnit.MILLISECONDS),
                        /*childActors = */ listOf(google, yandex, bing),
                        /*report = */::collectingReport
                    ), "MainActor"
                )


                probe.expectMsg(
                    Duration.ofMillis(500),
                    listOf(G_BAREL_ROLL_RESPONCE to "Google").toResult()
                )

                assertEquals(errors, listOf("Searcher: Яндекс got an error: Not a yandex trick"))
            }
        }
    }
}