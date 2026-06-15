param(
  [Parameter(Mandatory = $true)]
  [string]$FtpHost,
  [Parameter(Mandatory = $true)]
  [string]$Username,
  [Parameter(Mandatory = $true)]
  [string]$Password,
  [string]$RemoteRoot = "/",
  [string]$SourceRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

function Get-RelativeUnixPath {
  param(
    [string]$BasePath,
    [string]$FullPath
  )

  $baseFull = [System.IO.Path]::GetFullPath($BasePath)
  if (-not $baseFull.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
    $baseFull += [System.IO.Path]::DirectorySeparatorChar
  }
  $full = [System.IO.Path]::GetFullPath($FullPath)
  $baseUri = New-Object System.Uri($baseFull)
  $fullUri = New-Object System.Uri($full)
  $relativeUri = $baseUri.MakeRelativeUri($fullUri)
  return [System.Uri]::UnescapeDataString($relativeUri.ToString())
}

function Join-RemotePath {
  param(
    [string]$Root,
    [string]$RelativePath
  )

  $trimmedRoot = if ([string]::IsNullOrWhiteSpace($Root)) { "/" } else { $Root.TrimEnd("/") }
  $segments = $RelativePath.Split("/") | Where-Object { $_ -ne "" } | ForEach-Object { [Uri]::EscapeDataString($_) }
  if ($segments.Count -eq 0) {
    return $trimmedRoot
  }
  if ($trimmedRoot -eq "") {
    return "/" + ($segments -join "/")
  }
  if ($trimmedRoot -eq "/") {
    return "/" + ($segments -join "/")
  }
  return $trimmedRoot + "/" + ($segments -join "/")
}

$sourceRootFull = [System.IO.Path]::GetFullPath($SourceRoot)

$excludePrefixes = @(
  "artifacts/",
  "docs/",
  "scripts/",
  "api/_lib/",
  "api/auth/",
  "api/data/",
  "api/public-data/"
)

$excludeNames = @(
  ".env.local",
  "README.md",
  "auth_proxy_setup.md",
  "data_proxy_setup.md",
  "server.mjs",
  "supabase-schema.sql",
  "vercel.json",
  "probe.txt"
)

$includeOnlyApi = @(
  "api/config.php",
  "api/config.local.php",
  "api/config.example.php",
  "api/index.php"
)

$files = Get-ChildItem -Path $sourceRootFull -Recurse -File | Where-Object {
  $relative = Get-RelativeUnixPath -BasePath $sourceRootFull -FullPath $_.FullName
  if ($excludeNames -contains $_.Name) {
    return $false
  }
  foreach ($prefix in $excludePrefixes) {
    if ($relative.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
      return $false
    }
  }
  if ($relative.StartsWith("api/", [System.StringComparison]::OrdinalIgnoreCase) -and $_.Extension -eq ".mjs") {
    return $false
  }
  return $true
}

$apiFiles = @()
foreach ($apiPath in $includeOnlyApi) {
  $fullPath = Join-Path $sourceRootFull $apiPath
  if (Test-Path $fullPath) {
    $apiFiles += Get-Item $fullPath
  }
}

$combinedFiles = @($files + $apiFiles | Sort-Object FullName -Unique)

Write-Host "Uploading $($combinedFiles.Count) files from $sourceRootFull to ftp://$FtpHost$RemoteRoot"

foreach ($file in $combinedFiles) {
  $relative = Get-RelativeUnixPath -BasePath $sourceRootFull -FullPath $file.FullName
  $remotePath = Join-RemotePath -Root $RemoteRoot -RelativePath $relative
  $targetUrl = "ftp://$FtpHost$remotePath"
  Write-Host "Uploading $relative"
  & curl.exe --silent --show-error --ftp-create-dirs --disable-epsv --user "${Username}:${Password}" -T $file.FullName $targetUrl | Out-Null
}

Write-Host "FTP deploy complete."
