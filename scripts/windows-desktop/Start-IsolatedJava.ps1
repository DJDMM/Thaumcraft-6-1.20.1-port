<#
Launch one final Java JVM on a named Win32 desktop without changing the input
desktop. Defaults to validation only. Run this script in a hidden/background
PowerShell process and keep that process alive until the JVM exits.

Do not pass GradleWrapperMain or a launcher that forks the actual client JVM.
The final Java launch arguments must be captured/prepared before using -Launch.
#>
[CmdletBinding()]
param(
    [string]$JavaPath = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe',
    [string[]]$JavaArguments = @(),
    [string]$WorkingDirectory = $PSScriptRoot,
    [string]$LogPath = (Join-Path $PSScriptRoot 'isolated-java.log'),
    [string]$StatusPath = (Join-Path $PSScriptRoot 'isolated-java-status.json'),
    [string]$EnvironmentFile,
    [ValidatePattern('^[A-Za-z0-9_-]{1,100}$')]
    [string]$DesktopName = ('ThaumcraftQA_' + [Guid]::NewGuid().ToString('N')),
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
$resolvedJava = (Resolve-Path -LiteralPath $JavaPath).Path
$resolvedWorkingDirectory = (Resolve-Path -LiteralPath $WorkingDirectory).Path
if (-not (Test-Path -LiteralPath $resolvedWorkingDirectory -PathType Container)) {
    throw 'WorkingDirectory must identify an existing directory.'
}
if ([IO.Path]::GetFileName($resolvedJava) -notin @('java.exe', 'javaw.exe')) {
    throw 'This helper only launches the final Java executable directly.'
}
if ($JavaArguments -contains 'org.gradle.wrapper.GradleWrapperMain' -or
    $JavaArguments -contains 'org.gradle.launcher.GradleMain' -or
    $JavaArguments -contains 'org.gradle.launcher.daemon.bootstrap.GradleDaemon') {
    throw 'Prepare the final client/server JVM arguments first; Gradle descendants are not verified desktop-isolated.'
}
$resolvedLog = [IO.Path]::GetFullPath($LogPath)
$resolvedStatus = [IO.Path]::GetFullPath($StatusPath)
if ($resolvedLog -eq $resolvedStatus) { throw 'LogPath and StatusPath must differ.' }
foreach ($outputPath in @($resolvedLog, $resolvedStatus)) {
    if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($outputPath)) -PathType Container)) {
        throw "Output directory does not exist: $outputPath"
    }
}
$environmentEntries = $null
if ($EnvironmentFile) {
    $environmentSpec = Get-Content -LiteralPath (Resolve-Path -LiteralPath $EnvironmentFile).Path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($environmentSpec.schemaVersion -ne 1) { throw 'Expected schemaVersion 1 environment overrides.' }
    $environmentMap = New-Object 'Collections.Generic.Dictionary[string,string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) {
        $environmentMap[[string]$entry.Key] = [string]$entry.Value
    }
    foreach ($name in @($environmentSpec.removedNames)) {
        if ($name) { [void]$environmentMap.Remove([string]$name) }
    }
    foreach ($property in $environmentSpec.overrides.PSObject.Properties) {
        if ($property.Name.IndexOf('=') -ge 0 -or $property.Name.IndexOf([char]0) -ge 0 -or
            $null -eq $property.Value -or ([string]$property.Value).IndexOf([char]0) -ge 0) {
            throw 'Invalid environment override name/value.'
        }
        $environmentMap[$property.Name] = [string]$property.Value
    }
    $environmentEntries = @($environmentMap.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value })
}

