package com.xty.englishhelper.domain.repository

interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}
