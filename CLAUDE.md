# CLAUDE.md — Claudeck (documentación completa del proyecto)

> Leé este archivo primero. Cubre arquitectura, funciones, convenciones, build y trampas conocidas, para no tener que re-explorar el código ni leer todo el zip. Si cambiás algo estructural, **actualizá este archivo**.

## 1. Qué es

Claudeck es un contenedor de cuentas de **claude.ai**: varias cuentas abiertas a la vez, cada una con **sesión aislada** (cookies/storage propios), y cambio entre ellas con un toque/clic. No es oficial de Anthropic, no usa ninguna API: solo abre `https://claude.ai/` en un WebView por cuenta.

Dos apps independientes, sin código compartido (se duplica la lógica a propósito):

| Plataforma | Stack | Carpeta | Aislamiento de sesión |
|---|---|---|---|
| Android | Kotlin, vistas XML + Material 3, WebView | `android/` | **Perfiles de WebView** (`androidx.webkit` `ProfileStore`, nombre `acc_<id>`) |
| Windows | Electron 33 (JS plano, sin framework ni bundler) | `windows/` | **Partición persistente** `persist:acc_<id>` |

Idioma de UI y mensajes: **español rioplatense** (voseo: "Agregá", "Poné", "usás"). Mantenerlo.

## 2. Estructura del repo

```
.github/workflows/
  cd.yml          # compila Android+Windows desde cd.zip (flujo principal del dueño)
  android.yml     # push a android/** o manual
  windows.yml     # push a windows/** o manual
scripts/make_icon.py        # genera windows/build/icon.png + icon.ico (Pillow)
android/
  build.gradle.kts, settings.gradle.kts, gradle.properties
  app/build.gradle.kts
  app/debug.keystore        # firma fija (¡no tocar!, ver §6)
  app/src/main/AndroidManifest.xml
  app/src/main/java/app/claudeck/
    MainActivity.kt         # UI + lógica: cuentas, WebViews, dock, menús, descargas, atajos (~900 líneas)
    BubbleService.kt        # burbuja flotante (overlay + servicio en primer plano)
    Account.kt              # data class Account + AccountStore (SharedPreferences, también ajustes de UI)
    Ui.kt                   # dibuja el avatar circular (color + letra) para burbuja y atajos
  app/src/main/res/         # layout/activity_main.xml, dialog_account.xml, values/, drawable/ (íconos vectoriales, bg_dock), mipmap-anydpi-v26/
windows/
  main.js                   # proceso principal: estado, vistas, IPC, atajos, notificaciones
  preload.js                # contextBridge → window.api
  renderer/index.html, app.js, styles.css   # UI de la barra lateral/diálogos
  build/icon.png, icon.ico
  package.json              # config electron-builder (nsis + portable)
README.md                   # documentación para el usuario final
```

No hay tests, linter ni gradle wrapper (`gradlew`). Las compilaciones se hacen **solo en GitHub Actions**.

## 3. Modelo de datos (igual en ambas plataformas)

```
Account { id, name, purpose, color, limitUntil }
```
- `id`: hex aleatorio (Android: UUID sin guiones, primeros 12; Windows: `crypto.randomBytes(6).toString('hex')`). Se usa para nombrar el perfil/partición → **nunca cambiar un id existente** (se perdería la sesión).
- `name` máx 24 chars, `purpose` máx 60, `color` `#RRGGBB` (Android lo guarda como Int ARGB).
- `limitUntil`: epoch ms hasta el que la cuenta está "en descanso" por límite de uso; `0` = disponible.
- Paleta de 8 colores duplicada en `MainActivity.kt` (`PALETTE`), `renderer/app.js` (`PALETTE`) y validada en `main.js` (`cleanColor`, default `#E07A5F`).
- Persistencia: Android → `SharedPreferences("claudeck")` claves `accounts` (JSON array) y `selected`. Windows → `app.getPath('userData')/accounts.json` (`{accounts, selectedId}`, escritura atómica con `.tmp` + rename).

Funcionalidades (paridad entre plataformas): agregar/editar cuenta, cambiar cuenta, recargar, marcar límite (5 h / 24 h / 7 d / hora exacta, si la hora ya pasó → mañana), quitar marca, cerrar sesión y borrar datos, eliminar cuenta, aviso cuando termina el descanso.

