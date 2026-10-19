# Генератор тир-текстур QuantX: II — золотая отделка, III — бирюза/алмаз.
# Берёт базовую (I тир, «ванильную») текстуру, перекрашивает акцентные
# пиксели в цвет металла тира и добавляет декоративные заклёпки.
# Запуск: powershell -File tools/gen_tier_textures.ps1

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\quantx\textures'
$BLOCK = Join-Path $root 'block'
$ITEM  = Join-Path $root 'item'

function Convert-RgbToHsv([System.Drawing.Color]$p) {
    $r = $p.R / 255.0; $g = $p.G / 255.0; $b = $p.B / 255.0
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    $d = $max - $min
    $h = 0.0
    if ($d -gt 0) {
        if ($max -eq $r)      { $h = 60.0 * ((($g - $b) / $d) % 6.0) }
        elseif ($max -eq $g)  { $h = 60.0 * (($b - $r) / $d + 2.0) }
        else                  { $h = 60.0 * (($r - $g) / $d + 4.0) }
    }
    if ($h -lt 0) { $h += 360.0 }
    $s = 0.0; if ($max -gt 0) { $s = $d / $max }
    return @($h, $s, $max)
}

function Convert-HsvToColor([double]$h, [double]$s, [double]$v) {
    $c = $v * $s
    $x = $c * (1.0 - [Math]::Abs((($h / 60.0) % 2.0) - 1.0))
    $m = $v - $c
    $r = 0.0; $g = 0.0; $b = 0.0
    if    ($h -lt 60)  { $r = $c; $g = $x }
    elseif ($h -lt 120) { $r = $x; $g = $c }
    elseif ($h -lt 180) { $g = $c; $b = $x }
    elseif ($h -lt 240) { $g = $x; $b = $c }
    elseif ($h -lt 300) { $r = $x; $b = $c }
    else                { $r = $c; $b = $x }
    return [System.Drawing.Color]::FromArgb(255,
        [Math]::Round(($r + $m) * 255),
        [Math]::Round(($g + $m) * 255),
        [Math]::Round(($b + $m) * 255))
}

# Металлы тира: цвет акцента (hue), яркая заклёпка, тёмная кромка.
$TierHue   = @{ 2 = 45.0;  3 = 183.0 }
$StudLight = @{ 2 = [System.Drawing.Color]::FromArgb(255, 255, 224, 110)
                3 = [System.Drawing.Color]::FromArgb(255, 130, 245, 240) }
$StudDark  = @{ 2 = [System.Drawing.Color]::FromArgb(255, 140, 100, 18)
                3 = [System.Drawing.Color]::FromArgb(255, 24, 130, 135) }

function Is-Accent([double]$hue, [double]$sat, [string]$mode) {
    switch ($mode) {
        'teal' { return ($hue -ge 130 -and $hue -le 220 -and $sat -gt 0.12) }
        'warm' { return (($hue -le 80 -or $hue -ge 320) -and $sat -gt 0.12) }
        'all'  { return ($sat -gt 0.15) }
    }
    return $false
}

function New-TierTexture([string]$src, [string]$dst, [int]$tier, [string]$mode) {
    $img = [System.Drawing.Bitmap]::FromFile($src)
    $bmp = New-Object System.Drawing.Bitmap $img.Width, $img.Height
    $th = $TierHue[$tier]
    for ($y = 0; $y -lt $img.Height; $y++) {
        for ($x = 0; $x -lt $img.Width; $x++) {
            $p = $img.GetPixel($x, $y)
            if ($p.A -lt 10) { $bmp.SetPixel($x, $y, $p); continue }
            $hsv = Convert-RgbToHsv $p
            if (Is-Accent $hsv[0] $hsv[1] $mode) {
                $ns = [Math]::Min(1.0, $hsv[1] * 1.1 + 0.12)
                $nv = if ($tier -eq 3) { [Math]::Min(1.0, $hsv[2] * 1.10) }
                      else             { [Math]::Min(1.0, $hsv[2] * 1.03) }
                $c = Convert-HsvToColor $th $ns $nv
                $bmp.SetPixel($x, $y,
                    [System.Drawing.Color]::FromArgb($p.A, $c.R, $c.G, $c.B))
            } else {
                $bmp.SetPixel($x, $y, $p)
            }
        }
    }

    # Декоративные заклёпки по краю: II — редкие в углах,
    # III — углы + середины рёбер (больше деталей).
    $w = [int]($bmp.Width) - 1
    $wm2 = $w - 2
    $wm1 = $w - 1
    if ($tier -eq 2) {
        $studs = @(@(2, 2), @($wm2, 2), @(2, $wm2), @($wm2, $wm2))
    } else {
        $mid = [int][Math]::Floor($w / 2)
        $mid1 = $mid + 1
        $studs = @(@(1, 1), @($wm1, 1), @(1, $wm1), @($wm1, $wm1),
                   @($mid, 0), @($mid1, 0), @($mid, $w), @($mid1, $w),
                   @(0, $mid), @(0, $mid1), @($w, $mid), @($w, $mid1))
    }
    foreach ($s in $studs) {
        $x = $s[0]; $y = $s[1]
        $cur = $bmp.GetPixel($x, $y)
        if ($cur.A -gt 0) { $bmp.SetPixel($x, $y, $StudLight[$tier]) }
    }

    $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose(); $bmp.Dispose()
    Write-Host "ok $dst"
}

