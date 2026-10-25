; Instalador de AlternIAs (Windows) con Inno Setup.
; Lo compila el workflow después de electron-builder (que deja la app lista en dist\win-unpacked).
; Uso manual:  ISCC.exe /DAppVersion=1.0.5 windows\installer\claudeck.iss

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
; GUID propio de la app (antes Alterna): no cambiarlo, así las actualizaciones pisan la versión instalada.
AppId={{6F3B2C9A-4D71-4E58-9A0B-C1A2D3E4F5A6}
AppName=AlternIAs
AppVersion={#AppVersion}
AppPublisher=Andrés Londero
DefaultDirName={autopf}\AlternIAs
DefaultGroupName=AlternIAs
UninstallDisplayIcon={app}\AlternIAs.exe
SetupIconFile=..\build\icon.ico
OutputDir=..\dist
OutputBaseFilename=AlternIAs-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
WizardImageFile=claudeck-wizard.bmp
WizardSmallImageFile=claudeck-wizard-small.bmp
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
Source: "..\dist\win-unpacked\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AlternIAs"; Filename: "{app}\AlternIAs.exe"
Name: "{group}\Desinstalar AlternIAs"; Filename: "{uninstallexe}"
Name: "{autodesktop}\AlternIAs"; Filename: "{app}\AlternIAs.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\AlternIAs.exe"; Description: "Abrir AlternIAs"; Flags: nowait postinstall skipifsilent

[InstallDelete]
; Limpia restos de cuando la app se llamaba Claudeck (mismo AppId: actualiza encima).
; Y de cuando se llamaba Alterna.
Type: files; Name: "{app}\Alterna.exe"
Type: files; Name: "{autodesktop}\Alterna.lnk"
Type: files; Name: "{group}\Alterna.lnk"
Type: files; Name: "{app}\Claudeck.exe"
Type: files; Name: "{autodesktop}\Claudeck.lnk"
Type: files; Name: "{group}\Claudeck.lnk"
Type: files; Name: "{app}\Duodeck.exe"
