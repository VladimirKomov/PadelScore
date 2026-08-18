@{
    Target = '192.168.101.17:5555'
    PackageName = 'com.vekom.padelprobe'
    ActivityName = '.MainActivity'

    Hdc = 'C:\Users\<user>\HuaweiSdk31\hmscore\3.1.0\toolchains\HdcExternal.exe'
    JavaHome = 'C:\Program Files\Huawei\DevEco Studio\jbr'
    Npm = 'C:\Program Files\nodejs\npm.cmd'
    DevEcoNode = 'C:\Program Files\Huawei\DevEco Studio\tools\node\node.exe'

    AndroidJar = '.local-tools\android-sdk\platforms\android-12\android.jar'
    BuildTools = '.local-tools\android-sdk\build-tools\android-14'
    D8Jar = '.local-tools\r8-9.4.14.jar'

    KeyStore = 'signing\compat-debug.keystore'
    KeyAlias = 'androiddebugkey'
    KeyStorePassword = 'replace-with-local-password'
    KeyPassword = 'replace-with-local-password'
    ExpectedSignerSha256 = '88F9D8A07115FD549252BC29F7B15D11493DFF018C6315F3D8888AA3507E7A9C'

    HapSignTool = 'C:\Users\<user>\HuaweiSdk31\openharmony\8\toolchains\lib\hap-sign-tool.jar'
    HapKeyStore = 'C:\Users\<user>\HuaweiSdk31\openharmony\8\toolchains\lib\OpenHarmony.p12'
    HapKeyAlias = 'openharmony application release'
    HapKeyStorePassword = 'replace-with-local-password'
    HapKeyPassword = 'replace-with-local-password'
    HapAppCert = 'signing\OpenHarmonyApplicationChain.cer'
    HapProfile = 'signing\PadelScoreDebugProfile.p7b'
}
