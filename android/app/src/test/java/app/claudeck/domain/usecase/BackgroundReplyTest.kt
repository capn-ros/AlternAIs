package app.claudeck.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReplyTest {
    @Test fun `solo se programan las cuentas que siguen en descanso`() {
        val list = listOf(AlarmCandidate("a", "A", 1_000), AlarmCandidate("b", "B", 500), AlarmCandidate("c", "C", 2_000))
        assertEquals(listOf(AlarmEntry("a", "A", 1_000), AlarmEntry("c", "C", 2_000)), AlarmPlan.schedule(list, 600))
        assertTrue(AlarmPlan.schedule(list, 5_000).isEmpty())
    }

    @Test fun `una cuenta que termina justo ahora no se programa`() {
        assertTrue(AlarmPlan.schedule(listOf(AlarmCandidate("a", "A", 100)), 100).isEmpty())
    }

    @Test fun `la alarma decide segun mensaje automatico y app abierta`() {
        assertEquals(AlarmAction.NOTIFY_ONLY, AlarmDispatch.decide(false, true))
        assertEquals(AlarmAction.NOTIFY_ONLY, AlarmDispatch.decide(false, false))
        assertEquals(AlarmAction.SEND_IN_APP, AlarmDispatch.decide(true, true))
        assertEquals(AlarmAction.SEND_IN_BACKGROUND, AlarmDispatch.decide(true, false))
    }

    @Test fun `solo el servicio en segundo plano puede no arrancar`() {
        assertFalse(AlarmDispatch.sendStarted(AlarmAction.SEND_IN_BACKGROUND, false))
        assertTrue(AlarmDispatch.sendStarted(AlarmAction.SEND_IN_BACKGROUND, true))
        assertTrue(AlarmDispatch.sendStarted(AlarmAction.SEND_IN_APP, false))
        assertTrue(AlarmDispatch.sendStarted(AlarmAction.NOTIFY_ONLY, false))
    }

    @Test fun `la respuesta usa lo escrito y si no el mensaje guardado`() {
        assertEquals("hola", ReplyText.resolve("  hola ", "otro"))
        assertEquals("guardado", ReplyText.resolve(null, " guardado"))
        assertNull(ReplyText.resolve("   ", "otro"))
        assertNull(ReplyText.resolve(null, null))
    }

    @Test fun `los textos del aviso de fin de descanso`() {
        assertEquals("«Trabajo» ya puede usarse", LimitNotificationText.doneTitle("Trabajo"))
        assertEquals("Terminó el descanso. Enviando el mensaje automático…", LimitNotificationText.doneText(true, true))
        assertEquals("Terminó el descanso. Abrí la app para que se envíe el mensaje automático.", LimitNotificationText.doneText(true, false))
        assertEquals("Terminó el descanso por límite de uso. Podés responder desde acá.", LimitNotificationText.doneText(false, true))
        assertTrue(LimitNotificationText.offersReply(false))
        assertFalse(LimitNotificationText.offersReply(true))
    }

    @Test fun `los botones de mensajes guardados son dos y se acortan`() {
        val acts = LimitNotificationText.snippetActions(listOf("Seguí", "a".repeat(30), "tercero"))
        assertEquals(2, acts.size)
        assertEquals(SnippetAction(0, "Seguí", "Seguí"), acts[0])
        assertEquals("a".repeat(22) + "…", acts[1].label)
        assertEquals("a".repeat(30), acts[1].text)
    }

    @Test fun `un mensaje guardado vacio se salta y conserva el lugar del otro`() {
        val acts = LimitNotificationText.snippetActions(listOf("  ", "ok", "x"))
        assertEquals(listOf(SnippetAction(1, "ok", "ok")), acts)
    }

    @Test fun `los textos del resultado del envio`() {
        assertEquals("Mensaje enviado a «A»", LimitNotificationText.resultTitle("A", true))
        assertEquals("No se pudo enviar el mensaje a «A»", LimitNotificationText.resultTitle("A", false))
        assertEquals("«hola»", LimitNotificationText.resultText("hola", true))
        assertEquals("Copié el mensaje: abrí la cuenta y pegalo. «hola»", LimitNotificationText.resultText("hola", false))
        val long = "b".repeat(100)
        assertEquals("«" + "b".repeat(80) + "…»", LimitNotificationText.resultText(long, true))
        assertEquals("«hola»", LimitNotificationText.resultBigText("hola", true))
        assertEquals("Copié el mensaje al portapapeles: abrí la cuenta y pegalo.\n«hola»", LimitNotificationText.resultBigText("hola", false))
    }

    @Test fun `el envio reintenta hasta 22 veces y despues se rinde`() {
        assertEquals(SendStep.Sent, AutoSendPolicy.next(0, true))
        assertEquals(SendStep.RetryIn(2_000), AutoSendPolicy.next(0, false))
        assertEquals(SendStep.RetryIn(2_000), AutoSendPolicy.next(21, false))
        assertEquals(SendStep.GiveUp, AutoSendPolicy.next(22, false))
        assertEquals(SendStep.Sent, AutoSendPolicy.next(22, true))
    }
}
