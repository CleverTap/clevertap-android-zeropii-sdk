package com.clevertap.android.zeropii.sdk.util

import com.clevertap.android.zeropii.sdk.ZeroPiiSDK
import com.clevertap.android.zeropii.sdk.auth.AccessTokenCallback
import com.clevertap.android.zeropii.sdk.auth.AccessTokenInfo
import com.clevertap.android.zeropii.sdk.auth.AccessTokenProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stands in for com.clevertap.android.sdk.CleverTapAPI: same static getInstances()
 * and instance setCustomSdkVersion(String, int), found by class name through reflection.
 */
class FakeCleverTapAPI {
    val customSdkVersions = HashMap<String, Int>()
    var setCallCount = 0

    fun setCustomSdkVersion(customSdkName: String, customSdkVersion: Int) {
        setCallCount++
        customSdkVersions[customSdkName] = customSdkVersion
    }

    companion object {
        @JvmStatic
        var instancesMap: HashMap<String, FakeCleverTapAPI>? = null

        @JvmStatic
        fun getInstances(): HashMap<String, FakeCleverTapAPI>? = instancesMap
    }
}

/** Core without setCustomSdkVersion, like an older Core release. */
class FakeOldCleverTapAPI {
    companion object {
        @JvmStatic
        fun getInstances(): HashMap<String, FakeOldCleverTapAPI> = hashMapOf("acc" to FakeOldCleverTapAPI())
    }
}

/** Core whose getInstances() throws. */
class FakeThrowingCleverTapAPI {
    fun setCustomSdkVersion(customSdkName: String, customSdkVersion: Int) {}

    companion object {
        @JvmStatic
        fun getInstances(): HashMap<String, FakeThrowingCleverTapAPI> = throw IllegalStateException("boom")
    }
}

class SdkVersionTest {

    private val fakeCore = FakeCleverTapAPI::class.java.name

    @Before
    fun setUp() {
        FakeCleverTapAPI.instancesMap = null
        resetZeroPiiSDKInstance()
    }

    @Test
    fun sdkVersion_constants() {
        assertEquals("ZeroPii", SdkVersion.NAME)
        assertEquals(10000, SdkVersion.VERSION)
    }

    // ========================================
    // Registration
    // ========================================

    @Test
    fun register_setsVersionOnEveryInstance() {
        val first = FakeCleverTapAPI()
        val second = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc1" to first, "acc2" to second)
        val registrar = CoreSdkVersionRegistrar(coreClassName = fakeCore, version = 10203)

        assertEquals(2, registrar.registerWithAllInstances())
        assertEquals(mapOf("ZeroPii" to 10203), first.customSdkVersions)
        assertEquals(mapOf("ZeroPii" to 10203), second.customSdkVersions)
    }

    @Test
    fun register_usesDefaultNameAndVersion() {
        val instance = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc" to instance)

        CoreSdkVersionRegistrar(coreClassName = fakeCore).registerWithAllInstances()

        assertEquals(SdkVersion.VERSION, instance.customSdkVersions[SdkVersion.NAME])
    }

    @Test
    fun register_onlyCallsEachInstanceOnce() {
        val instance = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc" to instance)
        val registrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)

        registrar.registerWithAllInstances()
        assertEquals(0, registrar.registerWithAllInstances())
        assertEquals(0, registrar.registerWithAllInstances())

