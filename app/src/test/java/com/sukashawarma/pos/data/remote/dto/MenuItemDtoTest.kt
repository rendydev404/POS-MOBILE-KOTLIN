package com.sukashawarma.pos.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuItemDtoTest {

    private val gson = Gson()

    @Test
    fun `deserializes full menu item payload including package and channel fields`() {
        val json = """
            {
              "id": "item-1",
              "category_id": "cat-1",
              "outlet_id": "outlet-a",
              "name": "Shawarma Ayam",
              "description": null,
              "price": 25000.0,
              "strike_price": 30000.0,
              "channel_prices": {"gofood": 27000.0, "grabfood": 28000.0},
              "image_url": null,
              "is_available": true,
              "is_available_online": false,
              "available_online_channels": ["gofood"],
              "sort_order": 1,
              "prep_time": 12,
              "is_package": true,
              "package_items": [
                {"id": "pk-1", "menu_item_id": "item-2", "or_menu_item_id": null, "quantity": 2}
              ],
              "categories": {"id": "cat-1", "name": "Menu Utama", "sort_order": 1}
            }
        """.trimIndent()

        val dto = gson.fromJson(json, MenuItemDto::class.java)

        assertEquals("outlet-a", dto.outletId)
        assertEquals(30000.0, dto.strikePrice)
        assertEquals(27000.0, dto.channelPrices?.get("gofood"))
        assertEquals(false, dto.isAvailableOnline)
        assertEquals(listOf("gofood"), dto.availableOnlineChannels)
        assertEquals(true, dto.isPackage)
        assertEquals(1, dto.packageItems?.size)
        assertEquals("item-2", dto.packageItems?.first()?.menuItemId)
        assertEquals("Menu Utama", dto.categories?.name)
    }

    @Test
    fun `missing optional fields deserialize to null rather than throwing`() {
        val json = """{"id":"item-1","category_id":"cat-1","name":"Item","description":null,"price":1000.0,"image_url":null}"""
        val dto = gson.fromJson(json, MenuItemDto::class.java)
        assertNull(dto.outletId)
        assertNull(dto.channelPrices)
        assertNull(dto.packageItems)
    }
}
