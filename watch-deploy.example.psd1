@{
    Target = '<watch-ip>:5555'
    PackageName = 'com.vekom.padelprobe'
    ActivityName = '.MainActivity'

    Hdc = '%USERPROFILE%\HuaweiSdk31\hmscore\3.1.0\toolchains\HdcExternal.exe'
    JavaHome = '%ProgramFiles%\Huawei\DevEco Studio\jbr'
    Npm = '%ProgramFiles%\nodejs\npm.cmd'
    DevEcoNode = '%ProgramFiles%\Huawei\DevEco Studio\tools\node\node.exe'

    AndroidJar = '.local-tools\android-sdk\platforms\android-12\android.jar'
    BuildTools = '.local-tools\android-sdk\build-tools\android-14'
    D8Jar = '.local-tools\r8-9.4.14.jar'

    KeyStore = 'signing\compat-debug.keystore'
    KeyAlias = 'androiddebugkey'
    KeyStorePassword = 'replace-with-local-password'
    KeyPassword = 'replace-with-local-password'
    ExpectedSignerSha256 = 'replace-with-certificate-sha256'

    HapSignTool = '%USERPROFILE%\HuaweiSdk31\openharmony\8\toolchains\lib\hap-sign-tool.jar'
    HapKeyStore = '%USERPROFILE%\HuaweiSdk31\openharmony\8\toolchains\lib\OpenHarmony.p12'
    HapKeyAlias = 'openharmony application release'
    HapKeyStorePassword = 'replace-with-local-password'
    HapKeyPassword = 'replace-with-local-password'
    HapAppCert = 'signing\OpenHarmonyApplicationChain.cer'
    HapProfile = 'signing\PadelScoreDebugProfile.p7b'
}
