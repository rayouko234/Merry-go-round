package com.example.merrygoround.ui.auth

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.merrygoround.data.backend.InMemorySessionStore
import com.example.merrygoround.data.backend.local.LocalBackend
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ViewModel-layer tests: validation, submission, and error mapping through the
 * real repository + local backend (brief §12 ViewModel coverage).
 *
 * The backend hops to Dispatchers.IO, so tests poll the StateFlow with a real
 * timeout instead of relying on virtual time.
 */
@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        // Unconfined so viewModelScope launches run eagerly; the backend hops to
        // Dispatchers.IO, so tests poll the StateFlow with a real-time timeout.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val session = InMemorySessionStore()
        val backend = LocalBackend(db, session) { 1_700_000_000_000L }
        viewModel = AuthViewModel(AuthRepository(backend, session))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(20)
        }
    }

    @Test
    fun clientSideValidationProducesFieldErrorsWithoutHittingBackend() = runBlocking {
        viewModel.register("", "bad-email", "123", "short")
        val state = viewModel.uiState.value
        assertTrue(state.fieldErrors.containsKey("fullName"))
        assertTrue(state.fieldErrors.containsKey("email"))
        assertTrue(state.fieldErrors.containsKey("phone"))
        assertTrue(state.fieldErrors.containsKey("password"))
        assertNull(state.loggedInUser)
    }

    @Test
    fun successfulRegisterSetsUserAndSuccessFlag() = runBlocking {
        viewModel.register("Jane Wanjiru", "jane@ex.com", "0733333333", "passw0rd1")
        await { viewModel.uiState.value.success }
        val state = viewModel.uiState.value
        assertEquals("Jane Wanjiru", state.loggedInUser?.fullName)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun duplicateRegistrationSurfacesBackendConflictMessage() = runBlocking {
        viewModel.register("Jane Wanjiru", "jane@ex.com", "0733333333", "passw0rd1")
        await { viewModel.uiState.value.success }
        viewModel.resetSuccess()

        viewModel.register("Another Person", "jane@ex.com", "0733333334", "passw0rd1")
        await { viewModel.uiState.value.formError != null }
        val state = viewModel.uiState.value
        assertNull(state.loggedInUser)
        assertNotNull(state.formError)
        assertTrue(state.formError!!.contains("already"))
    }

    @Test
    fun loginWrongPasswordThenCorrectPassword() = runBlocking {
        viewModel.register("Jane Wanjiru", "jane@ex.com", "0733333333", "passw0rd1")
        await { viewModel.uiState.value.success }

        viewModel.login("jane@ex.com", "wrongPass9")
        await { viewModel.uiState.value.formError != null }

        viewModel.clearErrors()
        viewModel.login("0733333333", "passw0rd1")
        await { viewModel.uiState.value.success }
        assertTrue(viewModel.uiState.value.success)
    }
}
