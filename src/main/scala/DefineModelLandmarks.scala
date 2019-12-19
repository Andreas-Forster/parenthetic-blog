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

import java.io.File

import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.ScallopException
import scalismo.faces.io.MoMoIO
import scalismo.geometry.{Landmark, _3D}
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.TriangleMesh3D
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

import scala.reflect.io.Path


/**
 * Application to build different masked models of the Basel Face Model 2019.
 */
object ClickModelLandmarks extends App {

  scalismo.initialize()
  implicit val rng = Random(1024l)

  val conf = new CommandlineOptions(args)
  conf.verify()

  val inputPath = Path(conf.modelDirectory())
  val modelFile = (inputPath/"model2019_fullHead.h5").jfile
  val momo = MoMoIO.read(modelFile).getOrElse{ throw new Exception(s"Could not load model: ${modelFile}!") }

  val meshFile = (inputPath / "model2019_fullHead_lvl1.ply").jfile
  val reference = MeshIO.readMesh(meshFile).getOrElse( throw new Exception(s"Could not load mesh: ${meshFile}!"))
//  val reference = momo.referenceMesh // USE THIS LINE IF YOU WANT TO CLICK LANDMAKRS ON THE LVL 3, BUT YOU MIGHT NOT BE ABLE TO USE THEM FOR A LVL 1 MODEL

  val ui = ScalismoUI()
  val grp = ui.createGroup("default")

  ui.show(grp,reference,"reference")
  ui.show(grp,momo.landmarks.values.toIndexedSeq,"landmarks")



  class CommandlineOptions(args: Seq[String]) extends ScallopConf(args) {
    version(s"ClickModelLandmarks version v0.1\n© Andreas Morel-Forster")

    val modelDirectory = opt[String](required = true, descr = "directory where the downloaded BFM2019 data resides")

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



object AddLandmarksToModel extends App {

  scalismo.initialize()
  implicit val rng = Random(1024l)

  val conf = new CommandlineOptions(args)
  conf.verify()

  val modelFile = new File(conf.modelFile())
  val momo = MoMoIO.read(modelFile).getOrElse{ throw new Exception(s"Could not load model: ${modelFile}!") }

  val landmarksFile = conf.landmarkFile()
  val landmarks3d = if( landmarksFile.endsWith(".csv") ) {
    LandmarkIO.readLandmarksCsv[_3D](new File(landmarksFile)).get
  } else if ( landmarksFile.endsWith(".json") ) {
    LandmarkIO.readLandmarksJson[_3D](new File(landmarksFile)).get
  } else {
    throw new Exception("Do not know how to load the landmarks file.")
  }

  val landmarkMap = sanitizeLandmarks(landmarks3d, momo.referenceMesh).map{ lm => lm.id -> lm}.toMap

  MoMoIO.write(momo.withLandmarks(landmarkMap),new File(conf.outputFile())).getOrElse(s"Could not write model: ${conf.outputFile()}")



  def sanitizeLandmarks(lms: Seq[Landmark[_3D]], reference: TriangleMesh3D): Seq[Landmark[_3D]] = {
    lms.map{ lm =>
      val cp = reference.pointSet.findClosestPoint(lm.point)
      if ( (cp.point-lm.point).norm < 1.0e-4) {
        lm.copy(point = cp.point)
      } else {
        throw new Exception("Could not add landmarks to model. The points do not match to the reference mesh. Please provide a different landmarks file.")
      }
    }
  }



  class CommandlineOptions(args: Seq[String]) extends ScallopConf(args) {
    version(s"AddLandmarksToModel version v0.1\n© Andreas Morel-Forster")

    val modelFile = opt[String](required = true, descr = "model to which the landmarks should be added")
    val landmarkFile = opt[String](required = true, descr = "new landmarks to save into the model")
    val outputFile = opt[String](required = true, descr = "file where to store the model with changed landmarks")

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