package studio.nxtech.fujubank

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform