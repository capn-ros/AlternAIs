# AlternAIs

Tu mazo de cuentas de claude.ai y replit.com: varias abiertas a la vez, cada una con su **sesión totalmente aislada**, y cambio entre ellas con un toque. Para Android y Windows, compiladas en GitHub.

No es una app oficial de Anthropic: es un contenedor que abre `claude.ai` y guarda las cookies de cada cuenta por separado. No usa ninguna API ni toca tus datos.

## Compilar en GitHub con cd.zip

1. Una sola vez: en tu repo creá el archivo `.github/workflows/cd.yml` (viene aparte en la entrega).
2. Cada vez que quieras compilar: subí `cd.zip` a la raíz del repo (reemplaza al anterior).
3. El workflow **Compilar desde cd.zip** borra el contenido del repo, descomprime el zip y compila Android y Windows. No publica ni guarda nada en el repo.
4. Cuando termine, bajá de **Artifacts**:
   - `Claudeck-Android` → `Claudeck-N.apk`
   - `Claudeck-Windows` → `AlternAIs-Setup-1.0.N.exe` (instalador)

`cd.zip` debe tener en su raíz lo mismo que la raíz del repo (`android/`, `windows-native/`, `.github/`), sin carpeta intermedia (si trae una sola carpeta, también la entiende).

También funcionan los workflows normales: al hacer push de cambios en `android/` se compila esa parte, y desde **Actions → Run workflow** podés lanzarlos a mano.

El APK usa siempre la misma firma (incluida en `android/app/debug.keystore`), así que cada versión nueva se instala encima de la anterior sin perder las sesiones.

## Qué hace

- **Dos apps en una: Claude y Replit.** En el cajón de **Ajustes**, arriba, están los nombres **Claude** y **Replit** con el logo de la app en el medio. Tocá uno para pasar a esa mitad: funciona igual, pero cada una tiene **sus propias cuentas** (y su cuenta activa). **Claude se ve oscuro y Replit claro**, con la barra del color de fondo de Replit; en las dos, el borde y los íconos toman el color de la cuenta activa. Tocá **Claude** para volver.
- **Una cuenta = una sesión aislada.** Android usa perfiles de WebView; Windows usa una partición persistente por cuenta. Las sesiones quedan guardadas al cerrar la app y, al volver a abrirla, cada cuenta retoma su última pantalla de Claude.
- **Nombre, propósito y color** por cuenta (ej. "Trabajo · Código y revisiones").
- **Descanso por límite de uso:** cuando una cuenta llega a su límite, la marcás (en 5 h, 24 h, 7 días o a una hora exacta; en Replit, además, el día de renovación mensual, y las opciones siguen sus cuotas: Modo gratis cada 5 h con tope semanal, créditos diarios y créditos mensuales). Se ve en descanso con la cuenta regresiva y la app te avisa cuando pasa.
- **Exportar e importar cuentas** (Ajustes): guardás un archivo con tus cuentas (nombre, propósito, color y límites) y lo importás en otro dispositivo, Windows ↔ Android. **No copia las sesiones**: en el dispositivo nuevo hay que iniciar sesión en cada cuenta.
- **Cerrar sesión y borrar datos** o **eliminar** una cuenta, solo en el dispositivo.
- **Tutorial de bienvenida** en el primer inicio, con opción para saltearlo. Recomienda iniciar sesión en Claude con el correo electrónico; «Continuar con Google» puede fallar en navegadores integrados.
- En **Ajustes → Acerca de AlternAIs** encontrás la información de la app. Pensada y diseñada por **Andrés Londero**.

### Android
- Panel de cuentas abajo. Tocá una cuenta para cambiar, mantené apretado para editar, marcar límite, recargar, cerrar sesión o eliminar. Deslizá sobre la línea con el nombre para pasar a la cuenta siguiente/anterior; tocá esa línea para plegar el panel; el botón ⇄ vuelve a la cuenta anterior.
- **Burbuja flotante**: en ⋮ → «Burbuja flotante». Aparece sobre cualquier app; arrastrala, tocala y elegí una cuenta para abrir AlternAIs directo en ella. Pide el permiso «Mostrar sobre otras apps».
- **Descargas**: los archivos que genera Claude se guardan en la carpeta Descargas (aviso con botón «Abrir»). En Android 9 o menor pide permiso de almacenamiento.
- Mantené apretado el ícono de la app para abrir directo en una cuenta.
- Requiere Android 8.0+ y un Android System WebView actualizado (Play Store).

