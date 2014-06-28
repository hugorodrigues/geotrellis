/*
 * Copyright (c) 2014 Azavea.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.raster

import geotrellis.feature.Extent

import spire.syntax.cfor._
import scala.collection.mutable

object CompositeTile {
  def apply(r: Tile, tileLayout: TileLayout): CompositeTile =
    r match {
      case tr: CompositeTile =>
        if(tileLayout != tr.tileLayout) {
          throw new GeoAttrsError("This raster is a tile raster with a different layout than" +
                                  " the argument tile layout." +  
                                 s" $tileLayout does not match ${tr.tileLayout}")
        }
        tr
      case _ =>
        wrap(r, tileLayout)
    }

  /** Converts a raster into a CompositeTile with the given tileLayout.
    * 
    * @param        r              Tile to wrap.
    * @param        tileLayout     TileLayout of the resulting 
    *                              CompositeTile.
    * @param        cropped        Set this flag to false if you
    *                              want the tiles to be ArrayTiles,
    *                              otherwise they will be CroppedTiles
    *                              with the raster 'r' as the backing raster.
    */ 
  def wrap(r: Tile, tileLayout: TileLayout, cropped: Boolean = true): CompositeTile = {
    CompositeTile(split(r, tileLayout, cropped), tileLayout)
  }

  /** Splits a raster into a CompositeTile into tiles.
    * 
    * @param        r              Tile to split.
    * @param        tileLayout     TileLayout defining the tiles to be 
    *                              generated.
    * @param        cropped        Set this flag to false if you
    *                              want the tiles to be ArrayTiles,
    *                              otherwise they will be CroppedTiles
    *                              with the raster 'r' as the backing raster.
    */ 
  def split(r: Tile, tileLayout: TileLayout, cropped: Boolean = true): Seq[Tile] = {
    val pCols = tileLayout.pixelCols
    val pRows = tileLayout.pixelRows

    val tiles = mutable.ListBuffer[Tile]()
    cfor(0)(_ < tileLayout.tileRows, _ + 1) { trow =>
      cfor(0)(_ < tileLayout.tileCols, _ + 1) { tcol =>
        val firstCol = tcol * pCols
        val lastCol = firstCol + pCols - 1
        val firstRow = trow * pRows
        val lastRow = firstRow + pRows - 1
        val gb = GridBounds(firstCol, firstRow, lastCol, lastRow)
        tiles += {
          if(cropped) CroppedTile(r, gb)
          else CroppedTile(r, gb).toArrayTile
        }
      }
    }
    return tiles
  }
}

