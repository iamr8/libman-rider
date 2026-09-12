package com.github.iamr8.libman

import com.github.iamr8.libman.cli.WhatIfParser
import com.github.iamr8.libman.cli.WhatIfResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatIfParserTest {

    @Test fun `would update to newer version`() {
        // Verbatim from `libman update jquery --whatif` (libman 3.0.114).
        val out = "Library \"jquery\" would be updated to latest version \"4.0.0\""
        assertEquals(WhatIfResult.WouldUpdate("4.0.0"), WhatIfParser.parse(out))
    }

    @Test fun `already up to date`() {
        val out = "The library \"jquery\" is already up to date"
        assertEquals(WhatIfResult.UpToDate, WhatIfParser.parse(out))
    }

    @Test fun `prerelease would-update line parses the version`() {
        val out = "Library \"jquery\" would be updated to latest prerelease version \"4.1.0-beta.1\""
        assertEquals(WhatIfResult.WouldUpdate("4.1.0-beta.1"), WhatIfParser.parse(out))
    }

    @Test fun `result found among surrounding noise lines`() {
        val out = buildString {
            appendLine("Restoring libraries...")
            appendLine("Library \"bootstrap\" would be updated to latest version \"5.3.6\"")
            appendLine("done")
        }
        assertEquals(WhatIfResult.WouldUpdate("5.3.6"), WhatIfParser.parse(out))
    }

    @Test fun `unrecognized output is Unknown`() {
        assertEquals(WhatIfResult.Unknown, WhatIfParser.parse("some unexpected error text"))
    }

    @Test fun `empty output is Unknown`() {
        assertEquals(WhatIfResult.Unknown, WhatIfParser.parse(""))
    }
}