        assertEquals(1, instance.setCallCount)
    }

    @Test
    fun register_picksUpInstancesCreatedLater() {
        val registrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)
        assertEquals(0, registrar.registerWithAllInstances())

        val first = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc1" to first)
        assertEquals(1, registrar.registerWithAllInstances())

        val second = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap!!["acc2"] = second
        assertEquals(1, registrar.registerWithAllInstances())

        assertEquals(1, first.setCallCount)
        assertEquals(1, second.setCallCount)
    }

    @Test
    fun register_handlesEmptyInstances() {
        FakeCleverTapAPI.instancesMap = hashMapOf()

        assertEquals(0, CoreSdkVersionRegistrar(coreClassName = fakeCore).registerWithAllInstances())
    }

    @Test
    fun register_skipsWhenCoreIsMissing() {
        val registrar = CoreSdkVersionRegistrar(coreClassName = "com.example.DoesNotExist")

        assertEquals(0, registrar.registerWithAllInstances())
    }

    @Test
    fun register_skipsWhenCoreIsTooOld() {
        val registrar = CoreSdkVersionRegistrar(coreClassName = FakeOldCleverTapAPI::class.java.name)

        assertEquals(0, registrar.registerWithAllInstances())
    }

    @Test
    fun register_doesNotThrowWhenCoreThrows() {
        val registrar = CoreSdkVersionRegistrar(coreClassName = FakeThrowingCleverTapAPI::class.java.name)

        assertEquals(0, registrar.registerWithAllInstances())
    }

    @Test
    fun register_withoutCoreOnClasspath_defaultClassNameIsMissing() {
        // The unit test classpath does not include CleverTap Core, so the default lookup must fail gracefully.
        assertEquals(0, CoreSdkVersionRegistrar().registerWithAllInstances())
    }

    @Test
    fun register_isSafeFromManyThreads() {
        val instance = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc" to instance)
        val registrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)
        val executor = Executors.newFixedThreadPool(8)

        repeat(50) { executor.submit { registrar.registerWithAllInstances() } }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, instance.setCallCount)
    }

    // ========================================
    // ZeroPiiSDK.initialize
    // ========================================

    // Each test swaps in a registrar pointing at FakeCleverTapAPI and calls initialize again,
    // which is the path every later initialize call takes.

    private val tokenProvider = object : AccessTokenProvider {
        override fun fetchToken(callback: AccessTokenCallback) {
            callback.onSuccess(AccessTokenInfo("test-token", 3600))
        }
    }

    private fun initializeSDK(): ZeroPiiSDK = ZeroPiiSDK.initialize(tokenProvider, "https://api.test.com/")

    private fun resetZeroPiiSDKInstance() {
        val instanceField = ZeroPiiSDK::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }

    @After
    fun tearDown() {
        resetZeroPiiSDKInstance()
    }

    @Test
    fun initialize_createsRegistrar() {
        assertNotNull(initializeSDK().coreSdkVersionRegistrar)
    }

    @Test
    fun initialize_registersVersionWithCore() {
        val instance = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc" to instance)
        val sdk = initializeSDK()
        sdk.coreSdkVersionRegistrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)

        initializeSDK()

        assertEquals(mapOf("ZeroPii" to 10000), instance.customSdkVersions)
    }

    @Test
    fun initialize_laterCallRegistersInstancesCreatedSince() {
        val sdk = initializeSDK()
        sdk.coreSdkVersionRegistrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)

        initializeSDK()

        val first = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc1" to first)
        initializeSDK()
        assertEquals(mapOf("ZeroPii" to 10000), first.customSdkVersions)

        val second = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap!!["acc2"] = second
        assertTrue(second.customSdkVersions.isEmpty())
        initializeSDK()
        assertEquals(mapOf("ZeroPii" to 10000), second.customSdkVersions)
        assertEquals(1, first.setCallCount)
    }

    @Test
    fun initialize_repeatedCallsRegisterEachInstanceOnce() {
        val instance = FakeCleverTapAPI()
        FakeCleverTapAPI.instancesMap = hashMapOf("acc" to instance)
        val sdk = initializeSDK()
        sdk.coreSdkVersionRegistrar = CoreSdkVersionRegistrar(coreClassName = fakeCore)

        initializeSDK()
        initializeSDK()
        initializeSDK()

        assertEquals(1, instance.setCallCount)
    }

    @Test
    fun initialize_withoutCoreOnClasspath_doesNotThrow() {
        val sdk = initializeSDK()
        sdk.coreSdkVersionRegistrar = CoreSdkVersionRegistrar()

        val again = initializeSDK()

        assertSame(sdk, again)
        assertEquals(0, sdk.coreSdkVersionRegistrar.registerWithAllInstances())
    }
}
