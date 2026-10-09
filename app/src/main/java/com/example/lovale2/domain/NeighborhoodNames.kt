package com.example.lovale2.domain

fun explicitNeighborhood(address: String): String? = Regex("(?i)(?:CABA|Ciudad Aut[oó]noma de Buenos Aires|Vicente L[oó]pez)\\s*[-–]\\s*([\\p{L} ]{3,60})(?:,|$)")
    .find(address)?.groupValues?.get(1)?.trim()