## 4. Android — cómo funciona

**Stack:** `minSdk 26`, `targetSdk/compileSdk 34`, Kotlin 1.9.24, AGP 8.5.2, Java 17. Deps: core-ktx, appcompat, material 1.12, `androidx.webkit` 1.11 (sin dependencias nuevas en la v. de la burbuja). Tema `Theme.Material3.DayNight.NoActionBar`, color de marca `#D2694A`. Todo el texto hardcodeado en español rioplatense.

### 4.1 Pantalla principal (`MainActivity` + `activity_main.xml`)
- `launchMode="singleTask"`: la burbuja y los atajos del launcher reutilizan la Activity y le llegan por `onNewIntent` con el extra `EXTRA_SELECT` (id de cuenta) → `select(id)`. También se lee en `onCreate` (si el proceso estaba muerto).
- Layout: barra de progreso arriba (se tiñe con el color de la cuenta), `FrameLayout#container` (WebViews + `emptyView` + `errorView`), y abajo el **dock**. El dock del XML se **reemplaza en código** (`buildDock()`, al inicio de `onCreate`, conservando los ids `dock/infoRow/infoDot/info/btnMore/btnToggleDock/chipScroll/chipGroup` y los `LayoutParams` originales), así que su contenido ya no depende de `activity_main.xml`.
- **Un `WebView` por cuenta**, creado perezosamente en `select()` (`webViews.getOrPut`). Nace `GONE`; `transitionTo()` lo muestra con fundido + deslizamiento de 28 dp según el sentido del cambio (el anterior se desvanece y pasa a `GONE`). No se destruyen al cambiar. Cada uno hace `ProfileStore...getOrCreateProfile("acc_<id>")` + `WebViewCompat.setProfile` **antes** de cargar nada.
- `multiProfileOk` (feature `MULTI_PROFILE`): si es `false`, aviso y una sola cuenta.
- **Círculos de cuenta** (antes chips; el mapa sigue llamándose `chips`): `ImageView` de 40 dp con `Ui.avatar` (color + inicial; aro del color de texto si es la activa; punto ámbar si está en descanso). Tap = `select`, long-press = menú de cuenta. Se redibujan en `refreshCircles()` (la llaman `updateSelection`, `refreshChipTexts`, `rebuildChips`). Al final, botón **+** redondo con degradé sweep de `PALETTE`. Entran con animación escalonada.
- **Dock**: dos estados (`setDockCollapsed`, tocar el `infoRow` o la flecha alterna). *Plegado*: punto de la cuenta a la izquierda y flecha (del color de la cuenta) a la derecha. *Desplegado*: fila con punto + `Nombre · propósito · estado` + botón Ajustes + flecha, y debajo los círculos. **Tocar fuera de la barra la pliega** (`dispatchTouchEvent` de la Activity). **Ajustes**: el botón abre un **cajón** (`buildDrawer`/`setDrawerOpen`, hijo de `container`, sale desde detrás del dock) con el ícono y nombre de la app, «Ajustes» y: Burbuja de cuentas (interruptor), Recargar cuenta actual, Ver descargas, Permisos de la app. Íconos vectoriales propios `ic_*` (trazo redondeado, tinte `R.color.brand`) también en el menú de cuenta; ya no hay «volver a la cuenta anterior». **Se pliega solo** a los `AUTO_HIDE_MS` (4 s) sin tocarlo (`scheduleAutoHide`, reiniciado por `dispatchTouchEvent` del dock, `select` y `onResume`; no se pliega sin cuentas ni con el teclado abierto). Arranca siempre desplegado. **Borde superior del color de la cuenta activa** (`dockBg` + `setBorderColor`, animado); el fondo es un `LayerDrawable` con inset negativo abajo y el dock recorta con `clipToOutline` (esquinas superiores de 22 dp). **Deslizar** sobre el `infoRow` pasa a la cuenta siguiente/anterior (`step`). Se quitó el botón swap (`swapToPrevious` queda sin uso). Mientras el teclado está abierto el dock se oculta (`watchKeyboard`).
- **Menús**: `showAccountMenu(acc)` y `showMoreMenu()` son `BottomSheetDialog` armados en código (`sheetColumn/sheetHeader/sheetRow`, íconos emoji). "Más" tiene: interruptor de burbuja, cuenta anterior, recargar, ver descargas, permisos de la app (ajustes del sistema).
- Aviso inteligente: al seleccionar o marcar en descanso una cuenta con límite, `Snackbar` con acción "Ir a «otra cuenta disponible»". Al crear la 2ª cuenta se sugiere una vez la burbuja (`bubbleTipShown`).
- **Pantalla de error**: `onReceivedError` (solo main frame) agrega el id a `failed` y muestra `errorView` con "Reintentar" (`retryCurrent`). Se limpia en `onPageStarted`.
- **Atajos del launcher**: `updateShortcuts()` publica hasta 4 atajos dinámicos (uno por cuenta, ícono = `Ui.avatar`) con `ShortcutManagerCompat`; se llama cada vez que cambian las cuentas (`onAccountsChanged`).
- `ticker` (cada 30 s, solo entre `onResume`/`onPause`) → `tick()`: limpia límites vencidos, `Toast`, refresca chips/info/burbuja. **El aviso de fin de descanso sigue apareciendo solo con la app abierta.**
- Botón atrás: `canGoBack()` del WebView actual; si no, sale. `onPause/onResume` pausan/reanudan **todos** los WebViews.
- `shouldOverrideUrlLoading`: esquemas distintos de http/https → `ACTION_VIEW` externo. Sin filtro de hosts (todo http(s) navega en el WebView).
- Subida de archivos: `onShowFileChooser` + `ActivityResultContracts.StartActivityForResult` (no requiere permisos).
- Borrado (`wipeAccount`): destruye el WebView, `ProfileStore.deleteProfile`; fallback a `removeAllCookies` + `deleteAllData`.

