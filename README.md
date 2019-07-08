# pleiepengesoknad-mottak

Tjeneste som tar imot søknader om pleiepenger og legger de til til prosessering.
Mottar søknad som REST API-kall. Legges videre på en Kafka Topic som tjenesten [pleiepengesoknad-prosessering](https://github.com/navikt/pleiepengesoknad-prosessering) prosesserer.

## Versjon 1
### Path
/v1/soknad

### Meldingsformat
- Gir 202 response med SøknadId som entity på formatet ```{"id":"b3106960-0a85-4e02-9221-6ae057c8e93f"}```
- soker.mellomnavn er ikke påkrevd.
- ingen av attributtene for "barn" er påkrevd.
- arbeidsgivere kan være en tom liste
- arbeidsgivere[x].navn er ikke påkrevd.
- vedlegg[x] kan inneholde en JSON med vedlegg på format som vist i eksempel hvor "content" er base64 encoded vedlegg
- Det må være satt minst ett vedlegg.

```json
{
	"mottatt": "2019-02-15T20:43:32Z",
	"fra_og_med": "2018-10-10",
	"til_og_med": "2019-10-10",
	"soker": {
		"fodselsnummer": "290990123456",
		"fornavn": "MOR",
		"mellomnavn": "HEISANN",
		"etternavn": "MORSEN"
	},
	"barn": {
		"fodselsnummer": "25099012345",
		"alternativ_id": null,
		"navn": "Santa Heisann Winter"
	},
	"relasjon_til_barnet": "MOR",
	"arbeidsgivere": {
		"organisasjoner": [{
			"navn": "Bjeffefirmaet",
			"organisasjonsnummer": "897895478"
		}]
	},
	"vedlegg": [{
		"content": "iVBORw0KGg....ayne82ZEAAAAASUVORK5CYII=",
		"content_type": "image/png",
		"title": "Legeerklæring"
	}],
	"medlemskap": {
		"har_bodd_i_utlandet_siste_12_mnd": false,
		"skal_bo_i_utlandet_neste_12_mnd": false
	},
	"grad": 100,
	"har_medsoker": true,
	"har_bekreftet_opplysninger": true,
	"har_forstatt_rettigheter_og_plikter": true
}
```

### Metadata
#### Correlation ID vs Request ID
Correlation ID blir propagert videre, og har ikke nødvendigvis sitt opphav hos konsumenten.
Request ID blir ikke propagert videre, og skal ha sitt opphav hos konsumenten om den settes.

#### REST API
- Correlation ID må sendes som header 'X-Correlation-ID'
- Request ID kan sendes som heder 'X-Request-ID'
- Versjon på meldingen avledes fra pathen '/v1/soknad' -> 1

## For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #område-helse.