# Апгрейд-кит: тирная рама с красным ядром-редстоуном в центре.
function New-UpgradeTexture([string]$src, [string]$dst) {
    $img = [System.Drawing.Bitmap]::FromFile($src)
    $bmp = New-Object System.Drawing.Bitmap $img.Width, $img.Height
    for ($y = 0; $y -lt $img.Height; $y++) {
        for ($x = 0; $x -lt $img.Width; $x++) {
            $bmp.SetPixel($x, $y, $img.GetPixel($x, $y))
        }
    }
    $red      = [System.Drawing.Color]::FromArgb(255, 240, 48, 48)
    $darkRed  = [System.Drawing.Color]::FromArgb(255, 130, 16, 16)
    $c0 = [int][Math]::Floor($bmp.Width / 2) - 2
    for ($y = $c0; $y -lt $c0 + 4; $y++) {
        for ($x = $c0; $x -lt $c0 + 4; $x++) {
            $edge = ($x -eq $c0 -or $x -eq $c0 + 3 -or $y -eq $c0 -or $y -eq $c0 + 3)
            $bmp.SetPixel($x, $y, $(if ($edge) { $darkRed } else { $red }))
        }
    }
    $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose(); $bmp.Dispose()
    Write-Host "ok $dst"
}

# ===== Генерация =====

# Рамы: тёплый гранит → металл тира.
New-TierTexture (Join-Path $ITEM 'granite_frame.png') (Join-Path $ITEM 'granite_frame_2.png') 2 'warm'
New-TierTexture (Join-Path $ITEM 'granite_frame.png') (Join-Path $ITEM 'granite_frame_3.png') 3 'warm'

# Ранцы: вся окраска → металл тира (золотой / алмазный ранец).
New-TierTexture (Join-Path $ITEM 'matter_pack.png') (Join-Path $ITEM 'matter_pack_2.png') 2 'all'
New-TierTexture (Join-Path $ITEM 'matter_pack.png') (Join-Path $ITEM 'matter_pack_3.png') 3 'all'

# Апгрейды: тирная рама + красное ядро.
New-UpgradeTexture (Join-Path $ITEM 'granite_frame_2.png') (Join-Path $ITEM 'materializer_upgrade_2.png')
New-UpgradeTexture (Join-Path $ITEM 'granite_frame_3.png') (Join-Path $ITEM 'materializer_upgrade_3.png')

# Хранилища: бирюзовые акценты → металл тира.
New-TierTexture (Join-Path $BLOCK 'matter_storage.png') (Join-Path $BLOCK 'matter_storage_2.png') 2 'teal'
New-TierTexture (Join-Path $BLOCK 'matter_storage.png') (Join-Path $BLOCK 'matter_storage_3.png') 3 'teal'

# Материализатор: все 6 граней × 2 тира.
foreach ($face in 'front', 'back', 'left', 'right', 'top', 'bottom') {
    New-TierTexture (Join-Path $BLOCK "materializer_$face.png") `
        (Join-Path $BLOCK "materializer_2_$face.png") 2 'teal'
    New-TierTexture (Join-Path $BLOCK "materializer_$face.png") `
        (Join-Path $BLOCK "materializer_3_$face.png") 3 'teal'
}

Write-Host 'Done.'
