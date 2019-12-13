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
import scalismo.common.PointId
import scalismo.faces.io.{MeshIO, MoMoIO}
import scalismo.faces.mesh.BinaryMask
import scalismo.faces.momo.MoMoExpress
import scalismo.geometry.{Point, Point3D, _3D}
import scalismo.mesh.{TriangleCell, TriangleList, TriangleMesh3D}
import scalismo.utils.Random

import scala.reflect.io.Path


/**
 * Application to build different masked models of the Basel Face Model 2019.
 */
object ModelMasker extends App {

  scalismo.initialize()
  implicit val rng = Random(1024l)

  val conf = new CommandlineOptions(args)
  conf.verify()

  val inputPath = Path(conf.inputDirectory())
  val outputPath = Path(conf.outputDirectory())


  // which resolution of the model should be built ?
  val level = conf.level() match {
    case 1 => Level1
    case 2 => Level2
    case 3 => Level3
    case _ => throw new Exception(s"the level should be either 1, 2, or 3 ... you supplied ${conf.level()}")
  }

  // which part of the head should be contained in the model ?
  val mask = conf.masking() match {
    case "fullHead" => FullHead
    case "bfm" => BFM
    case "face12" => Face12
    case _ => throw new Exception(s"the masking should be either fullHead, bfm, or face12 ... you supplied ${conf.masking()}")
  }

  // should the interior of the mouth be in the model ?
  val mouth = if ( conf.removeMouthInterior() ) NoMouth else WithMouth


  // do not build models that can be downloaded
  if ( level == Level3 && mouth == WithMouth ) {
    throw new Exception(s"the model you want to build is provided as download ... not building the model")
  }

  // load the head model we always use for calculating parts of the mask
  val headModel = MoMoIO.read( (inputPath/"model2019_fullHead.h5").jfile ).get.expressionModel.get

  buildModel(level,mask,mouth)

  // builds the specified model based on the parameters
  def buildModel(
                  lvl: Level,
                  mask: Mask,
                  mouth: Mouth
                ) = {
    val mouthMask = tolerantMaskFromMesh(mask.reference, mouth.cutAway).inv()
    val maskMask = tolerantMaskFromMesh(headModel.referenceMesh,mask.reference)
    val newRef = lvl.reference.operations.maskPoints(lvl.mask.cut(mouthMask.emplaceOnTruesOf(maskMask))).transformedMesh
    val sanitizedModel = sanitizeLandmarksAndModel(changeReference(mask.model, newRef))
    MoMoIO.write(sanitizedModel, (outputPath / s"model2019${mask}${mouth}${if(lvl==Level3) "" else lvl.toString}.h5").jfile).get
  }


  /**
   * Correct model and landmarks according to statismo file format.
   * The write-read cylce changes the written model to float when writing and back to double after reading. While the
   * landmarks are handled differently, they do no longer correspond to exact vertex positions. However this is
   * required by our software at some places, e.g. MoMoRenderer. With this method it is ensured that the landmarks
   * correspond to vertex positions.
   */
  def sanitizeLandmarksAndModel(momo: MoMoExpress): MoMoExpress = {
    val tmp = File.createTempFile("model",".h5")
    tmp.deleteOnExit()

    scalismo.faces.io.MoMoIO.write(momo, tmp).get
    val readBackModel = scalismo.faces.io.MoMoIO.read(tmp).get.asInstanceOf[MoMoExpress]

    val landmarks = readBackModel.landmarks.values.filter { lm =>
      val cp = readBackModel.referenceMesh.pointSet.findClosestPoint(lm.point)
      (cp.point - lm.point).norm < 1.0e-4
    }.map { lm =>
      val cp = readBackModel.referenceMesh.pointSet.findClosestPoint(lm.point)
      lm.id -> lm.copy(point = cp.point)
    }.toMap

    MoMoExpress(
      readBackModel.referenceMesh,
      readBackModel.shape,
      readBackModel.color,
      readBackModel.expression,
      landmarks
    )
  }

