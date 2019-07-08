package no.nav.helse

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dusseldorf.ktor.core.DefaultProbeRoutes
import no.nav.helse.dusseldorf.ktor.core.id
import no.nav.helse.dusseldorf.ktor.core.logProxyProperties
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import org.slf4j.Logger
import org.slf4j.LoggerFactory


private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengesoknadMottak")

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengesoknadMottak() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()


    install(Routing) {
        MetricsRoute()
        DefaultProbeRoutes()
    }

}