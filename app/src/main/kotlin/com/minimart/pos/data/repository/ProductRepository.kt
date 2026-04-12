package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.entity.Product
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    fun getAllProducts(): Flow<List<Product>> = productDao.getAllProducts()
    fun getProductsByCategory(category: String): Flow<List<Product>> = productDao.getProductsByCategory(category)
    fun searchProducts(query: String): Flow<List<Product>> = productDao.searchProducts(query)
    fun getLowStockProducts(): Flow<List<Product>> = productDao.getLowStockProducts()
    fun getCategories(): Flow<List<String>> = productDao.getCategories()

    suspend fun getByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)
    suspend fun getById(id: Long): Product? = productDao.getProductById(id)
    suspend fun insert(product: Product): Long = productDao.insertProduct(product)
    suspend fun insertAll(products: List<Product>) = productDao.insertProducts(products)
    suspend fun update(product: Product) = productDao.updateProduct(product)
    suspend fun softDelete(productId: Long) = productDao.softDeleteProduct(productId)
    suspend fun decrementStock(productId: Long, qty: Int) = productDao.decrementStock(productId, qty)
    suspend fun incrementStock(productId: Long, qty: Int) = productDao.incrementStock(productId, qty)
    suspend fun getProductCount(): Int = productDao.getProductCount()
}
