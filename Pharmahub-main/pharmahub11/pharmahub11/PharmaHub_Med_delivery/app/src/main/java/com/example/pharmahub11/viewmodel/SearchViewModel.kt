package com.example.pharmahub11.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.util.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _searchResults = MutableLiveData<Resource<List<Product>>>()
    val searchResults: LiveData<Resource<List<Product>>> = _searchResults

    private var currentQuery = ""
    private var currentCategory: String? = null

    fun searchProducts(query: String) {
        currentQuery = query
        executeSearch()
    }

    fun setCategoryFilter(category: String?) {
        currentCategory = category
        executeSearch()
    }

    private fun executeSearch() {
        if (currentQuery.isEmpty()) {
            _searchResults.value = Resource.Success(emptyList())
            return
        }

        _searchResults.value = Resource.Loading()

        viewModelScope.launch {
            try {
                var query = firestore.collection("Products")
                    .orderBy("name")
                    .startAt(currentQuery)
                    .endAt("$currentQuery\uf8ff")

                currentCategory?.let { category ->
                    query = query.whereEqualTo("category", category)
                }

                query.get()
                    .addOnSuccessListener { result ->
                        val products = result.toObjects(Product::class.java)
                        _searchResults.value = Resource.Success(products)
                    }
                    .addOnFailureListener { e ->
                        _searchResults.value = Resource.Error(e.message ?: "Unknown error")
                    }
            } catch (e: Exception) {
                _searchResults.value = Resource.Error(e.message ?: "Unknown error")
            }
        }
    }
}