### 4.2 Descargas (arreglado en esta versión)
Antes `setDownloadListener` hacía `ACTION_VIEW` con la URL (el navegador externo no tiene las cookies de la cuenta → fallaba; `blob:` imposible). Ahora `handleDownload()` decide por esquema, todo envuelto en `withStoragePermission` (solo pide `WRITE_EXTERNAL_STORAGE` en Android ≤ 9; en 10+ no hace falta):
- **http/https** → `enqueueDownload`: `DownloadManager` con cabeceras `Cookie` (del `cookieManager` del **perfil** de la cuenta) y `User-Agent`, destino `Descargas` público, notificación del sistema. `downloadReceiver` (registrado con `RECEIVER_EXPORTED`, vive con la Activity) muestra Snackbar "Descargado" con acción **Abrir**.
- **blob:** → `fetchBlob`: ejecuta JS en la página que lee el blob y lo manda por el objeto **`claudeckDl`** (`WebViewCompat.addWebMessageListener`, limitado a los orígenes `claude.ai`/`*.claude.ai`/`claude.com`/`*.claude.com`, requiere feature `WEB_MESSAGE_LISTENER`). `HOOK_JS` (inyectado en `onPageStarted` y `onPageFinished`, idempotente) guarda los blobs creados con `URL.createObjectURL` y demora 60 s el `revokeObjectURL` (la página suele revocarlo enseguida), y recuerda el `download=` del `<a>` clickeado para el nombre del archivo. El mensaje JSON `{name, mime, data(base64)}` llega a `onBridgeMessage` → hilo aparte → `saveToDownloads` (MediaStore.Downloads en 10+, archivo directo en ≤ 9) → Snackbar con **Abrir**.
- **data:** → `saveDataUrl`.
- Sigue sin soportarse micrófono/cámara (falta `onPermissionRequest`).

