package rs.moma.privezak.viewmodels


import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.app.Application

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn = _isLoggedIn.asStateFlow()


    fun login(pin: String? = null) = viewModelScope.launch {
        _isLoggedIn.value = true
    }

    fun logout() = viewModelScope.launch {
        _isLoggedIn.value = false
    }
}