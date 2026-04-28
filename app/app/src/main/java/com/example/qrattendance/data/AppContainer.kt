package com.example.qrattendance.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.qrattendance.data.repository.RemoteAuthRepository
import com.example.qrattendance.data.repository.RemoteCoursesRepository
import com.example.qrattendance.data.repository.RemoteLeaveRepository
import com.example.qrattendance.data.repository.RemoteProfileRepository
import com.example.qrattendance.data.repository.RemoteRecordsRepository
import com.example.qrattendance.data.repository.RemoteScanRepository
import com.example.qrattendance.data.repository.RemoteSessionsRepository
import com.example.qrattendance.ui.auth.LoginViewModel
import com.example.qrattendance.ui.home.HomeViewModel
import com.example.qrattendance.ui.leave.LeaveViewModel
import com.example.qrattendance.ui.profile.ProfileViewModel
import com.example.qrattendance.ui.records.RecordsViewModel
import com.example.qrattendance.ui.scan.ScanViewModel
import com.example.qrattendance.ui.sessions.SessionsViewModel
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
  val sessionStore: SessionStore = EncryptedSharedPreferencesSessionStore(context.applicationContext)
  private val okHttpClient = OkHttpClient()
  val apiClient = AttendanceApiClient(client = okHttpClient, onUnauthorized = { sessionStore.clear() })

  val authRepository = RemoteAuthRepository(apiClient, sessionStore)
  val coursesRepository = RemoteCoursesRepository(apiClient, sessionStore)
  val sessionsRepository = RemoteSessionsRepository(apiClient, sessionStore)
  val recordsRepository = RemoteRecordsRepository(apiClient, sessionStore)
  val leaveRepository = RemoteLeaveRepository(apiClient, sessionStore)
  val profileRepository = RemoteProfileRepository(apiClient, sessionStore)
  val scanRepository = RemoteScanRepository(apiClient, sessionStore)

  val loginViewModelFactory = viewModelFactory { LoginViewModel(authRepository) }
  val homeViewModelFactory = viewModelFactory { HomeViewModel(sessionStore, sessionsRepository, recordsRepository, leaveRepository) }
  val sessionsViewModelFactory = viewModelFactory { SessionsViewModel(sessionsRepository) }
  val recordsViewModelFactory = viewModelFactory { RecordsViewModel(recordsRepository) }
  val scanViewModelFactory = viewModelFactory { ScanViewModel(scanRepository) }
  val leaveViewModelFactory = viewModelFactory { LeaveViewModel(leaveRepository) }
  val profileViewModelFactory = viewModelFactory { ProfileViewModel(profileRepository) }
}

inline fun <reified T : ViewModel> viewModelFactory(crossinline creator: () -> T): ViewModelProvider.Factory =
  object : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
      if (modelClass.isAssignableFrom(T::class.java)) {
        @Suppress("UNCHECKED_CAST")
        return creator() as VM
      }
      throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
  }
