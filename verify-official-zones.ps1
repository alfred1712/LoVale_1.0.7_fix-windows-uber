param([string]$CsvPath = "$PSScriptRoot/delitos-2025-source.csv")
$ErrorActionPreference = 'Stop'
# Fuente GCBA, CC BY. Descargar desde:
# https://data.buenosaires.gob.ar/dataset/delitos/resource/66c0f0f6-e682-403d-abbe-47227d1966fe/download
# Snapshot consultado 2026-09-11, SHA256:
# 1ED67C3346EB4F42635CC44BC78E00EC2B023370F1FF6DF0E7F309C72A6B45DF
$rows = Import-Csv -LiteralPath $CsvPath
$ranking = $rows | Where-Object { $_.anio -eq '2025' -and $_.tipo -eq 'Robo' -and $_.barrio } |
    Group-Object barrio | ForEach-Object {
        [pscustomobject]@{ Barrio = $_.Name; Cantidad = ($_.Group | Measure-Object cantidad -Sum).Sum }
    } | Sort-Object Cantidad -Descending
$expected = @{'PALERMO'=3879; 'FLORES'=3128; 'BALVANERA'=2923; 'CABALLITO'=2381; 'RECOLETA'=2184}
$top = @($ranking | Select-Object -First 5)
foreach ($item in $top) {
    if ($expected[$item.Barrio] -ne $item.Cantidad) { throw "El snapshot cambió: revisar las cifras y actualizar OfficialZones.kt" }
}
$top | Format-Table
