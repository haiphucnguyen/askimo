package io.askimo.core.secrets

interface SecretResolver {
    fun resolve(ref: SecretRef): CharArray
}

class DefaultSecretResolver(
    private val fs: java.nio.file.FileSystem = java.nio.file.FileSystems.getDefault(),
    private val keychain: KeychainProvider? = null,
) : SecretResolver {
    override fun resolve(ref: SecretRef): CharArray = when (ref) {
        is SecretRef.Inline  -> ref.value.toCharArray()
        is SecretRef.EnvVar  -> (System.getenv(ref.name) ?: error("Env var ${ref.name} not set")).toCharArray()
        is SecretRef.FilePath -> java.nio.file.Files.readString(fs.getPath(ref.path)).trim().toCharArray()
        is SecretRef.Keychain -> {
            val kc = keychain ?: error("Keychain provider not available on this platform")
            kc.getPassword(service = ref.service, account = ref.account)
        }
    }
}