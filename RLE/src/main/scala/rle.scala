//see LICENSE for license
//authors: Vrishab Madduri

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

class WrapBundle(nPTWPorts: Int)(implicit p: Parameters) extends Bundle {
  val io = new RoCCIO(nPTWPorts)
  val clock = Clock(INPUT)
  val reset = Input(UInt(1.W))
}

class rleAccel (opcodes: OpcodeSet) (implicit p: Parameters) extends LazyRoCC(opcodes = opcodes) {
    override lazy val module = new rleAccelImp(this)
}

class rleAccelImp(outer: rleAccel) (implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  // route commands into this queue
  val cmd = Queue(io.cmd)
  val resp = Queue(io.resp)
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

  val cmd_router = Module(new CommandRouter)
  cmd_router.io.rocc_in <> io.cmd

}

class WithrleAccel extends Config ((site, here, up) => {
    case BuildRoCC => Seq((p: Parameters) => LazyModule(
    new rleAccel(OpcodeSet.custom0 | OpcodeSet.custom1)(p)))
})

class WithrleAccelPrintf extends Config((site, here, up) => {
  case rleAccelPrintfEnable => true
})