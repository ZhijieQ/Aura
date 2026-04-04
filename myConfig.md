```powershell
[Environment]::SetEnvironmentVariable('ANDROID_HOME', 'C:\Users\Zhijie\AppData\Local\Android\Sdk', 'Machine')
$sdkPath = "C:\Users\Zhijie\AppData\Local\Android\Sdk"
$oldPath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
[Environment]::SetEnvironmentVariable('Path', "$oldPath;$sdkPath\platform-tools", 'Machine')
```