  /**
   * Marginalizes a model to a new masking.
   * Do not use this function to change the level of the model, as there is a new triangulation needed
   * which is not calculated automatically.
   */
  def maskModel(momo: MoMoExpress, mask: BinaryMask): MoMoExpress = {
    require(momo.referenceMesh.pointSet.numberOfPoints == mask.entries.size)

    val reference = momo.referenceMesh

    val pidList = mask.entries.zipWithIndex.filter(_._1).map(p => PointId(p._2))

    val newReference = reference.operations.maskPoints(mask).transformedMesh

    val landmarks = momo.landmarks.values.filter { lm =>
      val cp = newReference.pointSet.findClosestPoint(lm.point)
      (cp.point - lm.point).norm < 1.0e-4
    }.map { lm =>
      val cp = newReference.pointSet.findClosestPoint(lm.point)
      lm.id -> lm.copy(point = Point3D(
        cp.point.x.toFloat,
        cp.point.y.toFloat,
        cp.point.z.toFloat
      ))
    }.toMap

    MoMoExpress(
      newReference,
      momo.shape.marginal(pidList),
      momo.color.marginal(pidList),
      momo.expression.marginal(pidList),
      landmarks
    )
  }

  /**
   * Method to calculate the model for a new level. The triangulation is taken from the new reference.
   */
  def changeReference(momo: MoMoExpress, newReference: TriangleMesh3D): MoMoExpress = {
    val reference = momo.referenceMesh

    val mask = tolerantMaskFromMesh(reference,newReference)
    val pidList = mask.entries.zipWithIndex.filter(_._1).map(p => PointId(p._2))

    val landmarks = momo.landmarks.values.filter { lm =>
      val cp = newReference.pointSet.findClosestPoint(lm.point)
      (cp.point - lm.point).norm < 1.0e-4
    }.map { lm =>
      val cp = newReference.pointSet.findClosestPoint(lm.point)
      lm.id -> lm.copy(point = cp.point)
    }.toMap


    MoMoExpress(
      TriangleMesh3D(pidList.map(momo.referenceMesh.pointSet.point),newReference.triangulation),
      momo.shape.marginal(pidList),
      momo.color.marginal(pidList),
      momo.expression.marginal(pidList),
      landmarks
    )
  }

  /**
   * Create mask tolerating a small amount of errors in the vertices. The error stems from that reference meshes
   * in the model are stored with single precision while stored meshes outside of the model file format can have
   * double precision.
   */
  def tolerantMaskFromMesh(original: TriangleMesh3D, masked: TriangleMesh3D): BinaryMask = {
    val t = 1e-4

    if  (masked.pointSet.numberOfPoints == 0)
      return BinaryMask(IndexedSeq.fill(original.pointSet.numberOfPoints)(false))

    val distances = original.pointSet.points.toIndexedSeq.map{ pt =>
      (masked.pointSet.findClosestPoint(pt).point - pt).norm
    }

    val indicators = distances.map(_ < t)
    val mask = BinaryMask(indicators)

    mask
  }

  /**
   * Reorder points of the mesh such that they are in the same order as the higher level.
   * Keeps the triangulation but updates the references of the points to the new ordering.
   */
  def sanitizeSubLevelMesh(template: TriangleMesh3D, defect: TriangleMesh3D): TriangleMesh3D = {
    val points = defect.pointSet.pointsWithId.toIndexedSeq
    val cpIndices = points.map { case (pt,oldIdx) =>
      val cp = template.pointSet.findClosestPoint(pt)
      (oldIdx, cp.id)
    }

    val sortedCips = cpIndices.sortBy(_._2.id)
    val newPointList = sortedCips.map(cpi => defect.pointSet.point(cpi._1))

    val mapping = sortedCips.zipWithIndex.map{ case ((oldIdx,newIdx),idx) =>
      (oldIdx,PointId(idx))
    }.toMap
    val newTriangles = defect.triangulation.triangles.map{ cell =>
      TriangleCell(
        mapping(cell.ptId1),
        mapping(cell.ptId2),
        mapping(cell.ptId3)
      )
    }

    TriangleMesh3D(newPointList,TriangleList(newTriangles))
  }


