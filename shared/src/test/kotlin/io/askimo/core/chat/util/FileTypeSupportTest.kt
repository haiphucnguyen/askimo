/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTypeSupportTest {
    @Test
    fun `test new config file extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("toml"))
        assertTrue(FileTypeSupport.isTextExtractable("lock"))
    }

    @Test
    fun `test Terraform extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("tf"))
        assertTrue(FileTypeSupport.isTextExtractable("tfvars"))
    }

    @Test
    fun `test data format extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("jsonc"))
        assertTrue(FileTypeSupport.isTextExtractable("json5"))
        assertTrue(FileTypeSupport.isTextExtractable("graphql"))
        assertTrue(FileTypeSupport.isTextExtractable("gql"))
        assertTrue(FileTypeSupport.isTextExtractable("proto"))
    }

    @Test
    fun `test WebAssembly extension`() {
        assertTrue(FileTypeSupport.isTextExtractable("wat"))
    }

    @Test
    fun `test emerging language extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("zig"))
        assertTrue(FileTypeSupport.isTextExtractable("v"))
    }

    @Test
    fun `test Go module extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("mod"))
        assertTrue(FileTypeSupport.isTextExtractable("sum"))
    }

    @Test
    fun `test Nix and Dhall extensions`() {
        assertTrue(FileTypeSupport.isTextExtractable("nix"))
        assertTrue(FileTypeSupport.isTextExtractable("dhall"))
    }

    @Test
    fun `test config files without extensions`() {
        assertTrue("dockerfile" in FileTypeSupport.CONFIG_EXTENSIONS)
        assertTrue("makefile" in FileTypeSupport.CONFIG_EXTENSIONS)
        assertTrue("gemfile" in FileTypeSupport.CONFIG_EXTENSIONS)
        assertFalse("randomfile" in FileTypeSupport.CONFIG_EXTENSIONS)
    }
}
