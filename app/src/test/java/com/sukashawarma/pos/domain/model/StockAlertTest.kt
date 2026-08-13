package com.sukashawarma.pos.domain.model

import com.sukashawarma.pos.data.remote.dto.MonitoringViewCrewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockAlertTest {

    private fun row(
        id: String? = "b1",
        name: String? = "Daging Sapi",
        status: String? = "below",
        projection: String? = null
    ) = MonitoringViewCrewDto(
        bahanBakuId = id,
        itemName = name,
        satuan = "kg",
        kategori = "Protein",
        currentQty = 1.5,
        threshold = 10.0,
        status = status,
        projectionText = projection
    )

    @Test
    fun `status ok tidak pernah jadi peringatan`() {
        assertTrue(listOf(row(status = "ok")).toStockAlerts().isEmpty())
    }

    @Test
    fun `below dan warning keduanya ikut, status dipertahankan`() {
        val alerts = listOf(
            row(id = "b1", name = "Sapi", status = "below"),
            row(id = "b2", name = "Ayam", status = "warning")
        ).toStockAlerts()

        assertEquals(2, alerts.size)
        assertEquals(StockAlertStatus.BELOW, alerts[0].status)
        assertEquals(StockAlertStatus.WARNING, alerts[1].status)
    }

    @Test
    fun `baris ganda per bahan hanya dihitung sekali`() {
        val alerts = listOf(row(id = "b1"), row(id = "b1")).toStockAlerts()
        assertEquals(1, alerts.size)
    }

    @Test
    fun `baris tanpa nama dibuang, bukan tampil kosong di marquee`() {
        assertTrue(listOf(row(name = null), row(name = "  ")).toStockAlerts().isEmpty())
    }

    @Test
    fun `nama ditampilkan kapital`() {
        assertEquals("DAGING SAPI", listOf(row(name = "Daging Sapi")).toStockAlerts().first().name)
    }

    @Test
    fun `proyeksi porsi ikut ditampilkan bila ada`() {
        val alert = listOf(row(projection = "Shawarma Ayam (3 porsi)")).toStockAlerts().first()
        assertEquals("DAGING SAPI (sisa 3 porsi)", alert.marqueeText)
    }

    @Test
    fun `banyak resep dipadatkan jadi porsi terkecil`() {
        // Bentuk asli dari monitoring_view_crew untuk bahan yang dipakai banyak resep.
        val alert = listOf(
            row(projection = "Shawarma Sedang (7 porsi) atau Shawarma Jumbo (2 porsi) atau Shawarmie (9 porsi)")
        ).toStockAlerts().first()

        assertEquals(2, alert.minPorsi)
        assertEquals("DAGING SAPI (sisa 2 porsi)", alert.marqueeText)
    }

    @Test
    fun `tanpa proyeksi cukup namanya saja`() {
        val alert = listOf(row(projection = null)).toStockAlerts().first()
        assertEquals("DAGING SAPI", alert.marqueeText)
    }

    @Test
    fun `proyeksi tanpa angka porsi tidak bikin teks aneh`() {
        val alert = listOf(row(projection = "Shawarma Ayam")).toStockAlerts().first()
        assertEquals("DAGING SAPI", alert.marqueeText)
    }

    @Test
    fun `bahan tanpa id tetap masuk, dibedakan lewat namanya`() {
        val alerts = listOf(row(id = null, name = "Sapi"), row(id = null, name = "Ayam")).toStockAlerts()
        assertEquals(2, alerts.size)
    }
}
