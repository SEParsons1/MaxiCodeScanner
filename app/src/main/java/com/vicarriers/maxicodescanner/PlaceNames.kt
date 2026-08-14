package com.vicarriers.maxicodescanner

object PlaceNames {
    private val islandBases = listOf(
        "NORTH PENDER",
        "SOUTH PENDER",
        "SALT SPRING",
        "SALISPRING",
        "GABRIOLA",
        "SATURNA",
        "MAYNE",
        "THETIS",
        "CORTES",
        "GALIANO",
        "PENDER",
        "HORNBY",
        "DENMAN",
        "QUADRA",
        "TEXADA",
        "BOWEN",
        "KEATS",
        "LASQUETI",
        "MALCOLM",
    ).sortedByDescending { it.length }

    fun display(placeName: String): String {
        val name = placeName.trim().replace(Regex("\\s+"), " ").uppercase()
        if (name.isEmpty()) return name
        if (name.endsWith(" ISLAND") || name.endsWith(" ISLANDS")) return name
        for (base in islandBases) {
            if (name == base) return "$base ISLAND"
        }
        return name
    }
}
