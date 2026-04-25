//package com.example.notetaker.core.network.di
//
//import android.content.Context
//import com.cloudinary.android.MediaManager
//import com.cloudinary.android.callback.ErrorInfo
//import com.cloudinary.android.callback.ObjectCallback
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.android.qualifiers.ApplicationContext
//import dagger.hilt.components.SingletonComponent
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import java.util.UUID
//import kotlin.coroutines.resume
//import kotlin.coroutines.resumeWithException
//import kotlin.coroutines.suspendCoroutine
//import javax.inject.Singleton
//import android.util.Log
//
//@Module
//@InstallIn(SingletonComponent::class)
//object CloudinaryModule {
//
//    private const val TAG = "CloudinaryModule"
//
//    @Provides
//    @Singleton
//    fun provideCloudinaryMediaManager(@ApplicationContext context: Context): MediaManager {
//        // Initialize MediaManager. This configuration should ideally come from BuildConfig or local.properties.
//        // For demonstration, using placeholders. MediaManager.init should be called once, e.g., in Application.onCreate().
//        // Here, we simulate obtaining an already initialized instance or initialize it if not.
//
//        // It's more common to initialize MediaManager once globally in the Application class.
//        // If it's already initialized, we can get it. If not, we'd initialize it here, but this might have side effects.
//        // For dependency injection, it's better if MediaManager is managed by Hilt or provided by an Application-level setup.
//
//        // Assuming MediaManager.init is called elsewhere (e.g., Application class).
//        // If not, it would look like this:
//        // MediaManager.init(context).apply {
//        //     setCloudinaryUrl("cloudinary://API_KEY:API_SECRET@YOUR_CLOUD_NAME") // Replace with actual URL or config
//        // }
//        // For DI, it's better to have a provider that returns the singleton instance.
//
//        // Let's assume MediaManager.get() returns the singleton instance.
//        // If MediaManager needs explicit init here, it should be done carefully to avoid re-initialization.
//        // A safer pattern might be to provide a factory or a direct instance if initialized elsewhere.
//
//        // For now, simulate getting an instance.
//        // If MediaManager.init() has been called, MediaManager.get() can be used.
//        // If MediaManager is not initialized, this will throw an exception.
//        // A robust solution would involve checking initialization status or ensuring it's done in Application.
//
//        // For demonstration, let's ensure initialization if not already done and return the instance.
//        // IMPORTANT: Replace placeholders with actual values from BuildConfig or local.properties.
//        val cloudName = "your_cloud_name" // BuildConfig.CLOUDINARY_CLOUD_NAME
//        val apiKey = "your_api_key" // BuildConfig.CLOUDINARY_API_KEY
//        val apiSecret = "your_api_secret" // BuildConfig.CLOUDINARY_API_SECRET
//        val uploadPreset = "YOUR_UNSIGNED_UPLOAD_PRESET" // BuildConfig.CLOUDINARY_UPLOAD_PRESET
//
////        if (!MediaManager.isInitialized) {
////            Log.d(TAG, "MediaManager not initialized, initializing now.")
////            MediaManager.init(context).apply {
////                // Use setCloudinaryUrl if API_KEY and API_SECRET are available, or setCloudinaryConfig
////                 setCloudinaryUrl("cloudinary://$apiKey:$apiSecret@$cloudName")
////                 // If using unsigned presets, this is also configured here or during upload call.
////            }
////        } else {
////            Log.d(TAG, "MediaManager already initialized.")
////        }
//
//        return MediaManager.get() // Returns the singleton instance
//    }
//}
