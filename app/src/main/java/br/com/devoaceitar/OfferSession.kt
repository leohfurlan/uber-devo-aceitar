package br.com.devoaceitar

data class ActiveOffer(val id: Long, val offer: Offer, val expiresAtMs: Long)

class OfferSession {
    private var sequence = 0L
    private var active: ActiveOffer? = null
    private var dismissed: Long? = null

    fun start(offer: Offer, nowMs: Long, durationMs: Long): ActiveOffer {
        require(durationMs > 0)
        return ActiveOffer(++sequence, offer, nowMs + durationMs).also {
            active = it
            dismissed = null
        }
    }

    fun current(nowMs: Long): ActiveOffer? {
        val offer = active ?: return null
        if (nowMs >= offer.expiresAtMs) {
            end(offer.id)
            return null
        }
        return offer
    }

    fun showAssessment(id: Long): Boolean = active?.id == id && dismissed != id

    fun dismiss(id: Long) {
        if (active?.id == id) dismissed = id
    }

    fun end(id: Long) {
        if (active?.id == id) {
            active = null
            dismissed = null
        }
    }
}
