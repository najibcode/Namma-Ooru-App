package com.example

import org.junit.Assert.*
import org.junit.Test
import com.google.firebase.ai.type.Schema

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun printKotlinSchemaClassInfo() {
    val schemaClass = Schema::class.java
    println("Resolved Schema Class: ${schemaClass.name}")
    println("Package: ${schemaClass.`package`?.name}")
    
    println("Methods:")
    for (m in schemaClass.methods) {
      if (m.name in listOf("str", "obj", "string", "object", "integer", "numInt", "double", "numDouble")) {
        println("  ${m.name} -> parameters: ${m.parameterTypes.joinToString { it.simpleName }}")
      }
    }

    try {
      val compField = schemaClass.getDeclaredField("Companion")
      val companionObj = compField.get(null)
      println("Companion Class: ${companionObj.javaClass.name}")
      println("Companion Methods:")
      for (m in companionObj.javaClass.methods) {
        if (m.name in listOf("str", "obj", "string", "object", "integer", "numInt", "double", "numDouble", "array")) {
          println("  Companion.${m.name} -> parameters: ${m.parameterTypes.joinToString { it.simpleName }}")
        }
      }
    } catch (e: Exception) {
      println("Companion not found or failed: ${e.message}")
    }

    fail("Show output")
  }
}
