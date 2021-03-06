package com.kakao.s2graph.core.storage.hbase

import java.util.UUID

import com.kakao.s2graph.core.SnapshotEdge
import com.kakao.s2graph.core.mysqls.LabelIndex
import com.kakao.s2graph.core.storage.{StorageSerializable, SKeyValue}
import com.kakao.s2graph.core.types.{HBaseType, SourceAndTargetVertexIdPair, VertexId}
import com.kakao.s2graph.core.utils.logger
import org.apache.hadoop.hbase.util.Bytes

import scala.util.Random

class SnapshotEdgeSerializable(snapshotEdge: SnapshotEdge) extends HSerializable[SnapshotEdge] {
  import StorageSerializable._

  val label = snapshotEdge.label
  val table = label.hbaseTableName.getBytes()
  val cf = HSerializable.edgeCf

  def statusCodeWithOp(statusCode: Byte, op: Byte): Array[Byte] = {
    val byte = (((statusCode << 4) | op).toByte)
    Array.fill(1)(byte.toByte)
  }
  def valueBytes() = Bytes.add(statusCodeWithOp(snapshotEdge.statusCode, snapshotEdge.op),
    propsToKeyValuesWithTs(snapshotEdge.props.toList))

  override def toKeyValues: Seq[SKeyValue] = {
    label.schemaVersion match {
      case HBaseType.VERSION3 => toKeyValuesInnerV3
      case _ => toKeyValuesInner
    }
  }

  private def toKeyValuesInner: Seq[SKeyValue] = {
    val srcIdBytes = VertexId.toSourceVertexId(snapshotEdge.srcVertex.id).bytes
    val labelWithDirBytes = snapshotEdge.labelWithDir.bytes
    val labelIndexSeqWithIsInvertedBytes = labelOrderSeqWithIsInverted(LabelIndex.DefaultSeq, isInverted = true)

    val row = Bytes.add(srcIdBytes, labelWithDirBytes, labelIndexSeqWithIsInvertedBytes)
    val tgtIdBytes = VertexId.toTargetVertexId(snapshotEdge.tgtVertex.id).bytes

    val qualifier = tgtIdBytes

    val value = snapshotEdge.pendingEdgeOpt match {
      case None => valueBytes()
      case Some(pendingEdge) =>
        val opBytes = statusCodeWithOp(pendingEdge.statusCode, pendingEdge.op)
        val versionBytes = Array.empty[Byte]
//          Bytes.toBytes(snapshotEdge.version)
        val propsBytes = propsToKeyValuesWithTs(pendingEdge.propsWithTs.toSeq)
        val lockBytes = Bytes.toBytes(pendingEdge.lockTs.get)
//          Array.empty[Byte]
//          snapshotEdge.lockedAtOpt.map(lockedAt => Bytes.toBytes(lockedAt)).getOrElse(Array.empty[Byte])
        Bytes.add(Bytes.add(valueBytes(), opBytes, versionBytes), Bytes.add(propsBytes, lockBytes))
    }
    val kv = SKeyValue(table, row, cf, qualifier, value, snapshotEdge.version)
    Seq(kv)
  }

  private def toKeyValuesInnerV3: Seq[SKeyValue] = {
    val srcIdAndTgtIdBytes = SourceAndTargetVertexIdPair(snapshotEdge.srcVertex.innerId, snapshotEdge.tgtVertex.innerId).bytes
    val labelWithDirBytes = snapshotEdge.labelWithDir.bytes
    val labelIndexSeqWithIsInvertedBytes = labelOrderSeqWithIsInverted(LabelIndex.DefaultSeq, isInverted = true)

    val row = Bytes.add(srcIdAndTgtIdBytes, labelWithDirBytes, labelIndexSeqWithIsInvertedBytes)

    val qualifier = Array.empty[Byte]

    val value = snapshotEdge.pendingEdgeOpt match {
      case None => valueBytes()
      case Some(pendingEdge) =>
        val opBytes = statusCodeWithOp(pendingEdge.statusCode, pendingEdge.op)
        val versionBytes = Array.empty[Byte]
//          Bytes.toBytes(snapshotEdge.version)
        val propsBytes = propsToKeyValuesWithTs(pendingEdge.propsWithTs.toSeq)
        val lockBytes = Bytes.toBytes(pendingEdge.lockTs.get)
//          Array.empty[Byte]
//          snapshotEdge.lockedAtOpt.map(lockedAt => Bytes.toBytes(lockedAt)).getOrElse(Array.empty[Byte])
//        logger.error(s"ValueBytes: ${valueBytes().toList}")
//        logger.error(s"opBytes: ${opBytes.toList}")
//        logger.error(s"versionBytes: ${versionBytes.toList}")
//        logger.error(s"PropsBytes: ${propsBytes.toList}")
//        logger.error(s"LockBytes: ${lockBytes.toList}")
        Bytes.add(Bytes.add(valueBytes(), opBytes, versionBytes), Bytes.add(propsBytes, lockBytes))
    }

    val kv = SKeyValue(table, row, cf, qualifier, value, snapshotEdge.version)
    Seq(kv)
  }
}
