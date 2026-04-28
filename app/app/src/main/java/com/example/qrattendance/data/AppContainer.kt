package com.example.qrattendance.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.qrattendance.data.repository.AuthRepository
import com.example.qrattendance.data.repository.CoursesRepository
import com.example.qrattendance.data.repository.LeaveRepository
import com.example.qrattendance.data.repository.ProfileRepository
import com.example.qrattendance.data.repository.RecordsRepository
import com.example.qrattendance.data.repository.RemoteAuthRepository
import com.example.qrattendance.data.repository.RemoteCoursesRepository
import com.example.qrattendance.data.repository.RemoteLeaveRepository
import com.example.qrattendance.data.repository.RemoteProfileRepository
import com.example.qrattendance.data.repository.RemoteRecordsRepository
import com.example.qrattendance.data.repository.RemoteScanRepository
import com.example.qrattendance.data.repository.RemoteSessionsRepository
import com.example.qrattendance.data.repository.ScanRepository
import com.example.qrattendance.data.repository.SessionsRepository
import com.example.qrattendance.ui.auth.LoginViewModel
import com.example.qrattendance.ui.home.HomeViewModel
import com.example.qrattendance.ui.leave.LeaveViewModel
import com.example.qrattendance.ui.profile.ProfileViewModel
import com.example.qrattendance.ui.records.RecordsViewModel
import com.example.qrattendance.ui.scan.ScanViewModel
import com.example.qrattendance.ui.sessions.SessionsViewModel
import okhttp3.OkHttpClient

class AppContainer(
  val sessionStore: SessionStore,
  val apiEndpointStore: ApiEndpointStore = InMemoryApiEndpointStore(),
  val authRepository: AuthRepository,
  val sessionsRepository: SessionsRepository,
  val recordsRepository: RecordsRepository,
  val leaveRepository: LeaveRepository,
  val profileRepository: ProfileRepository,
  val scanRepository: ScanRepository,
  val coursesRepository: CoursesRepository = EmptyCoursesRepository,
) {
  constructor(context: Context) : this(createProductionGraph(context.applicationContext))

  private constructor(graph: ProductionGraph) : this(
    sessionStore = graph.sessionStore,
    apiEndpointStore = graph.apiEndpointStore,
    authRepository = graph.authRepository,
    coursesRepository = graph.coursesRepository,
    sessionsRepository = graph.sessionsRepository,
    recordsRepository = graph.recordsRepository,
    leaveRepository = graph.leaveRepository,
    profileRepository = graph.profileRepository,
    scanRepository = graph.scanRepository,
  )

  val loginViewModelFactory = viewModelFactory { LoginViewModel(authRepository, apiEndpointStore) }
  val homeViewModelFactory = viewModelFactory { HomeViewModel(sessionStore, sessionsRepository, recordsRepository) }
  val sessionsViewModelFactory = viewModelFactory { SessionsViewModel(sessionsRepository) }
  val recordsViewModelFactory = viewModelFactory { RecordsViewModel(recordsRepository) }
  val scanViewModelFactory = viewModelFactory { ScanViewModel(scanRepository) }
  val leaveViewModelFactory = viewModelFactory { LeaveViewModel(leaveRepository) }
  val profileViewModelFactory = viewModelFactory { ProfileViewModel(profileRepository) }

  private companion object {
    fun createProductionGraph(context: Context): ProductionGraph {
      val sessionStore = EncryptedSharedPreferencesSessionStore(context)
      val apiEndpointStore = SharedPreferencesApiEndpointStore(context)
      val okHttpClient = OkHttpClient()
      val apiClient = AttendanceApiClient(client = okHttpClient, baseUrlProvider = { apiEndpointStore.baseUrl.value }, onUnauthorized = { sessionStore.clear() })
      return ProductionGraph(
        sessionStore = sessionStore,
        apiEndpointStore = apiEndpointStore,
        authRepository = RemoteAuthRepository(apiClient, sessionStore),
        coursesRepository = RemoteCoursesRepository(apiClient, sessionStore),
        sessionsRepository = RemoteSessionsRepository(apiClient, sessionStore),
        recordsRepository = RemoteRecordsRepository(apiClient, sessionStore),
        leaveRepository = RemoteLeaveRepository(apiClient, sessionStore),
        profileRepository = RemoteProfileRepository(apiClient, sessionStore),
        scanRepository = RemoteScanRepository(apiClient, sessionStore),
      )
    }
  }
}

private data class ProductionGraph(
  val sessionStore: SessionStore,
  val apiEndpointStore: ApiEndpointStore,
  val authRepository: AuthRepository,
  val coursesRepository: CoursesRepository,
  val sessionsRepository: SessionsRepository,
  val recordsRepository: RecordsRepository,
  val leaveRepository: LeaveRepository,
  val profileRepository: ProfileRepository,
  val scanRepository: ScanRepository,
)

private object EmptyCoursesRepository : CoursesRepository {
  override suspend fun courses() = emptyList<com.example.qrattendance.data.CourseSummary>()
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
