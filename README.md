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

`cd.zip` debe tener en su raíz lo mismo que la raíz del repo (`android/`, `windows/`, `.github/`), sin carpeta intermedia (si trae una sola carpeta, también la entiende).

También funcionan los workflows normales: al hacer push de cambios en `android/` o `windows/` se compila esa parte, y desde **Actions → Run workflow** podés lanzarlos a mano.

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
- Para probarla en tu PC: `cd windows && npm install && npm start`.

## Limitaciones conocidas

- **"Continuar con Google"** puede no funcionar dentro de un navegador embebido (Google lo bloquea a veces). Si falla, entrá con tu email (te llega un código).
- El micrófono/cámara de la app web no están soportados en Android por ahora.
- El aviso de fin de descanso en Android aparece cuando abrís la app; en Windows es una notificación del sistema.

## Uso responsable

Las cuentas son para usos distintos y respetan sus propios límites. Usar varias cuentas para esquivar los límites de una sola va contra los términos de uso de Anthropic.
