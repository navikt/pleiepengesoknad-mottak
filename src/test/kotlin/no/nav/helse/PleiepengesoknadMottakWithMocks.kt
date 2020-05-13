package no.nav.helse

import io.ktor.server.testing.withApplication
import no.nav.helse.dusseldorf.testsupport.asArguments
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PleiepengesoknadMottakWithMocks {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PleiepengesoknadMottakWithMocks::class.java)


        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WireMockBuilder()
                .withPort(8141)
                .withAzureSupport()
                .build()
                .stubK9DokumentHealth()
                .stubLagreDokument()
                .stubAktoerRegisterGetAktoerId("02119970078", "1234561")
                .stubKafkaSchemaRegistry("/subjects/aapen-brukernotifikasjon-nyBeskjed-v1-key/versions")

            val kafkaEnvironment = KafkaWrapper.bootstrap()

            val testArgs = TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                kafkaEnvironment = kafkaEnvironment,
                port = 8142
            ).asArguments()

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
