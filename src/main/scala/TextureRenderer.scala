/*
 * Copyright 2019, University of Basel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.{File, FileInputStream, FileOutputStream}

import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.ScallopException
import scalismo.color.{ColorSpaceOperations, RGBA}
import scalismo.common.PointId
import scalismo.faces.gui.ImagePanel
import scalismo.faces.image.BufferedImageConverter
import scalismo.faces.io.{MeshIO, MoMoIO, PixelImageIO}
import scalismo.faces.mesh.{BinaryMask, ColorNormalMesh3D, TextureMappedProperty}
import scalismo.faces.momo.MoMoExpress
import scalismo.faces.parameters.{ParametricRenderer, RenderParameter}
import scalismo.geometry.{Point, Point3D, _2D, _3D}
import scalismo.mesh.{MeshSurfaceProperty, TriangleCell, TriangleList, TriangleMesh3D}
import scalismo.utils.Random
import spray.json.JsObject

import scala.reflect.ClassTag
import scala.reflect.io.Path
import scala.util.Try


/**
 * Application to build different masked models of the Basel Face Model 2019.
 */
object TextureRenderer extends App {

  scalismo.initialize()
  implicit val rng = Random(1024l)

  val conf = new CommandlineOptions(args)
  conf.verify()

  val inputPath = Path(conf.modelDirectory())
  val modelFile = (inputPath/"model2019_fullHead.h5").jfile
  val reference = MoMoIO.read(modelFile).getOrElse{ throw new Exception(s"Could not load model: ${modelFile}!") }.referenceMesh

  val texturePath = Path(conf.texture())
  val texture = TextureMappedPropertyIO.read[RGBA]((inputPath/"model2019_textureMapping.json").jfile,texturePath.jfile)

  val cnMesh = ColorNormalMesh3D(reference,texture,reference.vertexNormals)

  conf.plyFile.toOption.map{ meshOutputLocation =>
    if ( meshOutputLocation.endsWith(".ply")) {
      MeshIO.write(cnMesh,new File(meshOutputLocation))
        .getOrElse(throw new Exception(s"Could not write to file: ${meshOutputLocation}"))
    } else {
      println("Not writing file, as not a .ply file is specified.")
    }
  }

  val init = RenderParameter.defaultSquare
  val img = ParametricRenderer.renderParameterMesh(init,cnMesh)

  import scalismo.faces.gui.GUIBlock._
  ImagePanel(img).displayIn("texture mapping")


  class CommandlineOptions(args: Seq[String]) extends ScallopConf(args) {
    version(s"texture-renderer version v0.1\n© Andreas Morel-Forster")

    val modelDirectory = opt[String](required = true, descr = "directory where the downloaded BFM2019 data resides")
    val texture = opt[String](required = true, descr = "which image to use as a texture")
    val plyFile = opt[String](default = None, descr = "optional file where to store the mesh with texture coordinates as *.ply file")

    override def onError(e: Throwable) = e match {
      case ScallopException(message) =>
        printHelp
        println("Your args: "+args.mkString(" "))
        println(message)
        sys.exit(128)
      case ex => super.onError(ex)
    }

  }
}


object TextureMappedPropertyIO extends App {

  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV2._

  def read[A: ClassTag](mappingFile: File, imageFile: File)(implicit converter: BufferedImageConverter[A],  ops: ColorSpaceOperations[A]) : TextureMappedProperty[A] = {

    import scalismo.faces.io.RenderParameterIO.readASTFromStream

    val fields = readASTFromStream(new FileInputStream(mappingFile)).asJsObject.fields
    val triangles = fields("triangles").convertTo[IndexedSeq[TriangleCell]]
    val triangulation = TriangleList(triangles)

    val textureMapping = fields("textureMapping").convertTo[MeshSurfaceProperty[Point[_2D]]]

    val texture = PixelImageIO.read[A](imageFile).getOrElse{ throw new Exception(s"Could not load image: ${imageFile}!")}

    TextureMappedProperty[A](triangulation, textureMapping, texture)
  }

}