### 4.3 Burbuja flotante (`BubbleService`)
- Overlay `TYPE_APPLICATION_OVERLAY` dentro de un **servicio en primer plano** (`foregroundServiceType="specialUse"`, `START_NOT_STICKY` a propósito: Android 12+ no deja reiniciar FGS desde segundo plano). Permisos del manifest: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
- Activación: `MainActivity.setBubbleEnabled(true)` → si falta "Mostrar sobre otras apps" abre `ACTION_MANAGE_OVERLAY_PERMISSION` (`pendingBubble`, se reintenta en `onResume`) → pide notificaciones (13+; si las niegan igual funciona) → `BubbleService.start`. Estado en `AccountStore.bubbleEnabled`; `onResume` la re-lanza si estaba activa y tiene permiso.
- UI (todo en código, sin XML): `root` (LinearLayout vertical, padding 6 dp) = `[listTop][head][listBottom]`; la **cabeza** (`ImageView` 56 dp, `Ui.avatar` con color/letra de la cuenta activa, aro blanco, punto ámbar si está en descanso) nunca se re-parenta (si no, se cancela el arrastre). Toque = despliega la lista hacia el lado con más espacio (arriba si está en la mitad inferior; alineada a derecha/izquierda según el borde) con animación en cascada; cada fila = avatar + etiqueta con nombre. Última fila (la más lejana a la cabeza): "Ocultar burbuja". Arrastrable; al soltar se pega al borde más cercano y guarda posición (`bubbleX/bubbleY`). `FLAG_WATCH_OUTSIDE_TOUCH` cierra la lista al tocar afuera.
- Cambiar de cuenta: `switchTo(id)` → `store.selectedId = id` + `startActivity(MainActivity, EXTRA_SELECT=id, NEW_TASK|SINGLE_TOP|REORDER_TO_FRONT)` (permitido desde segundo plano por tener el permiso de overlay).
- Sincronización: `BubbleService.refresh()` (estático, hilo principal) la llama `MainActivity` al cambiar de cuenta, editar cuentas o vencer límites. Notificación con acción "Ocultar burbuja" (`ACTION_STOP`).
- Coordenadas: `hx/hy` = esquina de la cabeza en pantalla; la ventana se posiciona en `hx-pad, hy-pad` y, expandida, se recalcula y se clampa a la pantalla.

### 4.4 Persistencia (`AccountStore`, `SharedPreferences("claudeck")`)
Claves: `accounts` (JSON), `selected`, `bubble_enabled`, `bubble_x`, `bubble_y`, `dock_collapsed`, `bubble_tip_shown`. La comparten Activity y servicio (mismo proceso).

### 4.5 Misc
- `usesCleartextTraffic=false`, `allowBackup=false`. Config WebView: JS, DOM storage, `allowFileAccess=false`, `mediaPlaybackRequiresUserGesture=true`.
- Íconos de la app: adaptive icon vectorial (`ic_launcher_foreground.xml`, fondo `#2B2622`). Íconos de UI: `ic_swap`, `ic_expand`, `ic_more`, `ic_add`, `ic_bubble` (notificación).
- **Sin poder compilar en local**: esta versión se escribió sin compilar (solo CI). Si el primer build falla, el error estará casi seguro en `MainActivity.kt`/`BubbleService.kt`: corregir ahí.

## 5. Windows — cómo funciona

