package com.github.iamr8.libman

import com.github.iamr8.libman.model.UpdateLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateLabelTest {

    @Test fun `single option omits the severity`() {
        assertEquals("Update to 4.1.0", UpdateLabel.chip("4.1.0", "minor", single = true))
    }

    @Test fun `multiple options include the severity`() {
        assertEquals("Update to 4.0.14 (patch)", UpdateLabel.chip("4.0.14", "patch", single = false))
        assertEquals("Update to 5.0.0 (major)", UpdateLabel.chip("5.0.0", "major", single = false))
    }
}
