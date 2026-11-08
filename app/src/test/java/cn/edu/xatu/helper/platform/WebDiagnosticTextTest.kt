package cn.edu.xatu.helper.platform

import org.junit.Assert.*
import org.junit.Test

class WebDiagnosticTextTest {
    @Test fun urlsKeepResourcePathsButRemoveCredentialsQueriesAndFragments() {
        assertEquals("https://ehall.xatu.edu.cn:443/api/list", WebDiagnosticText.url("https://user:password@ehall.xatu.edu.cn:443/api/list?ticket=private#student"))
        assertEquals("[data]", WebDiagnosticText.url("data:text/plain,private"))
        assertEquals("https://school.edu/user/[redacted]", WebDiagnosticText.url("https://school.edu/user/2026123456"))
    }
    @Test fun diagnosticFieldsRedactTokensAccountsAndControlCharacters() {
        val value = WebDiagnosticText.clean("token=private password=private Cookie=private student=2026123456 test@example.com\nhttps://school.edu/list?ticket=private")
        assertFalse(value.contains("private"))
        assertFalse(value.contains("2026123456"))
        assertFalse(value.contains("test@example.com"))
        assertFalse(value.contains('\n'))
        assertTrue(value.contains("https://school.edu/list"))
    }
    @Test fun malformedAndOversizedMessagesAreBounded() {
        assertEquals("[invalid-url]", WebDiagnosticText.url("https://["))
        assertTrue(WebDiagnosticText.clean("中".repeat(10000)).length <= 6000)
    }
}
