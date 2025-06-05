package com.ght.androidchat

import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.net.nsd.NsdManager
import android.util.Log

class NsdHelper(
    private val context: Context,
    private val serviceResolvedCallback: (NsdServiceInfo) -> Unit
) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceName: String? = null

    companion object {
        const val SERVICE_TYPE = "_ghchat._tcp."
        private const val TAG = "NsdHelper"
    }

    fun registerService(port: Int, serviceNamePrefix: String) {
        if (registrationListener != null) {
            Log.d(TAG, "Service already registered or registration in progress. Unregister first.")
            // Optionally, unregister previous service first
            // unregisterService()
            // return
        }

        serviceName = "$serviceNamePrefix${System.currentTimeMillis()}" // Make service name unique

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                this@NsdHelper.serviceName = nsdServiceInfo.serviceName // Update with actual name if changed by system
                Log.d(TAG, "Service registered: ${nsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed. Error code: $errorCode")
                this@NsdHelper.registrationListener = null // Allow re-registration
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${arg0.serviceName}")
                this@NsdHelper.registrationListener = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed. Error code: $errorCode")
                // Still set listener to null to allow retrying registration
                this@NsdHelper.registrationListener = null
            }
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun discoverServices() {
        if (discoveryListener != null) {
            Log.d(TAG, "Discovery already active. Stop first.")
            // return
        }
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}, type: ${service.serviceType}")
                if (service.serviceType == SERVICE_TYPE) {
                    // Check if it's not our own service if we also registered one
                    if (service.serviceName == serviceName) {
                        Log.d(TAG, "Skipping own service: ${service.serviceName}")
                    } else {
                         nsdManager.resolveService(service, initializeResolveListener())
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "Service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
                this@NsdHelper.discoveryListener = null // Allow rediscovery
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start. Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
                this@NsdHelper.discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop. Error code: $errorCode")
                // Still set listener to null
                this@NsdHelper.discoveryListener = null
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun initializeResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}. Error code: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service resolved: ${serviceInfo.serviceName}, Host: ${serviceInfo.host}, Port: ${serviceInfo.port}")
                serviceResolvedCallback(serviceInfo)
            }
        }
    }

    fun stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery: ${e.message}", e)
            }
        }
        discoveryListener = null
    }

    fun unregisterService() {
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering service: ${e.message}", e)
            }
        }
        registrationListener = null // Clear listener regardless of success to allow re-registration
        serviceName = null
    }

    // Call this in Activity's onDestroy or when appropriate
    fun tearDown() {
        unregisterService()
        stopDiscovery()
    }
}