if (-not ('ThaumcraftQa.IsolatedJava' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace ThaumcraftQa {
    public static class IsolatedJava {
        const uint DESKTOP_READOBJECTS = 0x0001;
        const uint DESKTOP_CREATEWINDOW = 0x0002;
        const uint DESKTOP_CREATEMENU = 0x0004;
        const uint DESKTOP_ENUMERATE = 0x0040;
        const uint DESKTOP_WRITEOBJECTS = 0x0080;
        const uint CREATE_NO_WINDOW = 0x08000000;
        const uint CREATE_UNICODE_ENVIRONMENT = 0x00000400;
        const uint EXTENDED_STARTUPINFO_PRESENT = 0x00080000;
        const uint STARTF_USESTDHANDLES = 0x00000100;
        const uint STARTF_FORCEOFFFEEDBACK = 0x00000080;
        const uint FILE_APPEND_DATA = 0x0004;
        const uint GENERIC_READ = 0x80000000;
        const uint FILE_SHARE_READ = 1;
        const uint FILE_SHARE_WRITE = 2;
        const uint FILE_SHARE_DELETE = 4;
        const uint OPEN_EXISTING = 3;
        const uint WAIT_OBJECT_0 = 0;
        const uint WAIT_TIMEOUT = 258;
        const int UOI_NAME = 2;
        static readonly IntPtr InvalidHandle = new IntPtr(-1);

        [StructLayout(LayoutKind.Sequential)]
        struct SECURITY_ATTRIBUTES {
            public uint nLength;
            public IntPtr lpSecurityDescriptor;
            [MarshalAs(UnmanagedType.Bool)] public bool bInheritHandle;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        struct STARTUPINFO {
            public uint cb;
            public string lpReserved, lpDesktop, lpTitle;
            public uint dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars;
            public uint dwFillAttribute, dwFlags;
            public ushort wShowWindow, cbReserved2;
            public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
        }

        [StructLayout(LayoutKind.Sequential)]
        struct STARTUPINFOEX {
            public STARTUPINFO StartupInfo;
            public IntPtr lpAttributeList;
        }

        [StructLayout(LayoutKind.Sequential)]
        struct PROCESS_INFORMATION {
            public IntPtr hProcess, hThread;
            public uint dwProcessId, dwThreadId;
        }

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateDesktopW(string name, IntPtr device, IntPtr devmode,
            uint flags, uint access, IntPtr securityAttributes);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool CloseDesktop(IntPtr desktop);
        [DllImport("user32.dll", SetLastError = true)]
        static extern IntPtr GetProcessWindowStation();
        [DllImport("user32.dll", SetLastError = true)]
        static extern IntPtr OpenInputDesktop(uint flags, bool inherit, uint access);
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool GetUserObjectInformationW(IntPtr handle, int index,
            StringBuilder information, uint length, out uint needed);
        delegate bool EnumWindowCallback(IntPtr window, IntPtr parameter);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool EnumDesktopWindows(IntPtr desktop, EnumWindowCallback callback, IntPtr parameter);
        [DllImport("user32.dll", SetLastError = true)]
        static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateFileW(string filename, uint access, uint share,
            ref SECURITY_ATTRIBUTES attributes, uint disposition, uint flags,
            IntPtr template);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool InitializeProcThreadAttributeList(IntPtr list, int count,
            uint flags, ref IntPtr size);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags,
            IntPtr attribute, IntPtr value, IntPtr size, IntPtr previous, IntPtr returned);
        [DllImport("kernel32.dll")]
        static extern void DeleteProcThreadAttributeList(IntPtr list);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool CreateProcessW(string application, StringBuilder commandLine,
            IntPtr processAttributes, IntPtr threadAttributes, bool inheritHandles,
            uint flags, IntPtr environment, string directory,
            ref STARTUPINFOEX startup, out PROCESS_INFORMATION process);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool GetExitCodeProcess(IntPtr process, out uint exitCode);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool CloseHandle(IntPtr handle);

        static Exception NativeError(string operation) {
            return new Win32Exception(Marshal.GetLastWin32Error(), operation);
        }

        public static string QuoteArgument(string value) {
            if (value == null) throw new ArgumentNullException("value");
            var quoted = new StringBuilder("\"");
            int slashes = 0;
            foreach (char c in value) {
                if (c == '\\') { slashes++; continue; }
                if (c == '"') {
                    quoted.Append('\\', slashes * 2 + 1);
                    quoted.Append('"');
                } else {
                    quoted.Append('\\', slashes);
                    quoted.Append(c);
                }
                slashes = 0;
            }
            quoted.Append('\\', slashes * 2);
            quoted.Append('"');
            return quoted.ToString();
        }

        public static string CommandLine(string executable, string[] arguments) {
            var result = new StringBuilder(QuoteArgument(executable));
            foreach (string argument in arguments) {
                result.Append(' ');
                result.Append(QuoteArgument(argument));
            }
            if (result.Length >= 32767) throw new ArgumentException("Use Java's @argumentfile for a long command line.");
            return result.ToString();
        }

        public static string Validate() {
            int expectedStartupInfo = IntPtr.Size == 8 ? 104 : 68;
            int expectedExtended = IntPtr.Size == 8 ? 112 : 72;
            if (Marshal.SizeOf(typeof(STARTUPINFO)) != expectedStartupInfo ||
                Marshal.SizeOf(typeof(STARTUPINFOEX)) != expectedExtended)
                throw new InvalidOperationException("Incorrect native structure layout.");
            if (QuoteArgument("") != "\"\"" ||
                QuoteArgument("hello world") != "\"hello world\"" ||
                QuoteArgument("a\"b") != "\"a\\\"b\"" ||
                QuoteArgument("C:\\end\\") != "\"C:\\end\\\\\"")
                throw new InvalidOperationException("Windows argument quoting validation failed.");
            return "Native signatures compiled; structure layout and argument quoting validated. No desktop or JVM was created.";
        }

        static string UserObjectName(IntPtr handle) {
            var name = new StringBuilder(512);
            uint needed;
            if (handle == IntPtr.Zero || !GetUserObjectInformationW(handle, UOI_NAME,
                name, (uint)(name.Capacity * 2), out needed))
                throw NativeError("GetUserObjectInformationW(UOI_NAME)");
            return name.ToString();
        }

        static string InputDesktopName() {
            IntPtr input = OpenInputDesktop(0, false, DESKTOP_READOBJECTS);
            if (input == IntPtr.Zero) throw NativeError("OpenInputDesktop(read only)");
            try { return UserObjectName(input); }
            finally { CloseDesktop(input); }
        }

        static string JsonString(string value) {
            var result = new StringBuilder("\"");
            foreach (char c in value) {
                if (c == '"' || c == '\\') result.Append('\\').Append(c);
                else if (c < 32) result.Append("\\u").Append(((int)c).ToString("x4"));
                else result.Append(c);
            }
            return result.Append('"').ToString();
        }

        static void Status(string path, string state, uint pid, string desktop,
            string inputBefore, string inputAfter, string logPath, uint exitCode,
            int currentWindows, int maxWindows) {
            string data = "{\n  \"state\": " + JsonString(state) +
                ",\n  \"pid\": " + pid +
                ",\n  \"desktop\": " + JsonString(desktop) +
                ",\n  \"inputDesktopBefore\": " + JsonString(inputBefore) +
                ",\n  \"inputDesktopAfter\": " + JsonString(inputAfter) +
                ",\n  \"logPath\": " + JsonString(logPath) +
                ",\n  \"currentOwnedWindowsOnTargetDesktop\": " + currentWindows +
                ",\n  \"maxOwnedWindowsOnTargetDesktop\": " + maxWindows +
                ",\n  \"exitCode\": " + (state == "exited" ? exitCode.ToString() : "null") + "\n}\n";
            try { File.WriteAllText(path, data, new UTF8Encoding(false)); }
            catch (Exception error) { Console.Error.WriteLine("Status write failed: " + error.Message); }
        }

        public static uint LaunchAndWait(string javaPath, string[] arguments,
            string directory, string desktopName, string logPath, string statusPath,
            string[] environmentEntries) {
            Validate();
            if (!String.Equals(UserObjectName(GetProcessWindowStation()), "WinSta0",
                StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("Run as the interactive user's background process in WinSta0.");
            string inputBefore = InputDesktopName();
            if (String.Equals(desktopName, inputBefore, StringComparison.OrdinalIgnoreCase) ||
                String.Equals(desktopName, "Default", StringComparison.OrdinalIgnoreCase) ||
                String.Equals(desktopName, "Winlogon", StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("The target must be a distinct, non-input desktop.");

            IntPtr desktop = IntPtr.Zero, input = IntPtr.Zero, output = IntPtr.Zero;
            IntPtr attributeList = IntPtr.Zero, handleList = IntPtr.Zero;
            IntPtr environmentBlock = IntPtr.Zero;
            bool attributesInitialized = false;
            var process = new PROCESS_INFORMATION();
            try {
                // No DESKTOP_SWITCHDESKTOP access is requested. No input API is used.
                desktop = CreateDesktopW(desktopName, IntPtr.Zero, IntPtr.Zero, 0,
                    DESKTOP_READOBJECTS | DESKTOP_CREATEWINDOW | DESKTOP_CREATEMENU |
                    DESKTOP_ENUMERATE | DESKTOP_WRITEOBJECTS, IntPtr.Zero);
                if (desktop == IntPtr.Zero) throw NativeError("CreateDesktopW");
                if (!String.Equals(UserObjectName(desktop), desktopName, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException("Unexpected desktop name.");
                if (!String.Equals(InputDesktopName(), inputBefore, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException("The input desktop changed before launching; stop and inspect.");

                // CREATE_NEW avoids destroying a previous QA log.
                using (var stream = new FileStream(logPath, FileMode.CreateNew, FileAccess.Write,
                    FileShare.ReadWrite | FileShare.Delete)) { }
                var security = new SECURITY_ATTRIBUTES();
                security.nLength = (uint)Marshal.SizeOf(typeof(SECURITY_ATTRIBUTES));
                security.bInheritHandle = true;
                input = CreateFileW("NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    ref security, OPEN_EXISTING, 0, IntPtr.Zero);
                if (input == InvalidHandle) throw NativeError("CreateFileW(NUL)");
                output = CreateFileW(logPath, FILE_APPEND_DATA,
                    FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                    ref security, OPEN_EXISTING, 0, IntPtr.Zero);
                if (output == InvalidHandle) throw NativeError("CreateFileW(log)");

                IntPtr attributeBytes = IntPtr.Zero;
                InitializeProcThreadAttributeList(IntPtr.Zero, 1, 0, ref attributeBytes);
                if (attributeBytes == IntPtr.Zero) throw NativeError("Attribute list sizing");
                attributeList = Marshal.AllocHGlobal(attributeBytes);
                if (!InitializeProcThreadAttributeList(attributeList, 1, 0, ref attributeBytes))
                    throw NativeError("InitializeProcThreadAttributeList");
                attributesInitialized = true;
                handleList = Marshal.AllocHGlobal(IntPtr.Size * 2);
                Marshal.WriteIntPtr(handleList, 0, input);
                Marshal.WriteIntPtr(handleList, IntPtr.Size, output);
                if (!UpdateProcThreadAttribute(attributeList, 0, new IntPtr(0x00020002),
                    handleList, new IntPtr(IntPtr.Size * 2), IntPtr.Zero, IntPtr.Zero))
                    throw NativeError("UpdateProcThreadAttribute(HANDLE_LIST)");

                var startup = new STARTUPINFOEX();
                startup.StartupInfo.cb = (uint)Marshal.SizeOf(typeof(STARTUPINFOEX));
                startup.StartupInfo.lpDesktop = "WinSta0\\" + desktopName;
                startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES | STARTF_FORCEOFFFEEDBACK;
                startup.StartupInfo.hStdInput = input;
                startup.StartupInfo.hStdOutput = output;
                startup.StartupInfo.hStdError = output;
                startup.lpAttributeList = attributeList;
                uint processFlags = CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT;
                if (environmentEntries != null) {
                    Array.Sort(environmentEntries, StringComparer.OrdinalIgnoreCase);
                    environmentBlock = Marshal.StringToHGlobalUni(String.Join("\0", environmentEntries) + "\0\0");
                    processFlags |= CREATE_UNICODE_ENVIRONMENT;
                }
                if (!CreateProcessW(javaPath, new StringBuilder(CommandLine(javaPath, arguments)),
                    IntPtr.Zero, IntPtr.Zero, true, processFlags,
                    environmentBlock, directory, ref startup, out process))
                    throw NativeError("CreateProcessW(final Java JVM)");

                // hDesktop remains owned here for the full lifetime of the exact JVM.
                string inputAtLaunch;
                try { inputAtLaunch = InputDesktopName(); }
                catch { inputAtLaunch = "unavailable (locked or another desktop is active)"; }
                Status(statusPath, "running", process.dwProcessId, startup.StartupInfo.lpDesktop,
                    inputBefore, inputAtLaunch, logPath, 0, 0, 0);
                int currentWindows = 0, maxWindows = 0;
                while (true) {
                    uint wait = WaitForSingleObject(process.hProcess, 1000);
                    if (wait == WAIT_OBJECT_0) break;
                    if (wait != WAIT_TIMEOUT) throw NativeError("WaitForSingleObject");
                    int countedWindows = 0;
                    EnumWindowCallback callback = delegate(IntPtr window, IntPtr parameter) {
                        uint ownerPid;
                        GetWindowThreadProcessId(window, out ownerPid);
                        if (ownerPid == process.dwProcessId) countedWindows++;
                        return true;
                    };
                    bool enumerationSucceeded = EnumDesktopWindows(desktop, callback, IntPtr.Zero);
                    GC.KeepAlive(callback);
                    currentWindows = enumerationSucceeded ? countedWindows : -1;
                    if (currentWindows > maxWindows) {
                        maxWindows = currentWindows;
                        Status(statusPath, "running", process.dwProcessId, startup.StartupInfo.lpDesktop,
                            inputBefore, inputAtLaunch, logPath, 0, currentWindows, maxWindows);
                    }
                }
                uint exitCode;
                if (!GetExitCodeProcess(process.hProcess, out exitCode))
                    throw NativeError("GetExitCodeProcess");
                string inputAfter;
                try { inputAfter = InputDesktopName(); }
                catch { inputAfter = "unavailable (locked or another desktop is active)"; }
                Status(statusPath, "exited", process.dwProcessId, startup.StartupInfo.lpDesktop,
                    inputBefore, inputAfter, logPath, exitCode, currentWindows, maxWindows);
                return exitCode;
            }
            finally {
                if (process.hThread != IntPtr.Zero) CloseHandle(process.hThread);
                if (process.hProcess != IntPtr.Zero) CloseHandle(process.hProcess);
                if (attributesInitialized) DeleteProcThreadAttributeList(attributeList);
                if (attributeList != IntPtr.Zero) Marshal.FreeHGlobal(attributeList);
                if (handleList != IntPtr.Zero) Marshal.FreeHGlobal(handleList);
                if (environmentBlock != IntPtr.Zero) Marshal.FreeHGlobal(environmentBlock);
                if (input != IntPtr.Zero && input != InvalidHandle) CloseHandle(input);
                if (output != IntPtr.Zero && output != InvalidHandle) CloseHandle(output);
                if (desktop != IntPtr.Zero) CloseDesktop(desktop);
            }
        }
    }
}
'@
}

$validation = [ThaumcraftQa.IsolatedJava]::Validate()
if (-not $Launch) {
    [pscustomobject]@{
        Validation = $validation
        JavaPath = $resolvedJava
        Desktop = "WinSta0\$DesktopName"
        WorkingDirectory = $resolvedWorkingDirectory
        LogPath = $resolvedLog
        StatusPath = $resolvedStatus
        EnvironmentFile = $EnvironmentFile
        CommandLine = [ThaumcraftQa.IsolatedJava]::CommandLine($resolvedJava, $JavaArguments)
    }
    return
}
if ($JavaArguments.Count -eq 0) { throw 'A deliberate final JVM argument list is required for -Launch.' }
if (Test-Path -LiteralPath $resolvedLog) { throw "Use a new log path for this QA run: $resolvedLog" }
if (Test-Path -LiteralPath $resolvedStatus) { throw "Use a new status path for this QA run: $resolvedStatus" }
$exitCode = [ThaumcraftQa.IsolatedJava]::LaunchAndWait($resolvedJava, $JavaArguments,
    $resolvedWorkingDirectory, $DesktopName, $resolvedLog, $resolvedStatus, $environmentEntries)
exit [int]$exitCode
