package com.hoc.comicapp.ui.home

import com.hoc.comicapp.domain.repository.ComicRepository
import com.hoc.comicapp.domain.thread.CoroutinesDispatchersProvider
import com.hoc.comicapp.utils.Left
import com.hoc.comicapp.utils.fold
import com.hoc.comicapp.utils.getOrNull
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.cast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.rx3.rxObservable

@OptIn(ExperimentalCoroutinesApi::class)
class HomeInteractorImpl(
  private val comicRepository: ComicRepository,
  private val dispatchersProvider: CoroutinesDispatchersProvider,
) : HomeInteractor {
  /**
   * Newest list
   */
  override fun newestComics(): Observable<HomePartialChange> {
    return rxObservable(dispatchersProvider.main) {
      /**
       * Send loading
       */
      this.send(HomePartialChange.NewestHomePartialChange.Loading)

      /**
       * Get newest list
       */
      val newestResult = comicRepository.getNewestComics()

      /**
       * Send success change
       */
      newestResult
        .getOrNull()
        .orEmpty()
        .let { HomePartialChange.NewestHomePartialChange.Data(it) }
        .let { this.send(it) }

      /**
       * Send error change
       */
      if (newestResult is Left) {
        newestResult
          .value
          .let { HomePartialChange.NewestHomePartialChange.Error(it) }
          .let { this.send(it) }
      }
    }.cast()
  }

  /**
   * Most viewed list
   */
  override fun mostViewedComics(): Observable<HomePartialChange> {
    return rxObservable(dispatchersProvider.main) {
      /**
       * Send loading
       */
      this.send(HomePartialChange.MostViewedHomePartialChange.Loading)

      /**
       * Get most viewed list
       */
      val mostViewedResult = comicRepository.getMostViewedComics()

      /**
       * Send success change
       */
      mostViewedResult
        .getOrNull()
        .orEmpty()
        .let { HomePartialChange.MostViewedHomePartialChange.Data(it) }
        .let { this.send(it) }

      /**
       * Send error change
       */
      if (mostViewedResult is Left) {
        mostViewedResult
          .value
          .let { HomePartialChange.MostViewedHomePartialChange.Error(it) }
          .let { this.send(it) }
      }
    }.cast()
  }

  /**
   * Updated list
   */
  override fun updatedComics(
    page: Int,
  ): Observable<HomePartialChange> {
    return rxObservable(dispatchersProvider.main) {
      /**
       * Send loading
       */
      this.send(HomePartialChange.UpdatedPartialChange.Loading)

      /**
       * Get updated comics list
       */
      val updatedResult = comicRepository.getUpdatedComics(page = page)

      /**
       * Send success change or error change
       */
      updatedResult.fold(
        { HomePartialChange.UpdatedPartialChange.Error(it) },
        { HomePartialChange.UpdatedPartialChange.Data(it) }
      ).let { send(it) }
    }.cast()
  }

  override fun refreshAll(): Observable<HomePartialChange> {
    return rxObservable(dispatchersProvider.main) {
      send(HomePartialChange.RefreshPartialChange.Loading)

      comicRepository
        .refreshAll()
        .fold(
          left = { HomePartialChange.RefreshPartialChange.RefreshFailure(it) },
          right = { (newest, mostViewed, updated) ->
            HomePartialChange.RefreshPartialChange.RefreshSuccess(
              newestComics = newest,
              mostViewedComics = mostViewed,
              updatedComics = updated
            )
          }
        )
        .let { send(it) }
    }
  }
}
