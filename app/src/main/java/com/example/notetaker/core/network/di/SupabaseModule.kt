package com.example.notetaker.core.network.di

import com.example.notetaker.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.resumable.MemoryResumableCache
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    // TODO: move the url and key to secrets
    @Provides
    @Singleton
    fun providesSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://siohqbfckereskgkkrpc.supabase.co",
            supabaseKey = BuildConfig.SUPABASE_API
        ) {
            install(Storage) {
                resumable {
                    cache = MemoryResumableCache()
                }
            }
        }
    }
}