### Windows
- Barra de cuentas (a la izquierda, a la derecha o abajo: se elige en Ajustes), clic derecho para el menú de cada una. Ajustes también tiene **Iniciar con Windows** y qué hacer al cerrar: cerrar la app o dejarla en la **bandeja del sistema**.
- Atajos: `Ctrl+1…9` cambia de cuenta, `Ctrl+Tab` pasa a la siguiente, `Ctrl+N` agrega, `Ctrl+R` recarga.
- Ventana sin marco estándar de Windows, con barra de título y controles propios. El instalador de Inno Setup también incluye gráficos de marca a juego.

### Windows (versión nativa)
Hay una versión nueva en `windows-native/` (WinUI 3 + .NET 9 + WebView2, que reemplazó a la versión Electron, ya retirada). Con el `cd.yml` nuevo (copia en `docs/cd.yml`, pegarlo a mano en `.github/workflows/`) se compila sola al subir `cd.zip`: artifact **`AlternAIs-Windows`** (instalador) y `Tests-Windows` (reporte de tests).

Lee el mismo `accounts.json`, así que conserva tus cuentas, pero **hay que volver a iniciar sesión en cada una** (las sesiones de Electron no se migran). Necesita *Microsoft Edge WebView2 Runtime* (viene con Windows 10/11 actualizados).

Qué tiene (igual que la versión de siempre):
- Animación del logo al abrir y logo en bucle mientras carga una cuenta.
- Barra de título propia con la **barra de tiempo** de la cuenta activa («Trabajo · faltan 2 h 10 min»).
- Barra de cuentas a la izquierda, derecha o abajo; Claude y Replit con sus cuentas y su tema (tocá el logo de Ajustes para alternar oscuro/claro).
- **Explorador de archivos** (Ctrl+E): lugares rápidos, abrir/ejecutar, copiar, mover, renombrar, borrar a la Papelera, carpeta nueva y extraer .zip. Clic derecho = opciones; Supr, F2, Enter, Retroceso, Ctrl+C/X/V, Ctrl+Mayús+N.
- Mensajes guardados (Ctrl+M, con «Texto propio…»), navegador interno (Ctrl+B) y ajustes (Ctrl+,).
- **Bandeja del sistema**: en Ajustes → «Al cerrar la ventana» → «Seguir en la bandeja». Clic en el ícono abre; clic derecho muestra las cuentas con su cuenta regresiva y «Salir».
- Aviso de fin de descanso con caja para **responder desde el aviso** y los dos primeros mensajes guardados como botones; respuesta automática al marcar el límite (mensaje guardado, texto propio o ninguna).
- Atajos que funcionan aunque estés escribiendo en la página: Ctrl+Tab / Ctrl+Mayús+Tab, Ctrl+1…9, Ctrl+N, Ctrl+B, Ctrl+M, Ctrl+E, Ctrl+,.
- Micrófono para el dictado por voz (solo para claude.ai / replit.com); la cámara no.
- Tutorial la primera vez (se puede volver a ver desde Ajustes) y una sola AlternAIs abierta a la vez.

## Limitaciones conocidas

- **"Continuar con Google"** puede no funcionar dentro de un navegador embebido (Google lo bloquea a veces). Si falla, entrá con tu email (te llega un código).
- En Android el **micrófono** (dictado por voz de las IA) funciona: la primera vez Android pide el permiso. La cámara no está soportada.
- El aviso de fin de descanso es una notificación del sistema en ambas plataformas (en Windows, con la app abierta o en la bandeja).

## Uso responsable

Las cuentas son para usos distintos y respetan sus propios límites. Usar varias cuentas para esquivar los límites de una sola va contra los términos de uso de Anthropic.
