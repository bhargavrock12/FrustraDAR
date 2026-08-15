package com.frustradar.ml

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

data class XgbModel(
    @SerializedName("learner") val learner: XgbLearner
)

data class XgbLearner(
    @SerializedName("gradient_booster") val gradientBooster: XgbGradientBooster
)

data class XgbGradientBooster(
    @SerializedName("model") val model: XgbTreeModel
)

data class XgbTreeModel(
    @SerializedName("trees") val trees: List<XgbTree>
)

data class XgbTree(
    @SerializedName("left_children") val leftChildren: IntArray,
    @SerializedName("right_children") val rightChildren: IntArray,
    @SerializedName("split_indices") val splitIndices: IntArray,
    @SerializedName("split_conditions") val splitConditions: FloatArray,
    @SerializedName("default_left") val defaultLeft: IntArray,
    @SerializedName("base_weights") val baseWeights: FloatArray
)

/**
 * Pure-Kotlin XGBoost JSON evaluator.
 * Evaluates binary:logistic model with exactly 14 features and base_score = 0.6.
 */
class MotionModel(jsonString: String) {

    private val trees: List<XgbTree>
    private val baseScoreLogit = 0.6f

    init {
        val gson = Gson()
        val parsedModel = gson.fromJson(jsonString, XgbModel::class.java)
        trees = parsedModel.learner.gradientBooster.model.trees
    }

    suspend fun infer(features: FloatArray): Float = withContext(Dispatchers.Default) {
        require(features.size == 14) { "Motion model requires exactly 14 features." }

        var sum = 0.0f
        for (tree in trees) {
            sum += evaluateTree(tree, features)
        }

        // Apply base_score and sigmoid
        // Final sum = Σ leaves + 0.6
        val totalSum = sum + baseScoreLogit
        
        // sigmoid(x) = 1 / (1 + exp(-x))
        val probability = 1.0f / (1.0f + exp(-totalSum))
        probability
    }

    private fun evaluateTree(tree: XgbTree, features: FloatArray): Float {
        var node = 0
        while (true) {
            val left = tree.leftChildren[node]
            if (left == -1) {
                // Leaf node
                return tree.baseWeights[node]
            }

            val featureIdx = tree.splitIndices[node]
            val featureValue = features[featureIdx]

            val goLeft = if (featureValue.isNaN()) {
                tree.defaultLeft[node] == 1
            } else {
                featureValue < tree.splitConditions[node]
            }

            node = if (goLeft) left else tree.rightChildren[node]
        }
    }
}
