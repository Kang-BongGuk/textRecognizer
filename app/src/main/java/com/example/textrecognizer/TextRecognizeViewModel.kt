package com.example.textrecognizer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TextRecognizeViewModel : ViewModel() {

    private val _textNumberSet = MutableLiveData<String>()
    val textNumberSet: LiveData<String> = _textNumberSet

    fun updateCouponNumber(items: String) {
        _textNumberSet.postValue(items)
    }

}