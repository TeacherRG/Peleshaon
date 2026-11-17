package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.findCities
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UkTest {
    @Test
    fun ukrainianCityNames() {
        val uk = Locale("uk")
        assertEquals("Київ", cityById("Europe/Kiev")?.title(uk) ?: cityById("Europe/Kyiv")!!.title(uk))
        assertEquals("Відень", cityById("Europe/Vienna")!!.title(uk))
        assertEquals("Вена", cityById("Europe/Vienna")!!.title(Locale("ru")))
        assertTrue(findCities("Відень").any { it.id == "Europe/Vienna" })
        assertTrue(findCities("Харків").isNotEmpty())
        assertTrue(findCities("Ві").any { it.id == "Europe/Vienna" })
        assertTrue(findCities("Ірк").any { it.id == "Asia/Irkutsk" })
    }
}
