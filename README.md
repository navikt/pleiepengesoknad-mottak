# pleiepengesoknad-mottak

[![CircleCI](https://circleci.com/gh/navikt/pleiepengesoknad-mottak/tree/master.svg?style=svg)](https://circleci.com/gh/navikt/pleiepengesoknad-mottak/tree/master)

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

### Format på søknad lagt på kafka
attributten "data" er tilsvarende søknaden som kommer inn i REST-API'et med noen unntak:
- "soker.aktoer_id" er lagt til
- "vedlegg" er byttet ut med "vedlegg_urls" som peker på vedleggene mellomlagret i [pleiepenger-dokument](https://github.com/navikt/pleiepenger-dokument)
- "soknad_id" lagt til

```json
{
	"metadata:": {
		"version": 1,
		"correlation_id": "b3106960-0a85-4e02-9221-6ae057c8e93f",
		"request_id": "b3106960-0a85-4e02-9221-6ae05456asd841"
	},
	"data": {
		"soknad_id": "ff106960-0a85-4e02-9221-6ae057c8e93f",
		"mottatt": "2019-02-15T20:43:32Z",
		"fra_og_med": "2018-10-10",
		"til_og_med": "2019-10-10",
		"soker": {
			"aktoer_id": "1234567",
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
		"vedlegg_urls": [
			"https://pleiepenger-dokument/v1/b3106960-0a85-4e02-9221-6ae05456asd888"
		],
		"medlemskap": {
			"har_bodd_i_utlandet_siste_12_mnd": false,
			"skal_bo_i_utlandet_neste_12_mnd": false
		},
		"grad": 100,
		"har_medsoker": true,
		"har_bekreftet_opplysninger": true,
		"har_forstatt_rettigheter_og_plikter": true
	}
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

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

Interne henvendelser kan sendes via Slack i kanalen #team-düsseldorf.
