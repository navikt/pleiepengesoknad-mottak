package no.nav.helse.mottak.v1.dittnav

class ProduceBeskjedDto(val tekst: String, val link: String) {
    override fun toString(): String {
        return "ProduceBeskjedDto{tekst='$tekst', link='$link'}"
    }
}