- Electron `^33.2.0`, electron-builder `^25.1.8`, targets `nsis` (no one-click, por usuario, permite cambiar carpeta) y `portable`. Artefactos: `Claudeck-Setup-<ver>.exe`, `Claudeck-Portable-<ver>.exe`. `files` solo incluye `main.js`, `preload.js`, `renderer/**/*`, `package.json` → **si agregás un archivo nuevo, sumarlo a `build.files`**.
- **Arquitectura de ventana:** una `BrowserWindow` con el renderer (`renderer/index.html`: barra lateral de 76 px + topbar de 44 px + diálogos) y, **encima**, una `WebContentsView` por cuenta (claude.ai) posicionada con `setBounds` en el área restante. Las constantes `RAIL_W = 76` y `TOPBAR_H = 44` en `main.js` **deben coincidir con `--rail-w` / `--topbar-h` en `styles.css`**.
- **Gotcha clave de los modales:** las `WebContentsView` se dibujan por encima del HTML del renderer, así que un `<dialog>` quedaría tapado. Por eso el renderer llama `api.modal(true/false)` al abrir/cerrar cualquier diálogo y `main.js` oculta todas las vistas (`modalOpen`). Todo diálogo nuevo debe pasar por `openDialog()` en `app.js` (ya engancha el evento `close`).
- Vistas creadas perezosamente en `selectAccount` (`createView`). `partition: 'persist:acc_<id>'`, `contextIsolation`, `sandbox`, sin `nodeIntegration`. Solo la vista seleccionada es visible.
- Seguridad/navegación: `setWindowOpenHandler` → `openLink`: hosts `claude.ai|claude.com|anthropic.ai|anthropic.com` (regex `TRUSTED_HOST`) se cargan en la misma vista; el resto va a `shell.openExternal`; `mailto:` también externo. La ventana del renderer niega `will-navigate` y `window.open`. Permisos de sesión permitidos: `media`, `clipboard-sanitized-write`, `fullscreen`, `notifications` (`ALLOWED_PERMISSIONS`).
- User-agent: se le quita `Electron/x` para que parezca Chromium normal.
- Instancia única (`requestSingleInstanceLock`) → la segunda instancia enfoca la primera. `window-all-closed` → `app.quit()`.
- **IPC** (`ipcMain.handle`, expuesto en `preload.js` como `window.api`): `state:get`, `account:add|update|select|reload|wipe|remove|limit|menu`, `ui:modal`. Canales main→renderer: `state` (se emite el estado completo en `broadcast()`) y `action` (`add`, `edit`, `limit`, `confirm-wipe`, `confirm-remove`). Para agregar una función: handler en `main.js` + línea en `preload.js` + uso en `app.js`.
- El menú contextual de cada cuenta es **nativo** (`Menu.buildFromTemplate`, `account:menu`) y le devuelve acciones al renderer vía `action`; las confirmaciones las dibuja el renderer.
- Atajos (`handleShortcut`, enganchado en la ventana **y** en cada vista): `Ctrl+1…9`, `Ctrl+Tab`/`Ctrl+Shift+Tab`, `Ctrl+R` (recarga la vista, no la ventana), `Ctrl+N`.
- Límites: `setInterval(checkLimits, 30000)` en main → limpia vencidos y dispara `Notification` del sistema (esto sí funciona con la app minimizada). El renderer re-renderiza cada 30 s para actualizar contadores.
- CSP del renderer estricta: `default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:`. **Sin scripts ni `style=""` inline en el HTML**; los colores dinámicos se aplican con `el.style.setProperty('--c', ...)` desde JS (eso sí está permitido). Los SVG del logo están inline en `index.html` (duplicados en la barra y el estado vacío).
- Wipe: `session.fromPartition(...).clearStorageData()` + `clearCache()`, después destruye/recrea la vista.
- Tema claro/oscuro automático vía `prefers-color-scheme` en `styles.css` (variables CSS en `:root`).

## 6. Build y CI (lo más fácil de romper)

**Flujo del dueño:** sube `cd.zip` a la raíz del repo → `cd.yml` se dispara (`push` con `paths: cd.zip` o manual). Job `extract`: **borra todo el repo excepto `.git`, `cd.zip` y `.github`, descomprime el zip y commitea los archivos descomprimidos** (`git push` con `GITHUB_TOKEN`, permiso `contents: write`; no dispara otros workflows). Los workflows que vengan dentro del zip **se ignoran** (Actions no puede modificar `.github/workflows`; para cambiarlos hay que editarlos a mano en GitHub). Después los jobs `android` y `windows` (`needs: extract`) hacen checkout del repo ya actualizado y compilan en paralelo. Resultados en **Artifacts** (`Claudeck-Android` → `Claudeck-<run>.apk`; `Claudeck-Windows` → los dos `.exe`).

Reglas del `cd.zip` (si lo regenerás):
- Debe tener en la raíz `android/`, `windows/`, `.github/` (sin carpeta intermedia; si hay **una sola** carpeta raíz, igual la detecta). Ignora `__MACOSX`.
- **Todo lo que no esté en el zip se borra** del repo (y queda commiteado el borrado) en cada corrida, salvo `.git`, `cd.zip` y `.github`. Incluir siempre `.github/workflows/*.yml`, `.gitignore`, `README.md`, `scripts/` y este archivo.
- El `.gitignore` tiene `build/` pero con excepción `!windows/build/` (ahí viven los íconos). No renombrar esa carpeta.
- `cd.yml` también debe existir en el repo antes (el README dice que se crea una sola vez a mano; el que va dentro del zip no se "auto-instala" porque el workflow que corre es el ya commiteado).

**Versionado:** automático por `github.run_number`.
- Android: `versionCode = GITHUB_RUN_NUMBER`, `versionName = 1.0.N` (local sin variable → 1).
- Windows: `npm version 1.0.N --no-git-tag-version` antes del build (el `version` de `package.json` en el repo es siempre `1.0.0` y está bien).
- Ojo: el `run_number` es **por workflow**, así que APKs de `cd.yml` y `android.yml` tienen contadores distintos. Si el dueño mezcla ambos flujos, un `versionCode` menor no se instala encima de uno mayor. Se recomienda usar solo `cd.yml`.

