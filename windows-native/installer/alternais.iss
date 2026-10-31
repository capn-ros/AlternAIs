; Instalador de AlternAIs (Windows) con Inno Setup.
; Versión nativa (WinUI 3): lo compila el workflow después de «dotnet publish» (carpeta publish\).
; Uso manual:  ISCC.exe /DAppVersion=1.0.5 windows-native\installer\alternais.iss

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
; GUID propio de la app (antes Alterna): no cambiarlo, así las actualizaciones pisan la versión instalada.
AppId={{6F3B2C9A-4D71-4E58-9A0B-C1A2D3E4F5A6}
AppName=AlternAIs
AppVersion={#AppVersion}
AppPublisher=Andrés Londero
DefaultDirName={autopf}\AlternAIs
DefaultGroupName=AlternAIs
UninstallDisplayIcon={app}\AlternAIs.exe
SetupIconFile=..\src\AlternAIs.App\Assets\icon.ico
OutputDir=..\dist
OutputBaseFilename=AlternAIs-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
WizardImageFile=..\..\windows\installer\claudeck-wizard.bmp
WizardSmallImageFile=..\..\windows\installer\claudeck-wizard-small.bmp
WizardImageBackColor=$2B2622
WizardImageStretch=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; Por usuario y sin pedir administrador (como antes); el usuario puede elegir "para todos" en el diálogo.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DisableProgramGroupPage=yes
CloseApplications=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AlternAIs"; Filename: "{app}\AlternAIs.exe"
Name: "{group}\Desinstalar AlternAIs"; Filename: "{uninstallexe}"
Name: "{autodesktop}\AlternAIs"; Filename: "{app}\AlternAIs.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\AlternAIs.exe"; Description: "Abrir AlternAIs"; Flags: nowait postinstall skipifsilent

[InstallDelete]
; Restos de la versión anterior hecha con Electron (misma carpeta, mismo AppId: esta versión se instala encima).
Type: filesandordirs; Name: "{app}\resources"
Type: filesandordirs; Name: "{app}\locales"
Type: files; Name: "{app}\*.pak"
Type: files; Name: "{app}\ffmpeg.dll"
Type: files; Name: "{app}\libEGL.dll"
Type: files; Name: "{app}\libGLESv2.dll"
Type: files; Name: "{app}\d3dcompiler_47.dll"
Type: files; Name: "{app}\vulkan-1.dll"
Type: files; Name: "{app}\vk_swiftshader*"
Type: files; Name: "{app}\snapshot_blob.bin"
Type: files; Name: "{app}\v8_context_snapshot.bin"
Type: files; Name: "{app}\icudtl.dat"
Type: files; Name: "{app}\LICENSES.chromium.html"
; Limpia restos de cuando la app se llamaba Claudeck (mismo AppId: actualiza encima).
; Y de cuando se llamaba Alterna o AlternIAs.
Type: files; Name: "{app}\AlternIAs.exe"
Type: files; Name: "{autodesktop}\AlternIAs.lnk"
Type: files; Name: "{group}\AlternIAs.lnk"
Type: files; Name: "{app}\Alterna.exe"
Type: files; Name: "{autodesktop}\Alterna.lnk"
Type: files; Name: "{group}\Alterna.lnk"
Type: files; Name: "{app}\Claudeck.exe"
Type: files; Name: "{autodesktop}\Claudeck.lnk"
Type: files; Name: "{group}\Claudeck.lnk"
Type: files; Name: "{app}\Duodeck.exe"
