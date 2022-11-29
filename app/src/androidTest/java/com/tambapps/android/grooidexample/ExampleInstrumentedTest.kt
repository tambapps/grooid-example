package com.tambapps.android.grooidexample

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import java.io.File

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val classLoader: ClassLoader
    get() = context.classLoader
  private val tempDir: File
    get() = context.getDir("dynclasses", Context.MODE_PRIVATE)
  lateinit var shell: GrooidShell

  @Test
  fun testInteger() {
    val result = shell.evaluate("2")
    assertEquals(2, result.result)
  }

  @Test
  fun testIntegerSum() {
    val result = shell.evaluate("2")
    assertEquals(2, result.result)
  }

  @Test
  fun testIntegerForSum() {
    val result = shell.evaluate("""
      int a = 0
      for (i in 0..<10) a++
      a
    """.trimIndent())
    assertEquals(10, result.result)
  }

  @Test
  fun testIntegerIf() {
    val result = shell.evaluate("if (true) 1 else 2")
    assertEquals(1, result.result)
  }

  @Test
  fun testString() {
    val result = shell.evaluate("'string'")
    assertEquals("string", result.result)
  }

  @Test
  fun testObject() {
    // this test fails
    val result = shell.evaluate("new Object()")
    assertTrue(result.result is Any)
  }

  @Before
  fun init() {
    shell = GrooidShell(tempDir, classLoader)
  }

  @After
  fun clean() {
    tempDir.deleteRecursively()
  }
}