package com.frustradar.ml

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class MotionModelTest {

    // A minimal mock XGBoost JSON representing a 2-tree model
    // Tree 0: splits on feature 0. If < 0.5, leaf is 0.1, else 0.2.
    // Tree 1: splits on feature 1. If < 1.0, leaf is -0.1, else -0.3.
    private val mockXgbJson = """
    {
      "learner": {
        "gradient_booster": {
          "model": {
            "trees": [
              {
                "left_children": [1, -1, -1],
                "right_children": [2, -1, -1],
                "split_indices": [0, 0, 0],
                "split_conditions": [0.5, 0.0, 0.0],
                "default_left": [1, 0, 0],
                "base_weights": [0.0, 0.1, 0.2]
              },
              {
                "left_children": [1, -1, -1],
                "right_children": [2, -1, -1],
                "split_indices": [1, 0, 0],
                "split_conditions": [1.0, 0.0, 0.0],
                "default_left": [1, 0, 0],
                "base_weights": [0.0, -0.1, -0.3]
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun `test infer evaluates trees correctly and applies base score and sigmoid`() = runTest {
        val model = MotionModel(mockXgbJson)

        // Case 1: feature 0 < 0.5 (goes left -> 0.1), feature 1 < 1.0 (goes left -> -0.1)
        // Sum of leaves = 0.1 + (-0.1) = 0.0
        // totalSum = sum + 0.6 = 0.6
        // Expected probability = 1 / (1 + exp(-0.6)) ≈ 0.645656
        var features = FloatArray(14) { 0.0f }
        var prob = model.infer(features)
        
        val expectedProb1 = 1.0f / (1.0f + kotlin.math.exp(-0.6f))
        assertEquals(expectedProb1, prob, 0.0001f)

        // Case 2: feature 0 >= 0.5 (goes right -> 0.2), feature 1 >= 1.0 (goes right -> -0.3)
        // Sum of leaves = 0.2 + (-0.3) = -0.1
        // totalSum = -0.1 + 0.6 = 0.5
        // Expected probability = 1 / (1 + exp(-0.5)) ≈ 0.622459
        features = FloatArray(14) { 1.5f }
        prob = model.infer(features)
        
        val expectedProb2 = 1.0f / (1.0f + kotlin.math.exp(-0.5f))
        assertEquals(expectedProb2, prob, 0.0001f)
    }

    @Test
    fun `test default_left missing value routing`() = runTest {
        val model = MotionModel(mockXgbJson)

        // Case 3: feature 0 is NaN (default_left is 1 -> goes left -> 0.1)
        // feature 1 is NaN (default_left is 1 -> goes left -> -0.1)
        // Sum = 0.0, totalSum = 0.6
        val features = FloatArray(14) { Float.NaN }
        val prob = model.infer(features)
        
        val expectedProb1 = 1.0f / (1.0f + kotlin.math.exp(-0.6f))
        assertEquals(expectedProb1, prob, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid feature size throws exception`() = runTest {
        val model = MotionModel(mockXgbJson)
        model.infer(FloatArray(10)) // Should throw exception
    }
}
