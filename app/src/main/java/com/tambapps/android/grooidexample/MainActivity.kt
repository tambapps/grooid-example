package com.tambapps.android.grooidexample

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.tambapps.android.grooidexample.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import java.io.File

class MainActivity : AppCompatActivity() {

  companion object {
    const val TAG = "MainActivity"
  }

  val tempDir: File
   get() = getDir("dynclasses", Context.MODE_PRIVATE)
  private lateinit var binding: ActivityMainBinding
  private lateinit var grooidShell: GrooidShell

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    grooidShell = GrooidShell(tempDir, classLoader)
    binding.apply {
      fab.setOnClickListener {
        val text = promptEditText.text.toString()
        if (text.isNotBlank()) {
          prompt(text)
          promptEditText.setText("")
        }
      }
    }
  }
  private fun prompt(text: String) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val result = grooidShell.evaluate(text)
        withContext(Dispatchers.Main) {
          binding.resultText.text = result.result?.toString() ?: "null"
        }
      }catch (e: Exception) {
        Log.e(TAG, "Error while evaluating script", e)
        withContext(Dispatchers.Main) {
          binding.resultText.text = "Error: " + e.message
        }
      }
    }

  }

  override fun onDestroy() {
    super.onDestroy()
    ResourceGroovyMethods.deleteDir(tempDir)
  }
}