package com.example.fuju_bank_app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform