package com.vicarriers.maxicodescanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ErnNamesClientTest {
    @Test
    fun parseTsv_stripsSpacesAndSkipsHeader() {
        val names =
            ErnNamesClient.parseTsv(
                "tracking\tname\n1Z V56 D26 20 2433 4271\tJane Doe\n",
            )
        assertEquals("Jane Doe", names["1ZV56D262024334271"])
    }

    @Test
    fun lookupKeys_includeCompactServiceAliasAndLast11() {
        val keys = ErnNamesClient.lookupKeys("1Z V56 D26 02 2433 4271")
        assertEquals(
            listOf("1ZV56D260224334271", "1ZV56D26DK24334271", "60224334271"),
            keys,
        )
    }

    @Test
    fun parseResponse_readsGithubJsonContent() {
        val tsv = "tracking\tname\n1ZV56D262024334271\tJane Doe\n"
        val encoded = java.util.Base64.getEncoder().encodeToString(tsv.toByteArray())
        val json = """{"encoding":"base64","content":"$encoded"}"""
        val names = ErnNamesClient.parseResponse(json)
        assertEquals("Jane Doe", names["1ZV56D262024334271"])
    }
}
