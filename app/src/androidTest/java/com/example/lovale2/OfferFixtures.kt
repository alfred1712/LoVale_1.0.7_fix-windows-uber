package com.example.lovale2

import android.content.Context
import org.junit.Assume.assumeTrue
import java.io.InputStream

/** Real trip screenshots stay local; optional visual tests skip on a clean public checkout. */
fun Context.openOfferFixture(name: String): InputStream {
    assumeTrue("Local offer fixture required: $name (see PRUEBAS_DISPOSITIVO.md)", assets.list("").orEmpty().contains(name))
    return assets.open(name)
}
