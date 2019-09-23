package com.hoc.comicapp.data

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.withTransaction
import com.hoc.comicapp.data.local.AppDatabase
import com.hoc.comicapp.data.local.dao.ChapterDao
import com.hoc.comicapp.data.local.dao.ComicDao
import com.hoc.comicapp.data.local.entities.ChapterEntity
import com.hoc.comicapp.data.local.entities.ComicAndChapters
import com.hoc.comicapp.data.local.entities.ComicEntity
import com.hoc.comicapp.data.remote.ComicApiService
import com.hoc.comicapp.domain.models.*
import com.hoc.comicapp.domain.repository.DownloadComicsRepository
import com.hoc.comicapp.domain.thread.CoroutinesDispatcherProvider
import com.hoc.comicapp.domain.thread.RxSchedulerProvider
import com.hoc.comicapp.utils.Either
import com.hoc.comicapp.utils.copyTo
import com.hoc.comicapp.utils.left
import com.hoc.comicapp.utils.right
import io.reactivex.Observable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File
import java.util.*

class DownloadComicsRepositoryImpl(
  private val comicApiService: ComicApiService,
  private val application: Application,
  private val dispatcherProvider: CoroutinesDispatcherProvider,
  private val comicDao: ComicDao,
  private val chapterDao: ChapterDao,
  private val appDatabase: AppDatabase,
  private val rxSchedulerProvider: RxSchedulerProvider,
  private val retrofit: Retrofit
) : DownloadComicsRepository {
  override suspend fun deleteDownloadedChapter(chapter: DownloadedChapter): Either<ComicAppError, Unit> {
    return runCatching {
      withContext(dispatcherProvider.io) {
        chapterDao.delete(Mapper.domainToEntity(chapter))

        val chaptersCount = chapterDao.getCountByComicLink(chapter.comicLink).firstOrNull() ?: 0
        if (chaptersCount == 0) {
          comicDao.delete(
            ComicEntity(
              comicLink = chapter.comicLink,
              view = "",
              categories = emptyList(),
              authors = emptyList(),
              thumbnail = "",
              title = "",
              lastUpdated = "",
              shortenedContent = ""
            )
          )
        }

        chapter
          .images
          .map { File(application.filesDir, it) }
          .all(File::delete)
      }
    }.fold(
      {
        if (it) {
          Unit.right()
        } else {
          LocalStorageError.DeleteFileError.left()
        }
      },
      { it.toError(retrofit).left() }
    )
  }

  override fun downloadedChapters(): LiveData<List<DownloadedChapter>> {
    return chapterDao.getAllChapters().map { chapters ->
      chapters.map { Mapper.entityToDomainModel(it) }
    }
  }

  override fun downloadedComics(): Observable<Either<ComicAppError, List<DownloadedComic>>> {
    return chapterDao
      .getComicAndChapters()
      .map<Either<ComicAppError, List<DownloadedComic>>> { list ->
        list.map { item ->
          Mapper.entityToDomainModel(
            ComicAndChapters().also { copied ->
              copied.comic = item.comic
              copied.chapters = item.chapters
                .sortedByDescending { it.downloadedAt }
                .take(3)
            }
          )
        }.right()
      }
      .onErrorReturn { t: Throwable -> t.toError(retrofit).left() }
      .subscribeOn(rxSchedulerProvider.io)
  }

  @ExperimentalCoroutinesApi
  override fun downloadChapter(chapterLink: String): Flow<Int> {
    return flow {
      Timber.d("$tag Begin")

      emit(0)

      val chapterDetail = comicApiService.getChapterDetail(chapterLink)
      val comicNameEscaped = chapterDetail.comicName.escapeFileName()
      val chapterNameEscaped = chapterDetail.chapterName.escapeFileName()
      val totalImageSize = chapterDetail.images.size
      Timber.d("$tag Images.size = $totalImageSize")

      emit(10)

      var imagePaths = emptyList<String>()
      downloadAndSaveImages(
        images = chapterDetail.images,
        comicName = comicNameEscaped,
        chapterName = chapterNameEscaped
      ).collect {
        val progress = (10 + (it.size.toFloat() / totalImageSize) * 80).toInt()
        emit(progress)
        imagePaths = it
      }

      val comicDetail = comicApiService.getComicDetail(chapterDetail.comicLink)
      val thumbnailPath = downloadComicThumbnail(
        thumbnailUrl = comicDetail.thumbnail,
        comicName = comicNameEscaped
      )

      appDatabase.withTransaction {
        comicDao.upsert(
          ComicEntity(
            authors = comicDetail.authors.map {
              ComicEntity.Author(
                link = it.link,
                name = it.name
              )
            },
            categories = comicDetail.categories.map {
              ComicEntity.Category(
                link = it.link,
                name = it.name
              )
            },
            lastUpdated = comicDetail.lastUpdated,
            comicLink = comicDetail.link,
            shortenedContent = comicDetail.shortenedContent,
            thumbnail = thumbnailPath,
            title = comicDetail.title,
            view = comicDetail.view
          )
        )

        val currentIndex = comicDetail.chapters.indexOfFirst { it.chapterLink == chapterLink }
        val currentChapter = comicDetail.chapters[currentIndex]

        chapterDao.upsert(
          ChapterEntity(
            chapterLink = chapterLink,
            view = currentChapter.view,
            comicLink = comicDetail.link,
            images = imagePaths,
            time = currentChapter.time,
            chapterName = chapterDetail.chapterName,
            order = comicDetail.chapters.size - currentIndex,
            downloadedAt = Date()
          )
        )
      }

      emit(100)

      Timber.d("$tag Images = $imagePaths")
    }.flowOn(dispatcherProvider.io)
  }

  private suspend fun downloadComicThumbnail(thumbnailUrl: String, comicName: String): String {
    return comicApiService.downloadFile(thumbnailUrl).use { responseBody ->
      val imagePath = listOf(
        "images",
        comicName,
        "thumbnail.png"
      ).joinToString(File.separator)

      responseBody.byteStream().copyTo(
        File(
          application.filesDir.path,
          imagePath
        ),
        overwrite = true
      )

      imagePath
    }
  }

  /**
   * @return a [Flow] emit downloaded image paths
   */
  private fun downloadAndSaveImages(
    images: List<String>,
    comicName: String,
    chapterName: String
  ): Flow<List<String>> {
    return flow {
      val imagePaths = mutableListOf<String>()

      for ((index, imageUrl) in images.withIndex()) {
        Timber.d("$tag Begin $index $imageUrl")

        comicApiService
          .downloadFile(imageUrl)
          .use { responseBody ->
            val imagePath = listOf(
              "images",
              comicName,
              chapterName,
              "images_$index.png"
            ).joinToString(File.separator)

            responseBody.byteStream().copyTo(
              File(
                application.filesDir.path,
                imagePath
              ),
              overwrite = true
            )

            imagePaths += imagePath

            emit(imagePaths)
            Timber.d("$tag Done $index $imageUrl -> $imagePath")
          }
      }

      emit(imagePaths)
    }
  }

  private fun String.escapeFileName(): String {
    return replace(
      "[^a-zA-Z0-9.\\-]".toRegex(),
      replacement = "_"
    )
  }

  private companion object {
    const val tag = "[DOWNLOAD_COMIC_REPO]"
  }
}