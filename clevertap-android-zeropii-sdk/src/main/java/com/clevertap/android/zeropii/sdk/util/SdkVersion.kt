package com.clevertap.android.zeropii.sdk.util

import java.util.Collections
import java.util.WeakHashMap

/**
 * The SDK name and version reported to CleverTap Core SDK.
 */
internal object SdkVersion {
    const val NAME = "ZeroPii"

    // major * 10000 + minor * 100 + patch, the format CleverTap Core SDK expects. 10000 is 1.0.0.
    // Update together with clevertap-zeropii-sdk in gradle/libs.versions.toml on every release.
    const val VERSION: Int = 10000
}

/**
 * Registers the ZeroPii SDK name and version with CleverTap Core SDK through
 * `CleverTapAPI.setCustomSdkVersion`, so it is sent in the `af` block of Core's requests.
 *
 * Core is looked up through reflection, so ZeroPii has no compile-time dependency on it and
 * keeps working when the app does not include Core. Core keeps custom SDK versions per
 * instance, so every instance in `CleverTapAPI.getInstances()` is registered. Instances
 * created later are picked up by the next `ZeroPiiSDK.initialize` call.
 */
internal class CoreSdkVersionRegistrar(
    private val logger: ZeroPiiLogger? = null,
    private val coreClassName: String = CORE_CLASS_NAME,
    private val sdkName: String = SdkVersion.NAME,
    private val version: Int = SdkVersion.VERSION
) {
    companion object {
        const val CORE_CLASS_NAME = "com.clevertap.android.sdk.CleverTapAPI"
        private const val LOG_SUFFIX = "SdkVersion"
    }

    // Weak keys so a released Core instance is not kept alive by ZeroPii.
    private val registeredInstances: MutableSet<Any> =
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    /**
     * Registers the version with every Core instance that does not have it yet.
     * Never throws.
     *
     * @return the number of instances registered by this call
     */
    @Synchronized
    fun registerWithAllInstances(): Int {
        return try {
            val coreClass = try {
                Class.forName(coreClassName)
            } catch (e: ClassNotFoundException) {
                logger?.d(LOG_SUFFIX, "CleverTap Core SDK not found, skipping SDK version registration")
                return 0
            }
            val setCustomSdkVersion = try {
                coreClass.getMethod("setCustomSdkVersion", String::class.java, Int::class.javaPrimitiveType)
            } catch (e: NoSuchMethodException) {
                logger?.d(LOG_SUFFIX, "CleverTap Core SDK does not support setCustomSdkVersion, skipping SDK version registration")
                return 0
            }
            val instances = coreClass.getMethod("getInstances").invoke(null) as? Map<*, *>
            if (instances.isNullOrEmpty()) {
                logger?.d(LOG_SUFFIX, "No CleverTap instance yet, skipping SDK version registration")
                return 0
            }

            var count = 0
            for (instance in instances.values.toList()) {
                if (instance == null || instance in registeredInstances) continue
                setCustomSdkVersion.invoke(instance, sdkName, version)
                registeredInstances.add(instance)
                count++
            }
            if (count > 0) {
                logger?.i(LOG_SUFFIX, "Registered $sdkName $version with $count CleverTap instance(s)")
            }
            count
        } catch (t: Throwable) {
            logger?.d(LOG_SUFFIX, "SDK version registration failed", t)
            0
        }
    }
}
