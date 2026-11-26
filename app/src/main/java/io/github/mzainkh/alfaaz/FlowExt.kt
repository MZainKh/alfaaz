package io.github.mzainkh.alfaaz

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/** At most one value per [periodMs]. Intermediate values are dropped; the latest one always arrives. */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMs)
}
