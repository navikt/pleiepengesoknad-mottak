package no.nav.helse

import io.ktor.server.testing.withApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("no.nav.PleiepengesoknadMottakWithMocks")

class PleiepengesoknadMottakWithMocks {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WiremockWrapper.bootstrap(port = 8141)
            val kafkaEnvironment = KafkaWrapper.bootstrap()

            val testArgs = TestConfiguration.asArray(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                kafkaEnvironment = kafkaEnvironment,
                port = 8142
            ))

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    kafkaEnvironment.tearDown()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.helse.main(testArgs) }
        }
    }
}