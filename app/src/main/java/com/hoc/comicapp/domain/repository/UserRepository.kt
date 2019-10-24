package com.hoc.comicapp.domain.repository

import android.net.Uri
import com.hoc.comicapp.domain.models.ComicAppError
import com.hoc.comicapp.domain.models.User
import com.hoc.comicapp.utils.Either
import io.reactivex.Observable

interface UserRepository {
  suspend fun login(email: String, password: String): Either<ComicAppError, Unit>

  suspend fun signOut(): Either<ComicAppError, Unit>

  suspend fun register(
    email: String,
    password: String,
    fullName: String,
    avatar: Uri?
  ): Either<ComicAppError, Unit>

  fun userObservable(): Observable<Either<ComicAppError, User?>>
}