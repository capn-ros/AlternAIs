package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRulesTest {
    private val enc: (String) -> String = { it.replace(" ", "%20") }
    private fun r(s: String) = BrowserRules.resolve(s, enc)

    @Test fun `vacio no hace nada`() {
        assertEquals(BrowserInput.Ignore, r(""))
        assertEquals(BrowserInput.Ignore, r("   "))
    }

    @Test fun `http y https se cargan tal cual aunque esten en mayusculas`() {
        assertEquals(BrowserInput.Load("https://a.com/x"), r(" https://a.com/x "))
        assertEquals(BrowserInput.Load("HTTP://A.COM"), r("HTTP://A.COM"))
    }

    @Test fun `otros esquemas se rechazan con aviso`() {
        assertEquals(BrowserInput.Reject(BrowserRules.ONLY_HTTP_MESSAGE), r("file:///sdcard/x"))
        assertEquals(BrowserInput.Reject("Solo se pueden abrir direcciones http y https"), r("ftp://a.com"))
    }

    @Test fun `un dominio sin esquema pasa a https`() {
        assertEquals(BrowserInput.Load("https://github.com/capn-ros"), r("github.com/capn-ros"))
    }

    @Test fun `texto suelto o con espacios es una busqueda`() {
        assertEquals(BrowserInput.Load("https://duckduckgo.com/?q=kotlin"), r("kotlin"))
        assertEquals(BrowserInput.Load("https://duckduckgo.com/?q=hola%20mundo.com"), r("hola mundo.com"))
    }

    @Test fun `enlaces que abre la pagina`() {
        assertEquals(LinkAction.LOAD_HERE, BrowserRules.linkAction("https"))
        assertEquals(LinkAction.LOAD_HERE, BrowserRules.linkAction("HTTP"))
        assertEquals(LinkAction.LOAD_HERE, BrowserRules.linkAction(null))
        assertEquals(LinkAction.OPEN_EXTERNAL, BrowserRules.linkAction("mailto"))
        assertEquals(LinkAction.OPEN_EXTERNAL, BrowserRules.linkAction("TEL"))
        assertEquals(LinkAction.BLOCK, BrowserRules.linkAction("intent"))
        assertEquals(LinkAction.BLOCK, BrowserRules.linkAction("market"))
    }

    @Test fun `solo se recuerdan paginas web`() {
        assertTrue(BrowserRules.isRememberable("https://a.com"))
        assertTrue(BrowserRules.isRememberable("http://a.com"))
        assertFalse(BrowserRules.isRememberable("about:blank"))
        assertFalse(BrowserRules.isRememberable(null))
    }

    @Test fun `la primera vez abre la ultima pagina o GitHub`() {
        assertEquals("https://x.com/", BrowserRules.startUrl("https://x.com/"))
        assertEquals("https://github.com/", BrowserRules.startUrl(null))
        assertEquals(4, BrowserRules.QUICK_LINKS.size)
    }

    @Test fun `el user agent pierde el marcador de webview`() {
        assertEquals("Mozilla/5.0 (Linux; Android 14) Chrome/120", BrowserRules.cleanUserAgent("Mozilla/5.0 (Linux; Android 14; wv) Chrome/120"))
        assertEquals("a b", BrowserRules.cleanUserAgent("a b"))
        assertEquals("x)", BrowserRules.cleanUserAgent("x; wv)"))
    }

    @Test fun `atras`() {
        assertEquals(BrowserBack.LEAVE_FIELD, BrowserRules.back(true, true))
        assertEquals(BrowserBack.GO_BACK, BrowserRules.back(false, true))
        assertEquals(BrowserBack.CLOSE, BrowserRules.back(false, false))
    }

    @Test fun `botones`() {
        assertEquals("Detener", BrowserRules.reloadDescription(true))
        assertEquals("Recargar", BrowserRules.reloadDescription(false))
        assertEquals(1f, BrowserRules.buttonAlpha(true), 0f)
        assertEquals(0.3f, BrowserRules.buttonAlpha(false), 0f)
    }
}
