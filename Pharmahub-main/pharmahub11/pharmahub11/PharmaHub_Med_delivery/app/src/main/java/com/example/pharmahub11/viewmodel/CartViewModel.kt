package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.helper.getProductPrice
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val firebaseCommon: FirebaseCommon
) : ViewModel() {

    private val _cartProducts =
        MutableStateFlow<Resource<List<CartProduct>>>(Resource.Unspecified())
    val cartProducts = _cartProducts.asStateFlow()
    private val _prescriptions = MutableStateFlow<Resource<List<PrescriptionData>>>(Resource.Unspecified())
    val prescriptions = _prescriptions.asStateFlow()


    val productsPrice = cartProducts.map {
        when (it) {
            is Resource.Success -> {
                calculatePrice(it.data!!)
            }
            else -> null
        }
    }
    private val _deleteDialog = MutableSharedFlow<CartProduct>()
    val deleteDialog = _deleteDialog.asSharedFlow()

    private var cartProductDocuments = emptyList<DocumentSnapshot>()


    fun deleteCartProduct(cartProduct: CartProduct) {
        val index = cartProducts.value.data?.indexOf(cartProduct)
        if (index != null && index != -1) {
            val documentId = cartProductDocuments[index].id
            firestore.collection("user").document(auth.uid!!).collection("cart")
                .document(documentId).delete()
        }
    }


    private fun calculatePrice(data: List<CartProduct>): Float {
        return data.sumByDouble { cartProduct ->
            (cartProduct.product.offerPercentage.getProductPrice(cartProduct.product.price) * cartProduct.quantity).toDouble()
        }.toFloat()
    }


    init {
        getCartProducts()
    }


    private fun getCartProducts() {
        viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
        firestore.collection("user").document(auth.uid!!).collection("cart")
            .addSnapshotListener { value, error ->
                if (error != null || value == null) {
                    viewModelScope.launch { _cartProducts.emit(Resource.Error(error?.message.toString())) }
                } else {
                    cartProductDocuments = value.documents
                    val cartProducts = value.toObjects(CartProduct::class.java)
                    viewModelScope.launch { _cartProducts.emit(Resource.Success(cartProducts)) }
                }
            }
    }


    fun changeQuantity(
        cartProduct: CartProduct,
        quantityChanging: FirebaseCommon.QuantityChanging
    ) {

        val index = cartProducts.value.data?.indexOf(cartProduct)

        /**
         * index could be equal to -1 if the function [getCartProducts] delays which will also delay the result we expect to be inside the [_cartProducts]
         * and to prevent the app from crashing we make a check
         */
        if (index != null && index != -1) {
            val documentId = cartProductDocuments[index].id
            when (quantityChanging) {
                FirebaseCommon.QuantityChanging.INCREASE -> {
                    viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
                    increaseQuantity(documentId)
                }
                FirebaseCommon.QuantityChanging.DECREASE -> {
                    if (cartProduct.quantity == 1) {
                        viewModelScope.launch { _deleteDialog.emit(cartProduct) }
                        return
                    }
                    viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
                    decreaseQuantity(documentId)
                }
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            _cartProducts.value = Resource.Loading()
            try {
                // Fixed collection name from "users" to "user" to match getCartProducts() method
                firestore.collection("user")
                    .document(auth.uid!!)
                    .collection("cart")
                    .get()
                    .addOnSuccessListener { documents ->
                        val batch = firestore.batch()
                        documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                _cartProducts.value = Resource.Success(emptyList())
                            }
                            .addOnFailureListener { e ->
                                _cartProducts.value = Resource.Error(e.message.toString())
                            }
                    }
                    .addOnFailureListener { e ->
                        _cartProducts.value = Resource.Error(e.message.toString())
                    }
            } catch (e: Exception) {
                _cartProducts.value = Resource.Error(e.message.toString())
            }
        }
    }

    private fun decreaseQuantity(documentId: String) {
        firebaseCommon.decreaseQuantity(documentId) { result, exception ->
            if (exception != null)
                viewModelScope.launch { _cartProducts.emit(Resource.Error(exception.message.toString())) }
        }
    }

    private fun increaseQuantity(documentId: String) {
        firebaseCommon.increaseQuantity(documentId) { result, exception ->
            if (exception != null)
                viewModelScope.launch { _cartProducts.emit(Resource.Error(exception.message.toString())) }
        }
    }
    fun loadPrescriptions() {
        viewModelScope.launch {
            _prescriptions.value = Resource.Loading()
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                val prescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("usedInOrder", null) // Only get unused prescriptions
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .toObjects(PrescriptionData::class.java)
                    .map { it.copy(id = it.id) }

                _prescriptions.value = Resource.Success(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error getting prescriptions", e)
                _prescriptions.value = Resource.Error(e.message ?: "Failed to load prescriptions")
            }
        }
    }
    fun markPrescriptionAsUsed(prescriptionId: String, orderId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .update("usedInOrder", orderId)
                    .await()

                // Refresh prescriptions after marking as used
                loadPrescriptions()
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error marking prescription as used", e)
            }
        }
    }



    fun checkPrescriptionValidity(): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()

        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    result.postValue(false)
                    return@launch
                }

                val validPrescription = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", "approved")
                    .limit(1)
                    .get()
                    .await()
                    .documents.isNotEmpty()

                result.postValue(validPrescription)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error checking prescription validity", e)
                result.postValue(false)
            }
        }

        return result
    }

    fun getPrescriptionData(): LiveData<List<PrescriptionData>> {
        val liveData = MutableLiveData<List<PrescriptionData>>()

        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    liveData.postValue(emptyList())
                    return@launch
                }

                // Query prescriptions related to these product IDs that are not used
                val prescriptionsSnapshot = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", "approved")
                    .whereEqualTo("usedInOrder", null) // Only unused prescriptions
                    .get()
                    .await()

                val prescriptions = prescriptionsSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(PrescriptionData::class.java)?.copy(id = doc.id)
                }

                liveData.postValue(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error fetching prescription data", e)
                liveData.postValue(emptyList())
            }
        }

        return liveData
    }
    fun getLatestRelevantPrescription(
        prescriptions: List<PrescriptionData>,
        prescriptionItems: List<CartProduct>
    ): PrescriptionData? {
        return prescriptions.filter { prescription ->
            // Only consider unused prescriptions
            prescription.usedInOrder == null &&
                    prescriptionItems.any { cartItem ->
                        cartItem.product.id in prescription.productIds
                    }
        }.maxByOrNull { it.timestamp }
    }
    fun viewPrescription(prescriptionId: String): LiveData<PrescriptionData?> {
        val prescriptionLiveData = MutableLiveData<PrescriptionData?>()

        viewModelScope.launch {
            try {
                val document = firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .get()
                    .await()

                val prescription = document.toObject(PrescriptionData::class.java)
                prescriptionLiveData.postValue(prescription)
            } catch (e: Exception) {
                prescriptionLiveData.postValue(null)
            }
        }

        return prescriptionLiveData
    }
    // Add this to CartViewModel
    fun getUserPrescriptions() {
        viewModelScope.launch {
            _prescriptions.value = Resource.Loading()
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                val prescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .toObjects(PrescriptionData::class.java)
                    .map { it.copy(id = it.id) } // Ensure IDs are preserved

                _prescriptions.value = Resource.Success(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error getting prescriptions", e)
                _prescriptions.value = Resource.Error(
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        "Database index is being created. Please wait a few minutes."
                    } else {
                        "Failed to load prescriptions: ${e.message}"
                    }
                )
            }
        }
    }
}