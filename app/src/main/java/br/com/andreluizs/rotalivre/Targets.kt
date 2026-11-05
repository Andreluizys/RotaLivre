package br.com.andreluizs.rotalivre

/**
 * Pacotes dos apps lidos pelo Rota Livre.
 * Ao incluir um app novo, repita o pacote em accessibility_config.xml (packageNames).
 */
object Targets {
    val RIDE = setOf("com.ubercab.driver", "com.app99.driver")

    val DELIVERY = setOf(
        "br.com.ifood.driver.app",        // iFood Entregador (não confirmado)
        "com.loggi.driverapp",            // Loggi
        "com.shopee.spx.driver.brazil"    // Shopee Entregador
    )

    val ALL = RIDE + DELIVERY
    fun isDelivery(pkg: String) = pkg in DELIVERY
}
