# pleiepengesoknad-mottak

[![CircleCI](https://circleci.com/gh/navikt/pleiepengesoknad-mottak/tree/master.svg?style=svg)](https://circleci.com/gh/navikt/pleiepengesoknad-mottak/tree/master)

Tjeneste som tar imot søknader om pleiepenger og legger de til til prosessering.
Mottar søknad som REST API-kall. Legges videre på en Kafka Topic som tjenesten [pleiepengesoknad-prosessering](https://github.com/navikt/pleiepengesoknad-prosessering) prosesserer.
Tjenesten lagrer vedlegg slik at det kun er en referanse til vedleggene som legges på topic.

## Versjon 1
### Path
/v1/soknad

### Meldingsformat
- Gir 202 response med SøknadId som entity på formatet ```{"id":"b3106960-0a85-4e02-9221-6ae057c8e93f"}```
- Må være gyldig JSON
- Må inneholde soker.fodselsnummer som er et gyldig fødselsnummer/D-nummer
- Må inneholder soker.aktoer_id som er en gyldig aktør ID
- Må inneholde en liste vedlegg som inneholder mist et vedlegg på gyldig format
- vedlegg[x].content må være Base64 encoded vedlegg.
- Utover dette validerer ikke tjenesten ytterligere felter som sendes om en del av meldingen.

```json
{
	"soker": {
        "aktoer_id": "1234567",
		"fodselsnummer": "290990123456"
	},
	"vedlegg": [{
		"content": "iVBORw0KGg....ayne82ZEAAAAASUVORK5CYII=",
		"content_type": "image/png",
		"title": "Legeerklæring"
	}]
}
```

### Format på søknad lagt på kafka
attributten "data" er tilsvarende søknaden som kommer inn i REST-API'et med noen unntak:
- "vedlegg" er byttet ut med "vedlegg_urls" som peker på vedleggene mellomlagret i [pleiepenger-dokument](https://github.com/navikt/pleiepenger-dokument)
- "soknad_id" lagt til
- Alle andre felter som har vært en del av JSON-meldingen som kom inn i REST-API'et vil også være en del av "data"-attributten i Kafka-meldingen.

```json
{
	"metadata:": {
		"version": 1,
		"correlation_id": "b3106960-0a85-4e02-9221-6ae057c8e93f",
		"request_id": "b3106960-0a85-4e02-9221-6ae05456asd841"
	},
	"data": {
		"soknad_id": "ff106960-0a85-4e02-9221-6ae057c8e93f",
		"soker": {
			"aktoer_id": "1234567",
			"fodselsnummer": "290990123456"
		},
		"vedlegg_urls": [
			"https://pleiepenger-dokument/v1/b3106960-0a85-4e02-9221-6ae05456asd888"
		]
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
