package app.claudeck.ui.accountmenu

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.claudeck.R
import app.claudeck.domain.usecase.AccountMenuAction
import app.claudeck.ui.theme.AlternTheme

/** Lo que el menú le pide a la pantalla principal. El menú ya se cerró cuando se llama a [run]. */
interface AccountMenuActions {
    fun run(accountId: String, action: AccountMenuAction)

    /** Colores actuales (cambian con el servicio y el tema). */
    fun surface(): Int
    fun onSurface(): Int
}

/** Vista sin tamaño propio que aloja la hoja inferior (que vive en su propia ventana). */
fun createAccountMenuHost(context: Context, vm: AccountMenuViewModel, actions: AccountMenuActions): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val menu by vm.menu.collectAsStateWithLifecycle()
            val current = menu
            if (current != null) {
                val surface = actions.surface()
                AlternTheme(dark = ColorUtils.calculateLuminance(surface) < 0.5) {
                    AccountMenuSheet(
                        state = current, surface = Color(surface), fg = Color(actions.onSurface()),
                        onDismiss = vm::dismiss,
                        onPick = { action -> vm.dismiss(); actions.run(current.accountId, action) }
                    )
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountMenuSheet(
    state: AccountMenuState, surface: Color, fg: Color, onDismiss: () -> Unit, onPick: (AccountMenuAction) -> Unit
) {
    val tint = Color(state.color)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = surface, contentColor = fg
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 12.dp).size(14.dp).clip(CircleShape).background(tint))
                Column {
                    Text(state.model.title, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (state.model.subtitle.isNotBlank()) {
                        Text(state.model.subtitle, color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
            state.model.items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(item.action) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(iconFor(item.action)), null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(item.label, color = fg, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@DrawableRes
private fun iconFor(action: AccountMenuAction): Int = when (action) {
    AccountMenuAction.EDIT -> R.drawable.ic_edit
    AccountMenuAction.RELOAD -> R.drawable.ic_reload
    AccountMenuAction.MARK_LIMIT -> R.drawable.ic_clock
    AccountMenuAction.CLEAR_LIMIT -> R.drawable.ic_check
    AccountMenuAction.SIGN_OUT -> R.drawable.ic_logout
    AccountMenuAction.DELETE -> R.drawable.ic_trash
}
