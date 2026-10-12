# Converts Kenney Nature Kit (CC0) OBJ models to GLB and copies into app assets.
# Requires: kenney_nature-kit.zip extracted to asset-staging/kenney-nature/

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$srcDir = Join-Path $root "asset-staging\kenney-nature\Models\OBJ format"
$stagingGlb = Join-Path $root "asset-staging\kenney-glb"
$destDir = Join-Path $root "app\src\main\assets\plants"

if (-not (Test-Path $srcDir)) {
    Write-Error "Kenney OBJ folder not found. Download from https://kenney.nl/assets/nature-kit"
}

New-Item -ItemType Directory -Force -Path $stagingGlb, $destDir | Out-Null

# 20 species -> best-matching Kenney models (low-poly, cohesive style)
$map = [ordered]@{
    oak           = "tree_oak.obj"
    pine          = "tree_pineRoundA.obj"
    willow        = "tree_thin.obj"
    birch         = "tree_simple.obj"
    cypress       = "tree_cone.obj"
    palm          = "tree_palm.obj"
    lavender      = "flower_purpleA.obj"
    sunflower     = "flower_yellowA.obj"
    blossom       = "flower_redA.obj"
    wildflower    = "flower_yellowB.obj"
    fern          = "plant_flatShort.obj"
    moss          = "ground_grass.obj"
    bush          = "plant_bushLarge.obj"
    vine          = "grass_leafs.obj"
    mushroom      = "mushroom_red.obj"
    seagrass      = "grass_large.obj"
    clover        = "plant_bushSmall.obj"
    iris          = "lily_large.obj"
    lantern_bloom = "flower_redC.obj"
    ancient_oak   = "tree_detailed.obj"
}

foreach ($entry in $map.GetEnumerator()) {
    $assetName = $entry.Key
    $objFile = $entry.Value
    $objPath = Join-Path $srcDir $objFile
    $glbStaging = Join-Path $stagingGlb "$assetName.glb"
    $glbDest = Join-Path $destDir "$assetName.glb"

    if (-not (Test-Path $objPath)) {
        Write-Warning "Missing $objFile - skipping $assetName"
        continue
    }

    Write-Host "Converting $objFile -> $assetName.glb"
    npx --yes obj2gltf -i $objPath -o $glbStaging | Out-Null
    Copy-Item -Force $glbStaging $glbDest
    $kb = [math]::Round((Get-Item $glbDest).Length / 1KB, 1)
    Write-Host "  -> $kb KB"
}

Write-Host ""
Write-Host "Done. $(($map.Keys | Measure-Object).Count) models in $destDir"
