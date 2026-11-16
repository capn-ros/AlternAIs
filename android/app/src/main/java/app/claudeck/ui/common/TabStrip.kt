package app.claudeck.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.claudeck.R

/** Una pestaña tal como se dibuja: su id y el texto de la etiqueta. */
data class TabUi(val id: Int, val title: String)

/**
 * Fila de pestañas del navegador y del explorador: una píldora por pestaña (la activa con el color de la
 * cuenta), una × para cerrarla (solo si hay más de una) y un «+» para abrir otra.
 */
@Composable
fun TabStrip(
    tabs: List<TabUi>, activeId: Int, fg: Color, accent: Color,
    onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onNew: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (t in tabs) {
            val on = t.id == activeId
            Row(
                Modifier
                    .widthIn(max = 170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) accent.copy(alpha = 0.18f) else fg.copy(alpha = 0.06f))
                    .border(BorderStroke(if (on) 1.5.dp else 1.dp, if (on) accent else fg.copy(alpha = 0.20f)), RoundedCornerShape(16.dp))
                    .clickable { onSelect(t.id) }
                    .padding(start = 12.dp, end = if (tabs.size > 1) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t.title, color = fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp)
                )
                if (tabs.size > 1) {
                    Icon(
                        painterResource(R.drawable.ic_close), contentDescription = "Cerrar pestaña «${t.title}»", tint = accent,
                        modifier = Modifier.size(28.dp).clickable { onClose(t.id) }.padding(7.dp)
                    )
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_add), contentDescription = "Pestaña nueva", tint = accent,
            modifier = Modifier.size(34.dp).clickable(onClick = onNew).padding(7.dp)
        )
    }
}