**Firma Android:** el build `release` se firma con `android/app/debug.keystore` (alias `androiddebugkey`, pass `android`, PKCS12), declarado como `signingConfigs["debug"]`. Es **a propósito**: misma firma en todas las versiones = se actualiza sin perder sesiones. **No regenerar ni borrar el keystore** (obligaría a desinstalar y se pierden las sesiones). `isMinifyEnabled=false`, lint no aborta.

**Toolchain CI:** Android → `ubuntu-latest`, Temurin 17, Gradle 8.9 (vía `gradle/actions/setup-gradle`, no hay wrapper), comando `gradle assembleRelease --no-daemon`. Windows → `windows-latest`, Node 20, `npx electron-builder --win --x64 --publish never` con `CSC_IDENTITY_AUTO_DISCOVERY=false` (sin firma de código → SmartScreen avisa).

**Probar en local (Windows app):** `cd windows && npm install && npm start`. Android no se compila en local en este flujo.

**Íconos:** `python scripts/make_icon.py` (requiere Pillow) regenera `windows/build/icon.png|ico`. El ícono de Android es un vector aparte (`ic_launcher_foreground.xml`) y el SVG de `index.html` es otra copia: si cambiás el diseño, actualizar **las tres**.

## 7. Limitaciones y deuda conocida (candidatas a trabajar)

1. "Continuar con Google" puede ser bloqueado por Google en navegadores embebidos (ambas plataformas).
2. Android: micrófono y cámara no soportados (falta `onPermissionRequest` + permisos en el manifest).
3. Android: el aviso de fin de descanso solo aparece con la app abierta (no hay `WorkManager`/alarma/notificación).
4. Android: no hay filtro de hosts como en Windows (`TRUSTED_HOST`); links externos se abren dentro del WebView.
5. Android: `onPause` pausa todos los WebViews (las cuentas en segundo plano no siguen cargando/streameando).
6. Android: textos hardcodeados fuera de `strings.xml`; sin i18n en ninguna plataforma.
7. Windows: sin firma de código; sin auto-actualización; sin ícono de bandeja. **Windows no recibió ninguna de las mejoras de la versión Android nueva** (burbuja, descargas, rediseño): sigue igual.
8. Sin tests automáticos; sin gradle wrapper; sin minificación/ProGuard.
9. La lógica de cuentas/límites está duplicada Kotlin ↔ JS: cualquier cambio de modelo se hace dos veces.
10. Android: la burbuja requiere permiso de overlay y no existe en Windows; las descargas `blob:` dependen de que `WEB_MESSAGE_LISTENER` esté soportado por el WebView.
11. Android: el código de la v. con burbuja/descargas/rediseño no se probó en dispositivo ni se compiló antes de entregarlo.

## 8. Convenciones para modificar

- **Mínimos cambios, respetar estilo**: Kotlin con `private fun` agrupadas por secciones `// ---- Título`; JS `'use strict'`, 2 espacios, comillas simples, sin dependencias de runtime (solo devDependencies).
- Mantener **paridad de funciones** entre Android y Windows salvo que se pida lo contrario.
- No agregar dependencias pesadas sin necesidad: el valor del proyecto es ser un contenedor liviano.
- No enviar ni leer contenido de las conversaciones; no usar APIs de Anthropic. El README tiene una nota de uso responsable (no usar varias cuentas para esquivar límites de una sola): mantenerla.
- Cualquier cambio de modelo de datos debe ser **retrocompatible** con lo guardado (Android: `optString/optLong` con defaults; Windows: `loadState` ya sanea campos). Las claves nuevas de `AccountStore` tienen default, así que actualizar encima de una versión vieja es seguro.
- Al terminar un cambio: actualizar `README.md` (si afecta al usuario) y este `CLAUDE.md` (si afecta a la arquitectura), y **regenerar `cd.zip`** con la estructura de §6.

## 9. Receta rápida para regenerar el zip

```bash
# desde la raíz del proyecto (donde están android/, windows/, .github/)
zip -r cd.zip . -x "cd.zip" -x ".git/*" -x "*/node_modules/*" -x "*/dist/*" -x "*/.gradle/*" -x "android/app/build/*"
```
