package com.example.camera.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LoginViewModel : ViewModel() {
    private val apiService = ApiService()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token

    private val _isRegisterSuccess = MutableStateFlow(false)
    val isRegisterSuccess: StateFlow<Boolean> = _isRegisterSuccess

    suspend fun register(username: String, password: String) {
        _isLoading.value = true
        _message.value = ""
        _isRegisterSuccess.value = false

        try {
            val success = apiService.register(username, password)
            if (success) {
                _message.value = "Success! Now you can log in"
                _isRegisterSuccess.value = true
            } else {
                _message.value = "Username may exist"
                _isRegisterSuccess.value = false
            }
        } catch (e: Exception) {
            _message.value = "Error: ${e.message}"
            _isRegisterSuccess.value = false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun login(username: String, password: String) {
        _isLoading.value = true
        _message.value = ""
        _token.value = ""

        try {
            val token = apiService.login(username, password)
            if (token != null) {
                _message.value = "Success!"
                _token.value = token
            } else {
                _message.value = "The username or password may be incorrect"
            }
        } catch (e: Exception) {
            _message.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = ""
        _token.value = ""
        _isRegisterSuccess.value = false
    }
}