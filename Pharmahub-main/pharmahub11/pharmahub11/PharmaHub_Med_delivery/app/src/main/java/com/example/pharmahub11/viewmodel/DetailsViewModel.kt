package com.example.pharmahub11.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val firebaseCommon: FirebaseCommon
) : ViewModel() {

    private val _addToCart = MutableStateFlow<Resource<CartProduct>>(Resource.Unspecified())
    val addToCart = _addToCart.asStateFlow()

    fun addUpdateProductInCart(cartProduct: CartProduct) {
        viewModelScope.launch { _addToCart.emit(Resource.Loading()) }
        val userId = auth.uid
        if (userId != null) {
            firestore.collection("user").document(userId).collection("cart")
                .whereEqualTo("product.id", cartProduct.product.id).get()
                .addOnSuccessListener { querySnapshot ->
                    val documents = querySnapshot.documents
                    if (documents.isEmpty()) { // Add new product
                        addNewProduct(cartProduct)
                    } else {
                        val existingProduct = documents.first().toObject(CartProduct::class.java)
                        if (existingProduct != null) {
                            if (existingProduct.product.id == cartProduct.product.id &&
                                existingProduct.selectedStrength == cartProduct.selectedStrength &&
                                existingProduct.selectedDosageForm == cartProduct.selectedDosageForm) {
                                // Increase quantity if product matches
                                val documentId = documents.first().id
                                increaseQuantity(documentId, cartProduct)
                            } else {
                                // Add new product if it doesn't match
                                addNewProduct(cartProduct)
                            }
                        }
                    }
                }.addOnFailureListener { exception ->
                    viewModelScope.launch {
                        _addToCart.emit(Resource.Error(exception.localizedMessage ?: "An unknown error occurred"))
                    }
                }
        } else {
            viewModelScope.launch {
                _addToCart.emit(Resource.Error("User not authenticated"))
            }
        }
    }

    private fun addNewProduct(cartProduct: CartProduct) {
        firebaseCommon.addProductToCart(cartProduct) { addedProduct, exception ->
            viewModelScope.launch {
                if (exception == null) {
                    _addToCart.emit(Resource.Success(addedProduct!!))
                } else {
                    _addToCart.emit(Resource.Error(exception.localizedMessage ?: "Failed to add product"))
                }
            }
        }
    }

    private fun increaseQuantity(documentId: String, cartProduct: CartProduct) {
        firebaseCommon.increaseQuantity(documentId) { _, exception ->
            viewModelScope.launch {
                if (exception == null) {
                    _addToCart.emit(Resource.Success(cartProduct))
                } else {
                    _addToCart.emit(Resource.Error(exception.localizedMessage ?: "Failed to increase quantity"))
                }
            }
        }
    }
}