  sealed trait Mask {
    def reference: TriangleMesh3D
    def model: MoMoExpress
  }

  object FullHead extends Mask {
    override def toString() = "_fullHead"

    override def model = headModel
    override def reference = headModel.referenceMesh
  }

  object BFM extends Mask {
    override def toString() = "_bfm"

    lazy val bfmModel = MoMoIO.read( (inputPath/"model2019_bfm.h5").jfile ).get.expressionModel.get

    override def model = bfmModel
    override def reference = bfmModel.referenceMesh
  }

  object Face12 extends Mask {
    override def toString() = "_face12"

    lazy val face12Model = MoMoIO.read( (inputPath/"model2019_face12.h5").jfile ).get.expressionModel.get

    override def model = face12Model
    override def reference = face12Model.referenceMesh
  }


  sealed trait Mouth {
    def cutAway: TriangleMesh3D
  }

  object WithMouth extends Mouth {
    override def toString() = ""
    override def cutAway = TriangleMesh3D(IndexedSeq[Point[_3D]](),TriangleList(IndexedSeq[TriangleCell]()))
  }

  object NoMouth extends Mouth {
    override def toString() = "_noMouth"

    lazy val mouthOnlyReference = MeshIO.read( (inputPath/"model2019_mouthOnly.ply").jfile ).get.shape

    val reducedMouthInteriorMesh = mouthOnlyReference.operations.maskPoints( pid => !mouthOnlyReference.operations.pointIsOnBoundary(pid)).transformedMesh
    override def cutAway = reducedMouthInteriorMesh
  }


  sealed trait Level {
    def mask: BinaryMask
    def reference: TriangleMesh3D
  }

  object Level3 extends Level {
    override def toString: String = "_lvl3"

    lazy val lvl3Mask = BinaryMask(IndexedSeq.fill(headModel.referenceMesh.pointSet.numberOfPoints)(true))

    override def mask: BinaryMask = lvl3Mask
    override def reference: TriangleMesh3D = headModel.referenceMesh
  }

  object Level2 extends Level {
    override def toString: String = "_lvl2"

    lazy val lvl2Reference = sanitizeSubLevelMesh(headModel.referenceMesh,MeshIO.read( (inputPath/"model2019_fullHead_lvl2.ply").jfile ).get.shape)
    lazy val lvl2Mask = tolerantMaskFromMesh(headModel.referenceMesh,lvl2Reference)

    override def mask: BinaryMask = lvl2Mask
    override def reference: TriangleMesh3D = lvl2Reference
  }

  object Level1 extends Level {
    override def toString: String = "_lvl1"

    lazy val lvl1Reference = sanitizeSubLevelMesh(headModel.referenceMesh,MeshIO.read( (inputPath/"model2019_fullHead_lvl1.ply").jfile ).get.shape)
    lazy val lvl1Mask = tolerantMaskFromMesh(headModel.referenceMesh,lvl1Reference)

    override def mask: BinaryMask = lvl1Mask
    override def reference: TriangleMesh3D = lvl1Reference
  }


  class CommandlineOptions(args: Seq[String]) extends ScallopConf(args) {
    version(s"model-masker version v0.1\n© Andreas Morel-Forster")

    val inputDirectory = opt[String](required = true, descr = "directory where the downloaded BFM2019 data resides")
    val outputDirectory = opt[String](required = true, descr = "directory where to store the calculated models")
    val masking = opt[String](required = true, descr = "which mask version to generate: {fullHead,bfm,face12}")
    val removeMouthInterior = opt[Boolean](default=Some(false), descr = "switch to remove the interior of the mouth")
    val level = opt[Int](required = true, descr = "which level of the model to generate: {1,2,3}")

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