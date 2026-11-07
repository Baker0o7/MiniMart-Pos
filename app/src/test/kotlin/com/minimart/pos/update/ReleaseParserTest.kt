package com.minimart.pos.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {
    private val hex = "a".repeat(64)
    private fun json(extra: String = "", tag: String = "v1.29.0") = """
        {"tag_name":"$tag","body":"Faster till","assets":[
          {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://github.com/Baker0o7/MiniMart-Pos/releases/download/$tag/a.apk","size":1000,"digest":"sha256:$hex"},
          {"name":"app-release.apk","browser_download_url":"https://github.com/Baker0o7/MiniMart-Pos/releases/download/$tag/u.apk","size":2000}
        ]$extra}""".trimIndent()

    @Test fun readsTagNotesAssetsAndChecksum() {
        val r = ReleaseParser.parse(json())
        assertNotNull(r)
        assertEquals("v1.29.0", r!!.tag); assertEquals("Faster till", r.notes); assertEquals(2, r.assets.size)
        assertEquals(hex, r.assets[0].sha256); assertEquals(null, r.assets[1].sha256)
    }

    @Test fun draftsAndPrereleasesAreIgnored() {
        assertNull(ReleaseParser.parse(json(""","draft":true""")))
        assertNull(ReleaseParser.parse(json(""","prerelease":true""")))
    }

    @Test fun oddTagIsIgnored() { assertNull(ReleaseParser.parse(json(tag = "nightly"))) }

    @Test fun assetsWithoutAddressAreSkipped() {
        val r = ReleaseParser.parse("""{"tag_name":"v2.0.0","assets":[{"name":"x.apk","browser_download_url":"","size":1}]}""")
        assertEquals(0, r!!.assets.size)
    }
}
