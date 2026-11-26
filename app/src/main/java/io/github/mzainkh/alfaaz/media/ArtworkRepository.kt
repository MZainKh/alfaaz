@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** Art to prepare for [trackKey]. Requests with the same [identity] show the same image. */
class ArtRequest<S>(val trackKey: String, val identity: String, val source: S)

/**
 * Prepares each new image once. While a new one is prepared the previous result stays, so readers
 * check its track before showing it. Does nothing unless [artwork] is observed.
 */
class ArtworkRepository<S, A : Any>(
    requests: Flow<ArtRequest<S>?>,
    scope: CoroutineScope,
    private val process: suspend (ArtRequest<S>) -> A?,
) {
    private class Done<A>(val identity: String, val result: A?)

    @Volatile
    private var last: Done<A>? = null

    val artwork: StateFlow<A?> = requests
        .distinctUntilChanged { old, new -> old?.identity == new?.identity }
        .transformLatest { request ->
            val done = last
            when {
                request == null -> emit(null)
                done?.identity == request.identity -> emit(done.result)
                else -> {
                    val result = try {
                        process(request)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    last = Done(request.identity, result)
                    emit(result)
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
