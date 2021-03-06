package io.hydrosphere.mist.ml.loaders

import io.hydrosphere.mist.ml.Metadata
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.ml.linalg.{Vector, Vectors}

object LocalLogisticRegressionModel extends LocalModel {
  override def localLoad(metadata: Metadata, data: Map[String, Any]): Transformer = {
    val constructor = classOf[LogisticRegressionModel].getDeclaredConstructor(classOf[String], classOf[Vector], classOf[Double])
    constructor.setAccessible(true)
    val coefficientsParams = data("coefficients").asInstanceOf[Map[String, Any]] 
    val coefficients = Vectors.sparse(
      coefficientsParams("size").asInstanceOf[Int],
      coefficientsParams("indices").asInstanceOf[List[Int]].toArray[Int],
      coefficientsParams("values").asInstanceOf[List[Double]].toArray[Double]
    )
    constructor
      .newInstance(metadata.uid, coefficients, data("intercept").asInstanceOf[java.lang.Double])
      .setFeaturesCol(metadata.paramMap("featuresCol").asInstanceOf[String])
      .setPredictionCol(metadata.paramMap("predictionCol").asInstanceOf[String])
      .setProbabilityCol(metadata.paramMap("probabilityCol").asInstanceOf[String])
      .setThreshold(metadata.paramMap("threshold").asInstanceOf[Double])
  }
}
