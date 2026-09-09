<#
.SYNOPSIS
  Gera os assets de imagem da marca Cafey a partir dos SVGs, via Inkscape CLI.

.DESCRIPTION
  Fontes (geradas por gen_logo.py):
    cafey-logo.svg              marca, com respiro nas bordas
    cafey-icon.svg              icone generico, desenho a 80% do quadro
    cafey-icon-foreground.svg   camada foreground do icone adaptativo Android
                                (desenho dentro da zona segura de 72dp/108dp)

  No Windows use inkscape.com, nao inkscape.exe: o .exe e' do subsistema GUI
  e nao escreve no console, entao erros passam despercebidos em script.

  Inkscape nao exporta .ico. Para o favicon multi-resolucao, combine os PNGs
  com outra ferramenta (ImageMagick, png2ico).

.EXAMPLE
  .\export-assets.ps1
  .\export-assets.ps1 -OutDir ..\..\..\apps\shared\assets
#>
[CmdletBinding()]
param(
    [string]$OutDir  = (Join-Path $PSScriptRoot 'assets'),
    [string]$Inkscape = 'C:\Program Files\Inkscape\bin\inkscape.com'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Inkscape)) {
    throw "Inkscape nao encontrado em '$Inkscape'. Passe -Inkscape com o caminho do inkscape.com."
}

$srcLogo       = Join-Path $PSScriptRoot 'cafey-logo.svg'
$srcIcon       = Join-Path $PSScriptRoot 'cafey-icon.svg'
$srcForeground = Join-Path $PSScriptRoot 'cafey-icon-foreground.svg'
foreach ($s in @($srcLogo, $srcIcon, $srcForeground)) {
    if (-not (Test-Path -LiteralPath $s)) { throw "Fonte ausente: $s. Rode 'python gen_logo.py' antes." }
}

function Export-Png {
    param([string]$Source, [int]$Size, [string]$Destination)

    $dir = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    # -w define a largura; a altura acompanha pelo viewBox quadrado.
    & $Inkscape $Source -w $Size -o $Destination 2>&1 | Out-Null
    if (-not (Test-Path -LiteralPath $Destination)) { throw "Falha ao exportar $Destination" }
    $shown = (Resolve-Path -LiteralPath $Destination).Path.Substring((Resolve-Path -LiteralPath $OutDir).Path.Length).TrimStart('\')
    "{0,-46} {1,4}px" -f $shown, $Size
}

# marca solta, fundo transparente
foreach ($s in 256, 512, 1024) {
    Export-Png -Source $srcLogo -Size $s -Destination (Join-Path $OutDir "logo/cafey-logo-$s.png")
}

# icone generico (desktop, web, uso avulso)
foreach ($s in 16, 32, 48, 64, 128, 256, 512, 1024) {
    Export-Png -Source $srcIcon -Size $s -Destination (Join-Path $OutDir "icon/cafey-icon-$s.png")
}

# iOS: um unico 1024x1024 no Asset Catalog; o Xcode deriva o resto (Single Size)
Export-Png -Source $srcIcon -Size 1024 -Destination (Join-Path $OutDir 'ios/AppIcon-1024.png')

# Android legado: launcher de 48dp nas cinco densidades
$densities = [ordered]@{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }
foreach ($d in $densities.GetEnumerator()) {
    $px = [int][math]::Round(48 * $d.Value)
    Export-Png -Source $srcIcon -Size $px -Destination (Join-Path $OutDir "android/mipmap-$($d.Key)/ic_launcher.png")
}

# Android adaptativo: camada foreground de 108dp nas mesmas densidades
foreach ($d in $densities.GetEnumerator()) {
    $px = [int][math]::Round(108 * $d.Value)
    Export-Png -Source $srcForeground -Size $px -Destination (Join-Path $OutDir "android/mipmap-$($d.Key)/ic_launcher_foreground.png")
}

# marca vetorial para documento e impressao
& $Inkscape $srcLogo -o (Join-Path $OutDir 'logo/cafey-logo.pdf') 2>&1 | Out-Null

Write-Host ''
Write-Host "Assets gerados em: $OutDir"
