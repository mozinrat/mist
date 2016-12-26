package io.hydrosphere.mist.lib

case class LocalDataColumn[T](name: String, data: List[T])

class LocalData(private val columnData: List[LocalDataColumn[_]]) {
  
  def withColumn(localDataColumn: LocalDataColumn[_]): LocalData = {
    LocalData(columnData :+ localDataColumn)
  }
  
  def column(columnName: String): Option[LocalDataColumn[_]] = {
    columnData.find(_.name == columnName)
  }
  
  def columns: List[String] = columnData.map(_.name)

  override def toString: String = {
    
    def rowSeparator(colSizes: List[Int]): String = {
      colSizes.map("-" * _).mkString("+", "+", "+")
    }
    
    def rowFormat(items: List[String], colSizes: List[Int]): String = {
      items.zip(colSizes).map((t) => if (t._2 == 0) "" else s"%${t._2}s".format(t._1)).mkString("|", "|", "|")
    }
    
    var stringParts = List.empty[String]
    
    val rowCount = (for (column <- columnData) yield column.data.length).max
    val sizes = columnData.map(column => (List(column.name) ++ column.data.map(_.toString)).map(_.length).max + 1)
    
    stringParts :+= rowSeparator(sizes)
    stringParts :+= rowFormat(columns, sizes)
    stringParts :+= rowSeparator(sizes)
    for (rowNumber <- List.range(0, rowCount)) {
      // TODO: OutOfBoundsException
      val row = for (column <- columnData) yield column.data(rowNumber).toString
      stringParts :+= rowFormat(row, sizes)
    }
    stringParts :+= rowSeparator(sizes)
    
    stringParts.mkString("\n")
  }
}

object LocalData {

  def apply(columns: LocalDataColumn[_]*): LocalData = {
    new LocalData(columns.toList)
  }

  def apply(columns: List[LocalDataColumn[_]]): LocalData = new LocalData(columns)
  
}