case class CompositeTile(tiles: Seq[Tile],
                         tileLayout: TileLayout) extends Tile {
  val cols = tileLayout.totalCols
  val rows = tileLayout.totalRows

  private val tileList = tiles.toList
  private val tileCols = tileLayout.tileCols
  private def getTile(tcol: Int, trow: Int) = tileList(trow * tileCols + tcol)

  val cellType: CellType = tiles(0).cellType

  def warp(source: Extent, target: RasterExtent) = 
    toArrayTile.warp(source, target)

  def toArrayTile(): ArrayTile = {
    if (cols.toLong * rows.toLong > Int.MaxValue.toLong) {
      sys.error("This tiled raster is too big to convert into an array.") 
    } else {
      val tile = ArrayTile.alloc(cellType, cols, rows)
      val len = cols * rows
      val tileCols = tileLayout.tileCols
      val tileRows = tileLayout.tileRows
      val pixelCols = tileLayout.pixelCols
      val pixelRows = tileLayout.pixelRows
      if(!cellType.isFloatingPoint) {
        cfor(0)(_ < tileRows, _ + 1) { trow =>
          cfor(0)(_ < tileCols, _ + 1) { tcol =>
            val sourceTile = getTile(tcol, trow)
            cfor(0)(_ < pixelRows, _ + 1) { prow =>
              cfor(0)(_ < pixelCols, _ + 1) { pcol =>
                val acol = (pixelCols * tcol) + pcol
                val arow = (pixelRows * trow) + prow
                tile.set(acol, arow, sourceTile.get(pcol, prow))
              }
            }
          }
        }
      } else {
        cfor(0)(_ < tileRows, _ + 1) { trow =>
          cfor(0)(_ < tileCols, _ + 1) { tcol =>
            val sourceTile = getTile(tcol, trow)
            cfor(0)(_ < pixelRows, _ + 1) { prow =>
              cfor(0)(_ < pixelCols, _ + 1) { pcol =>
                val acol = (pixelCols * tcol) + pcol
                val arow = (pixelRows * trow) + prow
                tile.setDouble(acol, arow, sourceTile.getDouble(pcol, prow))
              }
            }
          }
        }
      }
      tile
    }
  }

  def toArray(): Array[Int] = {
    if (cols.toLong * rows.toLong > Int.MaxValue.toLong) {
      sys.error("This tiled raster is too big to convert into an array.") 
    } else {
      val arr = Array.ofDim[Int](cols * rows)
      val len = cols * rows
      val tileCols = tileLayout.tileCols
      val tileRows = tileLayout.tileRows
      val pixelCols = tileLayout.pixelCols
      val pixelRows = tileLayout.pixelRows
      val totalCols = tileCols * pixelCols

      cfor(0)(_ < tileRows, _ + 1) { trow =>
        cfor(0)(_ < tileCols, _ + 1) { tcol =>
          val tile = getTile(tcol, trow)
          cfor(0)(_ < pixelRows, _ + 1) { prow =>
            cfor(0)(_ < pixelCols, _ + 1) { pcol =>
              val acol = (pixelCols * tcol) + pcol
              val arow = (pixelRows * trow) + prow
              arr(arow * totalCols + acol) = tile.get(pcol, prow)
            }
          }
        }
      }
      arr
    }
  }

  def toArrayDouble(): Array[Double] = {
    if (cols.toLong * rows.toLong > Int.MaxValue.toLong) {
      sys.error("This tiled raster is too big to convert into an array.") 
    } else {
      val arr = Array.ofDim[Double](cols * rows)
      val len = cols * rows
      val tileCols = tileLayout.tileCols
      val tileRows = tileLayout.tileRows
      val pixelCols = tileLayout.pixelCols
      val pixelRows = tileLayout.pixelRows
      val totalCols = tileCols * pixelCols

      cfor(0)(_ < tileRows, _ + 1) { trow =>
        cfor(0)(_ < tileCols, _ + 1) { tcol =>
          val tile = getTile(tcol, trow)
          cfor(0)(_ < pixelRows, _ + 1) { prow =>
            cfor(0)(_ < pixelCols, _ + 1) { pcol =>
              val acol = (pixelCols * tcol) + pcol
              val arow = (pixelRows * trow) + prow
              arr(arow * totalCols + acol) = tile.getDouble(pcol, prow)
            }
          }
        }
      }
      arr
    }
  }

  def toBytes(): Array[Byte] = toArrayTile.toBytes

  def get(col: Int, row: Int): Int = {
    val tcol = col / tileLayout.pixelCols
    val trow = row / tileLayout.pixelRows
    val pcol = col % tileLayout.pixelCols
    val prow = row % tileLayout.pixelRows

    getTile(tcol, trow).get(pcol, prow)
  }

  def getDouble(col: Int, row: Int): Double = {
    val tcol = col / tileLayout.pixelCols
    val trow = row / tileLayout.pixelRows
    val pcol = col % tileLayout.pixelCols
    val prow = row % tileLayout.pixelRows
    getTile(tcol, trow).getDouble(pcol, prow)
  }

  def map(f: Int => Int): Tile = {
    val tile = ArrayTile.alloc(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        tile.set(col, row, f(get(col, row)))
      }
    }
    tile
  }

  def combine(other: Tile)(f: (Int, Int) => Int): Tile = {
    (this, other).assertEqualDimensions

    val tile = ArrayTile.alloc(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        tile.set(col, row, f(get(col, row), other.get(col, row)))
      }
    }
    tile
  }

  def mapDouble(f: Double =>Double): Tile = {
    val tile = ArrayTile.alloc(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        tile.setDouble(col, row, f(getDouble(col, row)))
      }
    }
    tile
  }

  def combineDouble(other: Tile)(f: (Double, Double) => Double): Tile = {
    (this, other).assertEqualDimensions

    val tile = ArrayTile.alloc(cellType, cols, rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        tile.setDouble(col, row, f(getDouble(col, row), other.getDouble(col, row)))
      }
    }
    tile
  }

  override
  def asciiDraw(): String = {
    val sb = new StringBuilder
    for(tileRow <- 0 until tileLayout.tileRows) {
      for(row <- 0 until tileLayout.pixelRows) {
        for(tileCol <- 0 until tileLayout.tileCols) {
          val tile = getTile(tileCol, tileRow)

          for(col <- 0 until tileLayout.pixelCols) {
            val v = tile.get(col, row)
            val s = if(isNoData(v)) {
              "ND"
            } else {
              s"$v"
            }
            val pad = " " * math.max(6 - s.size, 0)
            sb.append(s"$pad$s")
          }
          if(tileCol != tileLayout.tileCols - 1) {
            val pad = " " * 5
            sb.append(s"$pad| ")r
          }
        }
        sb.append(s"\n")
      }
      if(tileRow != tileLayout.tileRows - 1) {
        val rowDiv = "-" * (6 * tileLayout.pixelCols * tileLayout.tileCols - 2) + 
                     "-" * (6 * tileLayout.tileCols)
        sb.append(s"  $rowDiv\n")
      }
    }
    sb.toString
  }
}
