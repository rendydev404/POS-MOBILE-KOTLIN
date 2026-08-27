package com.sukashawarma.pos.domain.model

/**
 * One row in the order-manual cart builder — port of the web's `Line` type
 * (order-manual/page.tsx:27-34). Distinct from [OrderItem] (the flat shape actually
 * persisted): a [CartLine] can be a parent (a menu item the cashier tapped) or a
 * child (an extra/upsell added alongside a parent, linked via [parentId] purely for
 * in-app grouping/receipt display — the web keeps this relationship client-side only
 * too, see order-manual analysis §5/§7).
 */
data class CartLine(
    val cartItemId: String = java.util.UUID.randomUUID().toString(),
    val menuItemId: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val note: String = "",
    val parentId: String? = null,
    val packageChoices: Map<String, String> = emptyMap(),
    /** Baris hadiah sistem; kasir tidak boleh mengubah quantity atau harganya. */
    val isPromoReward: Boolean = false,
    val promoId: String? = null,
    val promoName: String? = null,
    val promoBuyQuantity: Int? = null,
    val promoGetQuantity: Int? = null,
    val sourceCartItemId: String? = null
) {
    val subtotal: Double get() = unitPrice * quantity
}
