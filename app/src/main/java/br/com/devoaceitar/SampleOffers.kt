package br.com.devoaceitar

data class Scenario(val label: String, val offer: Offer)

val sampleOffers = listOf(
    Scenario("Corrida favorável", Offer("Corrida", 2640, TripMeasure(pickup = 1200, trip = 8100),
        TripMeasure(pickup = 240, trip = 1080), "Bairro Central", rating = 4.93)),
    Scenario("Zona evitada", Offer("Corrida", 3150, TripMeasure(total = 9000),
        TripMeasure(total = 1380), "Bairro Horizonte", rating = 4.91)),
    Scenario("Espera na coleta", Offer("Entrega", 1940, TripMeasure(total = 8000),
        TripMeasure(total = 1200), "Bairro Central", collectionWaitSeconds = 900)),
    Scenario("Dados incompletos", Offer("Corrida", 2200, TripMeasure(total = 7400),
        TripMeasure(total = 1180), null)),
    Scenario("Nota abaixo da meta", Offer("Corrida", 2800, TripMeasure(total = 8500),
        TripMeasure(total = 1320), "Bairro Central", rating = 4.51)),
    Scenario("Entrega · dois trechos", Offer("Entrega", 1201, TripMeasure(pickup = 4800, trip = 7600),
        TripMeasure(pickup = 720, trip = 1260), "Bairro Central", rating = 4.95)),
)
