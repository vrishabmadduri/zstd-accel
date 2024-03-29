//see LICENSE for license
//authors: Vrishab Madduri
package rle

import Chisel._
import chisel3.{Printable}
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheArbiter}
import freechips.rocketchip.util.DecoupledHelper
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.tilelink._

case object rleAccelPrintfEnable extends Field[Boolean](false)

class rleAccel (opcodes: OpcodeSet) (implicit p: Parameters) extends LazyRoCC(opcodes = opcodes, nPTWPorts = 5) {
    override lazy val module = new rleAccelImp(this)
    //val l2_read_rle = LazyModule(new L1MemHelper("[l2_read_rle]"))
    //tlNode := l2_read_rle.masterNode

    //val l2_write_rle = LazyModule(new L1MemHelper("[l2_write_rle]"))
    //tlNode := l2_write_rle.masterNode

    val l2_read_rle_decode = LazyModule(new L1MemHelper("[l2_read_rle_decode]"))
    tlNode := l2_read_rle_decode.masterNode

    val l2_write_rle_decode = LazyModule(new L1MemHelper("[l2_write_rle_decode]"))
    tlNode := l2_write_rle_decode.masterNode
}

class rleAccelImp(outer: rleAccel) (implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  // route commands into this queue
  //val cmd = Queue(io.cmd)
  //val resp = Queue(io.resp)
  // The parts of the command are as follows
  // inst - the parts of the instruction itself
  //   opcode
  //   rd - destination register number
  //   rs1 - first source register number
  //   rs2 - second source register number
  //   funct
  //   xd - is the destination register being used?
  //   xs1 - is the first source register being used?
  //   xs2 - is the second source register being used?
  // rs1 - the value of source register 1
  // rs2 - the value of source register 2

  // hook up rle encode/decode modules here

  // io.interrupt := Bool(false)

  val cmd_router = Module(new CommandRouter)
  cmd_router.io.rocc_in <> io.cmd

  //val rle_encode = Module(new rleEncode)
  //rle_encode.io.rle_stage_cmd <> cmd_router.io.rle_stage_out
  //rle_encode.io.rle_encode_cmd <> cmd_router.io.rle_encode_out
  //io.resp <> rle_encode.io.rocc_out

  //outer.l2_read_rle.module.io.userif <> rle_encode.io.l1helperUserRead
  //outer.l2_write_rle.module.io.userif <> rle_encode.io.l1helperUserWrite

  val rle_decode = Module(new rleDecode)
  rle_decode.io.rle_staged_cmd <> cmd_router.io.rle_staged_out
  rle_decode.io.rle_decode_cmd <> cmd_router.io.rle_decode_out
  io.resp <> rle_decode.io.rocc_out

  outer.l2_read_rle_decode.module.io.userif <> rle_decode.io.l1helperUserRead
  outer.l2_write_rle_decode.module.io.userif <> rle_decode.io.l1helperUserWrite
  //outer.l2_read_rle.module.io.sfence <> cmd_router.io.sfence_out
}

class WithrleAccel extends Config ((site, here, up) => {
    case RleTLB => Some(TLBConfig(nSets = 1, nWays = 4, nSectors = 1, nSuperpageEntries = 1))
    case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val rle = LazyModule.apply(new rleAccel(OpcodeSet.custom0)(p))
      rle
    }
  ) // use just opcode 0
})

class WithrleAccelPrintf extends Config((site, here, up) => {
  case rleAccelPrintfEnable => true
})
