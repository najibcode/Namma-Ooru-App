package com.example

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun inspectSchemaClass() {
    try {
      val schemaClass = Class.forName("com.google.firebase.ai.type.Schema")
      println("=== SCHEMA CLASS METHODS ===")
      for (method in schemaClass.declaredMethods) {
        val modifiers = Modifier.toString(method.modifiers)
        val params = method.parameterTypes.joinToString { it.simpleName }
        println("$modifiers ${method.returnType.simpleName} ${method.name}($params)")
      }

      println("=== SCHEMA CLASS FIELDS ===")
      for (field in schemaClass.declaredFields) {
        val modifiers = Modifier.toString(field.modifiers)
        println("$modifiers ${field.type.simpleName} ${field.name}")
      }

      println("=== SCHEMA CLASS CLASSES ===")
      for (innerClass in schemaClass.declaredClasses) {
        println("Inner Class: ${innerClass.name}")
        for (method in innerClass.declaredMethods) {
          val modifiers = Modifier.toString(method.modifiers)
          val params = method.parameterTypes.joinToString { it.simpleName }
          println("  $modifiers ${method.returnType.simpleName} ${method.name}($params)")
        }
      }

    } catch (e: Exception) {
      e.printStackTrace()
      fail(e.message)
    }

    try {
      val typeClass = Class.forName("com.google.firebase.ai.type.Type")
      println("=== TYPE CLASS VALUES ===")
      for (field in typeClass.declaredFields) {
        val modifiers = Modifier.toString(field.modifiers)
        println("$modifiers ${field.type.simpleName} ${field.name}")
      }
    } catch (e: Exception) {
      // It's ok if Type doesn't exist
      println("Type class not found: ${e.message}")
    }

    // Fail the test so we get output
    fail("Inspection done")
  }
}
