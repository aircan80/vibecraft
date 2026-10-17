param([string]$Jar, [int]$WaitSeconds = 12, [string]$Shot = "launch.png", [string]$OutDir)
$ErrorActionPreference = "Continue"
if (-not ("W32L" -as [type])) {
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W32L {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
}
"@
}
[W32L]::SetProcessDPIAware() | Out-Null
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $OutDir | Out-Null
$java = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javaw.exe"
$err = Join-Path $OutDir "stderr.txt"
$p = Start-Process -FilePath $java -ArgumentList @("-jar", $Jar) -RedirectStandardError $err -PassThru -WorkingDirectory (Split-Path -Parent $Jar)
Start-Sleep -Seconds $WaitSeconds
$p.Refresh()
$h = $p.MainWindowHandle
"pid=$($p.Id) alive=$(-not $p.HasExited) hwnd=$h title=[$($p.MainWindowTitle)]"
if ($h -ne [IntPtr]::Zero) {
  [W32L]::ShowWindow($h, 9) | Out-Null
  [W32L]::SetForegroundWindow($h) | Out-Null
  Start-Sleep -Milliseconds 800
  $r = New-Object W32L+RECT
  [W32L]::GetWindowRect($h, [ref]$r) | Out-Null
  $w = $r.R - $r.L; $hh = $r.B - $r.T
  "rect=$($r.L),$($r.T) ${w}x${hh}"
  $bmp = New-Object System.Drawing.Bitmap $w, $hh
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.L, $r.T, 0, 0, (New-Object System.Drawing.Size $w, $hh))
  $png = Join-Path $OutDir $Shot
  $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  "screenshot=$png"
}
